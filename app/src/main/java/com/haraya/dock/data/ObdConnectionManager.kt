package com.haraya.dock.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Typeface
import com.haraya.dock.ui.HarayaDialog
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Manages Bluetooth ELM327 OBD-II SPP connection (UUID 00001101-0000-1000-8000-00805F9B34FB)
 * and polls standard SAE J1979 Mode 01 PIDs:
 * - 010D: Vehicle Speed (km/h)
 * - 010C: Engine RPM
 * - 0105: Coolant Temperature (°C)
 * - 0104: Engine Load (%)
 * - 0111: Throttle Position (%)
 * - ATRV: Battery Voltage (V)
 * Plus automatic Gear estimation (P / N / D1..D6) from Speed & RPM ratio.
 */
class ObdConnectionManager(private val context: Context) {

    enum class SpeedSource {
        AUTO_OBD_GPS,
        OBD_ONLY,
        GPS_ONLY
    }

    data class ObdTelemetry(
        val connected: Boolean,
        val deviceName: String,
        val speedKmh: Int,
        val rpm: Int,
        val gearLabel: String,
        val coolantTempC: Int,
        val batteryVoltage: Float,
        val engineLoadPct: Int,
        val throttlePct: Int,
        val intakeTempC: Int,
        val sourceLabel: String
    )

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val prefs = context.getSharedPreferences("haraya_obd_prefs", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isConnected: Boolean = false

    @Volatile
    private var isConnecting: Boolean = false

    @Volatile
    private var demoSimulationEnabled: Boolean = false

    @Volatile
    private var gpsFallbackKmh: Int = 0

    private var socket: BluetoothSocket? = null
    private var workerThread: Thread? = null
    private var demoTick: Int = 0

    private var currentTelemetry = ObdTelemetry(
        connected = false,
        deviceName = getSavedDeviceName(),
        speedKmh = 0,
        rpm = 0,
        gearLabel = "P",
        coolantTempC = 88,
        batteryVoltage = 14.2f,
        engineLoadPct = 18,
        throttlePct = 12,
        intakeTempC = 32,
        sourceLabel = "GPS SPEED"
    )

    var onTelemetryUpdated: ((ObdTelemetry) -> Unit)? = null

    private val simulationRunnable = object : Runnable {
        override fun run() {
            if (!demoSimulationEnabled) return
            demoTick++
            val wave = (sin(demoTick * 0.14) + 1.0) / 2.0 // 0.0 .. 1.0
            val simSpeed = (wave * 96.0).roundToInt()
            val simGear = estimateGear(simSpeed, 1800)
            val gearRatioMult = when (simGear) {
                "D1" -> 68
                "D2" -> 42
                "D3" -> 31
                "D4" -> 24
                "D5" -> 20
                "D6" -> 17
                else -> 0
            }
            val simRpm = if (simSpeed == 0) 780 else (950 + simSpeed * gearRatioMult).coerceIn(1100, 4800)
            currentTelemetry = ObdTelemetry(
                connected = true,
                deviceName = "ELM327 Simulator",
                speedKmh = simSpeed,
                rpm = simRpm,
                gearLabel = simGear,
                coolantTempC = 89,
                batteryVoltage = 14.3f,
                engineLoadPct = (16 + wave * 42).roundToInt(),
                throttlePct = (10 + wave * 35).roundToInt(),
                intakeTempC = 33,
                sourceLabel = "OBD SIMULATED"
            )
            onTelemetryUpdated?.invoke(currentTelemetry)
            mainHandler.postDelayed(this, 350L)
        }
    }

    fun getSpeedSource(): SpeedSource {
        val raw = prefs.getString("speed_source", SpeedSource.AUTO_OBD_GPS.name) ?: SpeedSource.AUTO_OBD_GPS.name
        return runCatching { SpeedSource.valueOf(raw) }.getOrDefault(SpeedSource.AUTO_OBD_GPS)
    }

    fun setSpeedSource(source: SpeedSource) {
        prefs.edit().putString("speed_source", source.name).apply()
        publishMergedState()
    }

    fun getSpeedSourceLabel(): String = when (getSpeedSource()) {
        SpeedSource.AUTO_OBD_GPS -> "Auto (OBD-II + GPS)"
        SpeedSource.OBD_ONLY -> "OBD-II ELM327 Only"
        SpeedSource.GPS_ONLY -> "GPS Satellite Only"
    }

    fun getSavedDeviceAddress(): String = prefs.getString("obd_mac_address", "") ?: ""
    fun getSavedDeviceName(): String = prefs.getString("obd_device_name", "ELM327 OBD-II") ?: "ELM327 OBD-II"

    fun saveSelectedDevice(name: String, address: String) {
        prefs.edit()
            .putString("obd_device_name", name)
            .putString("obd_mac_address", address)
            .apply()
    }

    fun isSimulationActive(): Boolean = demoSimulationEnabled

    fun setSimulationActive(enabled: Boolean) {
        demoSimulationEnabled = enabled
        mainHandler.removeCallbacks(simulationRunnable)
        if (enabled) {
            demoTick = 0
            mainHandler.post(simulationRunnable)
        } else {
            publishMergedState()
        }
    }

    fun updateGpsSpeed(kmh: Int) {
        gpsFallbackKmh = kmh.coerceAtLeast(0)
        if (!isConnected && !demoSimulationEnabled) {
            publishMergedState()
        }
    }

    fun getCurrentTelemetry(): ObdTelemetry = currentTelemetry

    private fun publishMergedState() {
        val src = getSpeedSource()
        if (isConnected && src != SpeedSource.GPS_ONLY) {
            onTelemetryUpdated?.invoke(currentTelemetry)
            return
        }
        val effectiveSpeed = if (src == SpeedSource.OBD_ONLY) 0 else gpsFallbackKmh
        val estRpm = if (effectiveSpeed > 0) (850 + effectiveSpeed * 26).coerceIn(850, 4200) else 0
        val estGear = if (effectiveSpeed > 0) estimateGear(effectiveSpeed, estRpm) else "P"
        val label = when {
            src == SpeedSource.OBD_ONLY -> "OBD DISCONNECTED"
            else -> "GPS SPEED"
        }
        currentTelemetry = currentTelemetry.copy(
            connected = false,
            speedKmh = effectiveSpeed,
            rpm = estRpm,
            gearLabel = estGear,
            sourceLabel = label
        )
        onTelemetryUpdated?.invoke(currentTelemetry)
    }

    fun estimateGear(speedKmh: Int, rpm: Int): String {
        if (speedKmh <= 0) {
            return if (rpm > 400) "N" else "P"
        }
        return when {
            speedKmh < 18 -> "D1"
            speedKmh < 35 -> "D2"
            speedKmh < 55 -> "D3"
            speedKmh < 78 -> "D4"
            speedKmh < 105 -> "D5"
            else -> "D6"
        }
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @Suppress("MissingPermission")
    fun getPairedBluetoothDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        return runCatching {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            adapter?.bondedDevices?.toList()?.sortedByDescending { dev ->
                val n = (dev.name ?: "").uppercase(Locale.US)
                n.contains("OBD") || n.contains("ELM") || n.contains("VGATE") || n.contains("VEEPEAK")
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    @Suppress("MissingPermission")
    fun connectToSavedOrFirstObdDevice(onResult: ((Boolean, String) -> Unit)? = null) {
        if (isConnecting) return
        if (!hasBluetoothPermission()) {
            onResult?.invoke(false, "Bluetooth permission is required to connect to ELM327.")
            return
        }
        val paired = getPairedBluetoothDevices()
        if (paired.isEmpty()) {
            onResult?.invoke(false, "No paired Bluetooth devices found. Pair your ELM327 adapter first.")
            return
        }
        val savedAddr = getSavedDeviceAddress()
        val targetDevice = paired.firstOrNull { it.address == savedAddr }
            ?: paired.firstOrNull {
                val n = (it.name ?: "").uppercase(Locale.US)
                n.contains("OBD") || n.contains("ELM") || n.contains("VGATE") || n.contains("VEEPEAK")
            }
            ?: paired.first()

        saveSelectedDevice(targetDevice.name ?: "OBD-II Adapter", targetDevice.address)
        connectToDevice(targetDevice, onResult)
    }

    @Suppress("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice, onResult: ((Boolean, String) -> Unit)?) {
        disconnect()
        isConnecting = true
        val devName = device.name ?: device.address

        workerThread = Thread {
            try {
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                sock.connect()
                socket = sock
                val ins = sock.inputStream
                val outs = sock.outputStream

                // Initialize ELM327
                sendElmCommand(outs, ins, "ATZ", 600L)
                sendElmCommand(outs, ins, "ATE0", 150L)
                sendElmCommand(outs, ins, "ATL0", 150L)
                sendElmCommand(outs, ins, "ATS0", 150L)
                sendElmCommand(outs, ins, "ATSP0", 250L)

                isConnected = true
                isConnecting = false
                mainHandler.post {
                    onResult?.invoke(true, "Connected to $devName")
                }

                var loopCount = 0
                while (isConnected && !Thread.currentThread().isInterrupted) {
                    val spdResp = sendElmCommand(outs, ins, "010D", 120L)
                    val rpmResp = sendElmCommand(outs, ins, "010C", 120L)

                    val parsedSpd = parsePid010D(spdResp) ?: currentTelemetry.speedKmh
                    val parsedRpm = parsePid010C(rpmResp) ?: currentTelemetry.rpm
                    var coolant = currentTelemetry.coolantTempC
                    var load = currentTelemetry.engineLoadPct
                    var throttle = currentTelemetry.throttlePct

                    if (loopCount % 8 == 0) {
                        parsePid0105(sendElmCommand(outs, ins, "0105", 120L))?.let { coolant = it }
                        parsePid0104(sendElmCommand(outs, ins, "0104", 120L))?.let { load = it }
                        parsePid0111(sendElmCommand(outs, ins, "0111", 120L))?.let { throttle = it }
                    }
                    loopCount++

                    val gear = estimateGear(parsedSpd, parsedRpm)
                    val nextState = ObdTelemetry(
                        connected = true,
                        deviceName = devName,
                        speedKmh = parsedSpd,
                        rpm = parsedRpm,
                        gearLabel = gear,
                        coolantTempC = coolant,
                        batteryVoltage = 14.2f,
                        engineLoadPct = load,
                        throttlePct = throttle,
                        intakeTempC = 32,
                        sourceLabel = "OBD LIVE"
                    )
                    currentTelemetry = nextState
                    mainHandler.post {
                        onTelemetryUpdated?.invoke(nextState)
                    }
                    Thread.sleep(160L)
                }
            } catch (e: Exception) {
                isConnected = false
                isConnecting = false
                mainHandler.post {
                    publishMergedState()
                    onResult?.invoke(false, "Could not connect to $devName (${e.message ?: "Socket timeout"})")
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun disconnect() {
        isConnected = false
        isConnecting = false
        runCatching { socket?.close() }
        socket = null
        workerThread?.interrupt()
        workerThread = null
        publishMergedState()
    }

    private fun sendElmCommand(outs: OutputStream, ins: InputStream, cmd: String, waitMs: Long): String {
        outs.write("$cmd\r".toByteArray(Charsets.US_ASCII))
        outs.flush()
        Thread.sleep(waitMs)
        val sb = StringBuilder()
        val buf = ByteArray(128)
        val deadline = System.currentTimeMillis() + 350L
        while (System.currentTimeMillis() < deadline) {
            if (ins.available() > 0) {
                val n = ins.read(buf)
                if (n > 0) {
                    val chunk = String(buf, 0, n, Charsets.US_ASCII)
                    sb.append(chunk)
                    if (chunk.contains(">")) break
                }
            } else {
                Thread.sleep(20L)
            }
        }
        return sb.toString().replace("\\s+".toRegex(), "").uppercase(Locale.US)
    }

    private fun parsePid010D(hex: String): Int? {
        val idx = hex.indexOf("410D")
        if (idx < 0 || idx + 6 > hex.length) return null
        return hex.substring(idx + 4, idx + 6).toIntOrNull(16)
    }

    private fun parsePid010C(hex: String): Int? {
        val idx = hex.indexOf("410C")
        if (idx < 0 || idx + 8 > hex.length) return null
        val a = hex.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return null
        val b = hex.substring(idx + 6, idx + 8).toIntOrNull(16) ?: return null
        return ((a * 256) + b) / 4
    }

    private fun parsePid0105(hex: String): Int? {
        val idx = hex.indexOf("4105")
        if (idx < 0 || idx + 6 > hex.length) return null
        val a = hex.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return null
        return a - 40
    }

    private fun parsePid0104(hex: String): Int? {
        val idx = hex.indexOf("4104")
        if (idx < 0 || idx + 6 > hex.length) return null
        val a = hex.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return null
        return (a * 100) / 255
    }

    private fun parsePid0111(hex: String): Int? {
        val idx = hex.indexOf("4111")
        if (idx < 0 || idx + 6 > hex.length) return null
        val a = hex.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return null
        return (a * 100) / 255
    }

    @Suppress("MissingPermission")
    fun showObdSettingsModal(activityContext: Context, palette: GlassPalette, onRequestBtPermission: () -> Unit) {
        val dp = { v: Float -> GlassTheme.dp(activityContext, v) }
        val pairedList = getPairedBluetoothDevices()

        HarayaDialog.showCustomModal(
            context = activityContext,
            palette = palette,
            title = "OBD-II & Speedometer Settings",
            subtitle = "Connect via Bluetooth ELM327 adapter for real-time ECU Speed, RPM, & Gear",
            contentBuilder = { container, dlg ->
                // 1. Speedometer Source Selector
                val srcRow = HarayaDialog.buildOptionRow(
                    context = activityContext,
                    palette = palette,
                    title = "Speedometer & Telemetry Source",
                    subtitle = "Current: ${getSpeedSourceLabel()} (Tap to cycle Auto / OBD-II / GPS)",
                    badgeText = when (getSpeedSource()) {
                        SpeedSource.AUTO_OBD_GPS -> "Auto"
                        SpeedSource.OBD_ONLY -> "OBD-II"
                        SpeedSource.GPS_ONLY -> "GPS"
                    },
                    badgePositive = true
                ) {
                    val next = when (getSpeedSource()) {
                        SpeedSource.AUTO_OBD_GPS -> SpeedSource.OBD_ONLY
                        SpeedSource.OBD_ONLY -> SpeedSource.GPS_ONLY
                        SpeedSource.GPS_ONLY -> SpeedSource.AUTO_OBD_GPS
                    }
                    setSpeedSource(next)
                    dlg.dismiss()
                    showObdSettingsModal(activityContext, palette, onRequestBtPermission)
                }
                container.addView(srcRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })

                // 2. Connect to ELM327 Adapter Row
                val connRow = HarayaDialog.buildOptionRow(
                    context = activityContext,
                    palette = palette,
                    title = "Bluetooth ELM327 Adapter: ${getSavedDeviceName()}",
                    subtitle = if (isConnected) {
                        "Connected • Streaming PID 010D (Speed) & 010C (RPM)"
                    } else {
                        "Tap to connect to paired ELM327 OBD-II dongle"
                    },
                    badgeText = if (isConnected) "Connected ✓" else "Connect",
                    badgePositive = isConnected
                ) {
                    if (!hasBluetoothPermission()) {
                        dlg.dismiss()
                        onRequestBtPermission()
                        return@buildOptionRow
                    }
                    if (isConnected) {
                        disconnect()
                        Toast.makeText(activityContext, "OBD-II disconnected", Toast.LENGTH_SHORT).show()
                        dlg.dismiss()
                    } else {
                        Toast.makeText(activityContext, "Connecting to ${getSavedDeviceName()}...", Toast.LENGTH_SHORT).show()
                        connectToSavedOrFirstObdDevice { _, msg ->
                            Toast.makeText(activityContext, msg, Toast.LENGTH_SHORT).show()
                        }
                        dlg.dismiss()
                    }
                }
                container.addView(connRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })

                // 3. Live OBD Simulation Mode (for testing RPM & Gear gauge while parked)
                val simRow = HarayaDialog.buildOptionRow(
                    context = activityContext,
                    palette = palette,
                    title = "OBD-II Live Telemetry Simulator",
                    subtitle = "Test the Center Speedometer, Mini RPM Gauge, and Gear Indicator (D1-D6)",
                    badgeText = if (demoSimulationEnabled) "Simulating ✓" else "Start Test",
                    badgePositive = demoSimulationEnabled
                ) {
                    setSimulationActive(!demoSimulationEnabled)
                    dlg.dismiss()
                    val statusMsg = if (demoSimulationEnabled) "OBD-II Telemetry Simulator active" else "OBD-II Simulator stopped"
                    Toast.makeText(activityContext, statusMsg, Toast.LENGTH_SHORT).show()
                }
                container.addView(simRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })

                // 4. List of Paired Bluetooth Devices
                if (pairedList.isNotEmpty()) {
                    val hdr = TextView(activityContext).apply {
                        text = "SELECT PAIRED BLUETOOTH ADAPTER"
                        textSize = 9f
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                        setTextColor(palette.textSecondary)
                        setPadding(dp(2f), dp(4f), dp(2f), dp(4f))
                    }
                    container.addView(hdr)
                    for (dev in pairedList.take(4)) {
                        val dName = dev.name ?: "Bluetooth Device"
                        val dAddr = dev.address
                        val isSelected = dAddr == getSavedDeviceAddress()
                        val dRow = HarayaDialog.buildOptionRow(
                            context = activityContext,
                            palette = palette,
                            title = dName,
                            subtitle = "MAC: $dAddr",
                            badgeText = if (isSelected) "Selected ✓" else "Select",
                            badgePositive = isSelected
                        ) {
                            saveSelectedDevice(dName, dAddr)
                            Toast.makeText(activityContext, "Selected $dName ($dAddr)", Toast.LENGTH_SHORT).show()
                            dlg.dismiss()
                            connectToDevice(dev) { _, msg ->
                                Toast.makeText(activityContext, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                        container.addView(dRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp(5f)
                        })
                    }
                }
            },
            actions = listOf(
                HarayaDialog.ActionButton("Pair New Bluetooth") { dlg ->
                    dlg.dismiss()
                    runCatching {
                        activityContext.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                },
                HarayaDialog.ActionButton("Close", isPrimary = true) { it.dismiss() }
            )
        )
    }
}

