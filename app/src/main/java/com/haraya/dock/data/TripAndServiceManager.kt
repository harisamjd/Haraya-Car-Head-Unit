package com.haraya.dock.data

import android.content.Context
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

data class CurrentTripState(
    val distanceKm: Float,
    val durationMinutes: Int,
    val avgSpeedKmh: Int,
    val hasStarted: Boolean
)

data class TripHistoryEntry(
    val dateLabel: String,
    val distanceKm: Float,
    val durationMinutes: Int,
    val avgSpeedKmh: Int
)

data class ServiceItemState(
    val id: String,
    val title: String,
    val unitType: String, // "km" atau "hr"
    val remainingValue: Int,
    val defaultTarget: Int
) {
    val isDue: Boolean get() = remainingValue <= 0
    val statusText: String
        get() = if (isDue) {
            "0 $unitType (Jatuh Tempo)"
        } else {
            "$remainingValue $unitType"
        }
}

class TripAndServiceManager(context: Context) {

    private val prefs = context.getSharedPreferences("haraya_trip_service", Context.MODE_PRIVATE)

    private var lastLocation: Location? = null
    private var tripStartMs: Long = 0L
    private var lastMoveMs: Long = 0L
    private var currentDistanceMeters: Float = 0f

    init {
        initDefaultServicesIfNeeded()
    }

    private fun initDefaultServicesIfNeeded() {
        if (!prefs.contains("svc_init_v1")) {
            prefs.edit()
                .putBoolean("svc_init_v1", true)
                .putInt("svc_oli_mesin", 1000)
                .putInt("svc_oli_matic", 5000)
                .putInt("svc_servis_ac", 182)
                .putInt("svc_tuneup", 5000)
                .apply()
        }
    }

    fun onLocationUpdate(location: Location): CurrentTripState {
        val now = System.currentTimeMillis()
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f

        // Cek apakah perjalanan sebelumnya sudah parkir >= 15 menit
        if (tripStartMs > 0L && lastMoveMs > 0L && (now - lastMoveMs) >= 15 * 60 * 1000L) {
            saveCurrentTripToHistory()
        }

        val prev = lastLocation
        if (prev != null) {
            val deltaM = prev.distanceTo(location)
            // Akumulasi jika bergerak nyata (antara 2 meter s/d 500 meter per titik)
            if (speedKmh >= 2.5f && deltaM in 2.0f..500.0f) {
                if (tripStartMs == 0L) {
                    tripStartMs = now
                }
                lastMoveMs = now
                currentDistanceMeters += deltaM
                decrementKmServices(deltaM / 1000f)
            }
        } else if (speedKmh >= 3.0f) {
            if (tripStartMs == 0L) tripStartMs = now
            lastMoveMs = now
        }

        lastLocation = location
        return getCurrentTripState()
    }

    fun getCurrentTripState(): CurrentTripState {
        if (tripStartMs == 0L || currentDistanceMeters < 10f) {
            return CurrentTripState(0f, 0, 0, false)
        }
        val activeEndMs = if (lastMoveMs > tripStartMs) lastMoveMs else System.currentTimeMillis()
        val elapsedMinutes = ((activeEndMs - tripStartMs) / 60000L).toInt().coerceAtLeast(1)
        val distKm = currentDistanceMeters / 1000f
        val elapsedHours = (activeEndMs - tripStartMs).coerceAtLeast(1000L) / 3600000f
        val avgKmh = (distKm / elapsedHours).roundToInt().coerceIn(1, 180)
        return CurrentTripState(distKm, elapsedMinutes, avgKmh, true)
    }

    fun saveCurrentTripToHistory(): Boolean {
        val state = getCurrentTripState()
        if (!state.hasStarted || state.distanceKm < 0.1f) return false

        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale("id", "ID"))
        val entry = TripHistoryEntry(
            dateLabel = fmt.format(Date(tripStartMs)),
            distanceKm = state.distanceKm,
            durationMinutes = state.durationMinutes,
            avgSpeedKmh = state.avgSpeedKmh
        )

