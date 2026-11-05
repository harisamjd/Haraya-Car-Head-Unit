package com.haraya.dock.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeatherState(
    val tempCelsius: Int,
    val conditionLabel: String,
    val isLive: Boolean
)

class WeatherFetcher(context: Context) {

    private val prefs = context.getSharedPreferences("haraya_weather", Context.MODE_PRIVATE)
    private var lastFetchMs: Long = 0L
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    fun getCachedWeather(): WeatherState {
        val temp = prefs.getInt("temp_c", 28)
        val cond = prefs.getString("cond_label", "Cerah Berawan • Open-Meteo") ?: "Cerah Berawan • Open-Meteo"
        val isLive = prefs.getBoolean("is_live", false)
        return WeatherState(temp, cond, isLive)
    }

    fun fetchIfNeeded(lat: Double, lon: Double, onResult: (WeatherState) -> Unit) {
        val now = System.currentTimeMillis()
        val movedSignificantly = abs(lat - lastLat) > 0.03 || abs(lon - lastLon) > 0.03
        if (!movedSignificantly && (now - lastFetchMs) < 15 * 60 * 1000L) {
            onResult(getCachedWeather())
            return
        }
        lastFetchMs = now
        lastLat = lat
        lastLon = lon

        Thread {
            try {
                val urlStr = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current_weather=true",
                    lat,
                    lon
                )
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val cur = json.getJSONObject("current_weather")
                    val temp = cur.getDouble("temperature").roundToInt()
                    val code = cur.optInt("weathercode", 0)
                    val label = "${mapWmoCode(code)} • Open-Meteo"

                    prefs.edit()
                        .putInt("temp_c", temp)
                        .putString("cond_label", label)
                        .putBoolean("is_live", true)
                        .apply()

                    onResult(WeatherState(temp, label, true))
                } else {
                    onResult(getCachedWeather())
                }
                conn.disconnect()
            } catch (_: Exception) {
                onResult(getCachedWeather())
            }
        }.start()
    }

    private fun mapWmoCode(code: Int): String = when (code) {
        0 -> "Cerah"
        1, 2, 3 -> "Cerah Berawan"
        45, 48 -> "Berkabut"
        51, 53, 55, 61, 63, 65 -> "Hujan Ringan"
        80, 81, 82 -> "Hujan Lokal"
        95, 96, 99 -> "Hujan Petir"
        else -> "Berawan"
    }
}

