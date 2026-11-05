package com.haraya.dock.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherState(
    val tempCelsius: Int,
    val conditionLabel: String,
    val isLive: Boolean
)

class WeatherFetcher(context: Context) {

    private val prefs = context.getSharedPreferences("haraya_weather_prefs", Context.MODE_PRIVATE)
    private var lastFetchMs: Long = 0L

    fun getCachedWeather(): WeatherState {
        val temp = prefs.getInt("temp_c", 28)
        val rawDesc = prefs.getString("desc", "Partly Cloudy") ?: "Partly Cloudy"
        val cleanDesc = translateLegacyWeatherLabel(rawDesc)
        return WeatherState(temp, cleanDesc, false)
    }

    private fun translateLegacyWeatherLabel(label: String): String {
        val base = label.split("•").firstOrNull()?.trim() ?: label
        return when (base.lowercase()) {
            "cerah" -> "Clear Sky"
            "cerah berawan" -> "Partly Cloudy"
            "berkabut" -> "Foggy"
            "gerimis" -> "Light Drizzle"
            "hujan ringan" -> "Light Rain"
            "hujan deras" -> "Heavy Rain"
            "hujan badai" -> "Thunderstorm"
            "berawan" -> "Cloudy"
            else -> base
        }
    }

    fun fetchIfNeeded(lat: Double, lon: Double, onResult: (WeatherState) -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastFetchMs < 10 * 60 * 1000L) {
            onResult(getCachedWeather())
            return
        }
        lastFetchMs = now

        Thread {
            try {
                val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    val cw = root.getJSONObject("current_weather")
                    val temp = cw.getDouble("temperature").toInt()
                    val code = cw.optInt("weathercode", 1)
                    val label = mapWmoCode(code)

                    prefs.edit()
                        .putInt("temp_c", temp)
                        .putString("desc", label)
                        .apply()

                    onResult(WeatherState(temp, label, true))
                } else {
                    onResult(getCachedWeather())
                }
            } catch (_: Exception) {
                onResult(getCachedWeather())
            }
        }.start()
    }

    private fun mapWmoCode(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2, 3 -> "Partly Cloudy"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Light Drizzle"
        61, 63, 65 -> "Light Rain"
        80, 81, 82 -> "Heavy Rain"
        95, 96, 99 -> "Thunderstorm"
        else -> "Cloudy"
    }
}