        val currentList = getTripHistory().toMutableList()
        currentList.add(0, entry)
        while (currentList.size > 5) {
            currentList.removeAt(currentList.lastIndex)
        }

        val arr = JSONArray()
        for (item in currentList) {
            val obj = JSONObject().apply {
                put("date", item.dateLabel)
                put("dist", item.distanceKm.toDouble())
                put("dur", item.durationMinutes)
                put("avg", item.avgSpeedKmh)
            }
            arr.put(obj)
        }
        prefs.edit().putString("trip_history_json", arr.toString()).apply()

        // Reset perjalanan saat ini
        tripStartMs = 0L
        lastMoveMs = 0L
        currentDistanceMeters = 0f
        return true
    }

    fun getTripHistory(): List<TripHistoryEntry> {
        val raw = prefs.getString("trip_history_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<TripHistoryEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    TripHistoryEntry(
                        dateLabel = o.optString("date", "--/--"),
                        distanceKm = o.optDouble("dist", 0.0).toFloat(),
                        durationMinutes = o.optInt("dur", 1),
                        avgSpeedKmh = o.optInt("avg", 0)
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun clearTripHistory() {
        prefs.edit().remove("trip_history_json").apply()
    }

    private fun decrementKmServices(deltaKm: Float) {
        val accum = prefs.getFloat("svc_km_fraction", 0f) + deltaKm
        if (accum >= 1.0f) {
            val wholeKm = accum.toInt()
            val rem = accum - wholeKm
            val e = prefs.edit().putFloat("svc_km_fraction", rem)
            for (key in listOf("svc_oli_mesin", "svc_oli_matic", "svc_tuneup")) {
                val cur = prefs.getInt(key, 1000)
                e.putInt(key, (cur - wholeKm).coerceAtLeast(0))
            }
            e.apply()
        } else {
            prefs.edit().putFloat("svc_km_fraction", accum).apply()
        }
    }

    fun getServiceItems(): List<ServiceItemState> {
        return listOf(
            ServiceItemState(
                id = "svc_oli_mesin",
                title = "Oli Mesin",
                unitType = "km",
                remainingValue = prefs.getInt("svc_oli_mesin", 1000),
                defaultTarget = 5000
            ),
            ServiceItemState(
                id = "svc_oli_matic",
                title = "Oli Matic",
                unitType = "km",
                remainingValue = prefs.getInt("svc_oli_matic", 5000),
                defaultTarget = 20000
            ),
            ServiceItemState(
                id = "svc_servis_ac",
                title = "Servis AC",
                unitType = "hr",
                remainingValue = prefs.getInt("svc_servis_ac", 182),
                defaultTarget = 180
            ),
            ServiceItemState(
                id = "svc_tuneup",
                title = "Tuneup",
                unitType = "km",
                remainingValue = prefs.getInt("svc_tuneup", 5000),
                defaultTarget = 10000
            )
        )
    }

    fun updateServiceValue(id: String, newValue: Int) {
        prefs.edit().putInt(id, newValue.coerceAtLeast(0)).apply()
    }

    fun getServicePin(): String {
        return prefs.getString("service_pin", "0000") ?: "0000"
    }

    fun verifyServicePin(input: String): Boolean {
        return input.trim() == getServicePin()
    }

    fun setServicePin(newPin: String) {
        val clean = newPin.trim().ifEmpty { "0000" }
        prefs.edit().putString("service_pin", clean).apply()
    }

    fun getVehicleName(): String {
        return prefs.getString("vehicle_name", "Suzuki Baleno") ?: "Suzuki Baleno"
    }

    fun getVehicleSubtitle(): String {
        return prefs.getString("vehicle_sub", "Hatchback • B 1353 EYX") ?: "Hatchback • B 1353 EYX"
    }

    fun setVehicleIdentity(name: String, subtitle: String) {
        prefs.edit()
            .putString("vehicle_name", name.trim().ifEmpty { "Suzuki Baleno" })
            .putString("vehicle_sub", subtitle.trim().ifEmpty { "Hatchback • B 1353 EYX" })
            .apply()
    }
}

