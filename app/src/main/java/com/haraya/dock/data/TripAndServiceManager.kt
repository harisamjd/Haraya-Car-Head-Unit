package com.haraya.dock.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

data class TripRecord(
    val dateLabel: String,
    val distanceKm: Float,
    val durationMinutes: Int,
    val avgSpeedKmh: Int
)

data class CurrentTripState(
    val distanceKm: Float,
    val durationMinutes: Int,
    val avgSpeedKmh: Int,
    val hasStarted: Boolean
)

data class ServiceItem(
    val id: String,
    val title: String,
    val unitType: String, // "km" or "days"
    val remainingValue: Int,
    val defaultTarget: Int
) {
    val isDue: Boolean get() = remainingValue <= 0
    val statusText: String
        get() = if (isDue) {
            "0 $unitType (Due Now)"
        } else {
            val shortUnit = if (unitType == "days") "d" else unitType
            "$remainingValue $shortUnit"
        }
}

class TripAndServiceManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("haraya_cockpit_data", Context.MODE_PRIVATE)
    private val customCarFile = File(context.filesDir, "custom_car_image.png")

    private var tripStartTimeMs: Long = 0L
    private var lastMovementTimeMs: Long = 0L
    private var accumulatedMeters: Float = 0f
    private var lastLocation: Location? = null

    fun onLocationUpdate(loc: Location) {
        val now = System.currentTimeMillis()
        val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f

        // Auto-save trip after 15 minutes parked
        if (tripStartTimeMs > 0L && lastMovementTimeMs > 0L && (now - lastMovementTimeMs) > 15 * 60 * 1000L) {
            saveCurrentTripToHistory()
        }

        val prev = lastLocation
        if (prev != null) {
            val deltaM = prev.distanceTo(loc)
            if (deltaM in 3f..800f && (speedKmh >= 2.5f || deltaM >= 8f)) {
                if (tripStartTimeMs == 0L) {
                    tripStartTimeMs = now
                }
                lastMovementTimeMs = now
                accumulatedMeters += deltaM
                deductServiceKilometers(deltaM / 1000f)
            }
        } else if (speedKmh >= 3f) {
            if (tripStartTimeMs == 0L) tripStartTimeMs = now
            lastMovementTimeMs = now
        }
        lastLocation = loc
    }

    fun getCurrentTripState(): CurrentTripState {
        if (tripStartTimeMs == 0L || accumulatedMeters < 10f) {
            return CurrentTripState(
                distanceKm = 0f,
                durationMinutes = 0,
                avgSpeedKmh = 0,
                hasStarted = false
            )
        }
        val now = System.currentTimeMillis()
        val durMin = ((now - tripStartTimeMs) / 60000L).toInt().coerceAtLeast(1)
        val distKm = accumulatedMeters / 1000f
        val hours = (now - tripStartTimeMs).toFloat() / 3600000f
        val avgKmh = if (hours > 0.005f) (distKm / hours).roundToInt().coerceIn(1, 180) else 20

        return CurrentTripState(
            distanceKm = distKm,
            durationMinutes = durMin,
            avgSpeedKmh = avgKmh,
            hasStarted = true
        )
    }

    fun saveCurrentTripToHistory() {
        val state = getCurrentTripState()
        if (!state.hasStarted || state.distanceKm < 0.05f) {
            resetCurrentTrip()
            return
        }
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.US)
        val rec = TripRecord(
            dateLabel = fmt.format(Date(tripStartTimeMs)),
            distanceKm = state.distanceKm,
            durationMinutes = state.durationMinutes,
            avgSpeedKmh = state.avgSpeedKmh
        )

        val currentList = getTripHistory().toMutableList()
        currentList.add(0, rec)
        while (currentList.size > 5) {
            currentList.removeAt(currentList.lastIndex)
        }
        persistHistory(currentList)
        resetCurrentTrip()
    }

    fun resetCurrentTrip() {
        tripStartTimeMs = 0L
        lastMovementTimeMs = 0L
        accumulatedMeters = 0f
    }

    fun clearTripHistory() {
        prefs.edit().remove("trip_history_json").apply()
    }

    fun getTripHistory(): List<TripRecord> {
        val raw = prefs.getString("trip_history_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<TripRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TripRecord(
                        dateLabel = obj.optString("date", "--/-- --:--"),
                        distanceKm = obj.optDouble("dist", 0.0).toFloat(),
                        durationMinutes = obj.optInt("dur", 1),
                        avgSpeedKmh = obj.optInt("avg", 0)
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun persistHistory(list: List<TripRecord>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("date", item.dateLabel)
                put("dist", item.distanceKm.toDouble())
                put("dur", item.durationMinutes)
                put("avg", item.avgSpeedKmh)
            }
            arr.put(obj)
        }
        prefs.edit().putString("trip_history_json", arr.toString()).apply()
    }

    private fun deductServiceKilometers(deltaKm: Float) {
        val carry = prefs.getFloat("km_fraction_carry", 0f) + deltaKm
        val wholeKm = carry.toInt()
        if (wholeKm >= 1) {
            prefs.edit().putFloat("km_fraction_carry", carry - wholeKm).apply()
            val items = getServiceItems()
            for (item in items) {
                if (item.unitType == "km") {
                    val nextVal = (item.remainingValue - wholeKm).coerceAtLeast(0)
                    prefs.edit().putInt("svc_${item.id}", nextVal).apply()
                }
            }
        } else {
            prefs.edit().putFloat("km_fraction_carry", carry).apply()
        }
    }

    fun getServiceItems(): List<ServiceItem> {
        return listOf(
            ServiceItem(
                id = "oli_mesin",
                title = "Engine Oil",
                unitType = "km",
                remainingValue = prefs.getInt("svc_oli_mesin", 1000),
                defaultTarget = 5000
            ),
            ServiceItem(
                id = "oli_matic",
                title = "ATF / Gear Oil",
                unitType = "km",
                remainingValue = prefs.getInt("svc_oli_matic", 5000),
                defaultTarget = 20000
            ),
            ServiceItem(
                id = "servis_ac",
                title = "AC Service",
                unitType = "days",
                remainingValue = prefs.getInt("svc_servis_ac", 182),
                defaultTarget = 180
            ),
            ServiceItem(
                id = "tuneup",
                title = "Tune Up",
                unitType = "km",
                remainingValue = prefs.getInt("svc_tuneup", 5000),
                defaultTarget = 10000
            )
        )
    }

    fun updateServiceValue(id: String, newRemaining: Int) {
        prefs.edit().putInt("svc_$id", newRemaining.coerceAtLeast(0)).apply()
    }

    // ==============================================
    // SERVICE PIN & VEHICLE IDENTITY + CUSTOM IMAGE
    // ==============================================
    fun getServicePin(): String {
        return prefs.getString("service_pin", "0000") ?: "0000"
    }

    fun verifyServicePin(inputPin: String): Boolean {
        return inputPin.trim() == getServicePin()
    }

    fun setServicePin(newPin: String) {
        val clean = newPin.trim().ifEmpty { "0000" }
        prefs.edit().putString("service_pin", clean).apply()
    }

    fun getVehicleName(): String {
        return prefs.getString("vehicle_name", "Suzuki Baleno") ?: "Suzuki Baleno"
    }

    fun getVehicleSubtitle(): String {
        return prefs.getString("vehicle_subtitle", "Hatchback • B 1353 EYX") ?: "Hatchback • B 1353 EYX"
    }

    fun setVehicleIdentity(name: String, subtitle: String) {
        prefs.edit()
            .putString("vehicle_name", name.trim().ifEmpty { "Suzuki Baleno" })
            .putString("vehicle_subtitle", subtitle.trim().ifEmpty { "Hatchback • B 1353 EYX" })
            .apply()
    }

    fun hasCustomCarImage(): Boolean {
        return customCarFile.exists() && customCarFile.length() > 0L
    }

    fun getCustomCarBitmap(): Bitmap? {
        if (!hasCustomCarImage()) return null
        return runCatching {
            BitmapFactory.decodeFile(customCarFile.absolutePath)
        }.getOrNull()
    }

    fun saveCustomCarImageFromUri(uri: Uri): Boolean {
        return runCatching {
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return false

            // Scale down if larger than 960px wide to keep UI fast and memory light
            val maxDim = 960
            val w = rawBitmap.width
            val h = rawBitmap.height
            val finalBmp = if (w > maxDim || h > maxDim) {
                val scale = maxDim.toFloat() / maxOf(w, h).toFloat()
                val nw = (w * scale).roundToInt().coerceAtLeast(1)
                val nh = (h * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(rawBitmap, nw, nh, true)
            } else {
                rawBitmap
            }

            FileOutputStream(customCarFile).use { out ->
                finalBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        }.getOrDefault(false)
    }

    fun resetCustomCarImage() {
        runCatching {
            if (customCarFile.exists()) {
                customCarFile.delete()
            }
        }
    }
}
