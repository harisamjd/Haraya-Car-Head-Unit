package com.haraya.dock.theme

import com.haraya.dock.calc.SolarCalc
import java.util.Calendar

enum class ThemeMode {
    LIGHT,
    DARK,
    AUTO
}

data class ThemeResult(
    val isDark: Boolean,
    val reason: String
)

/**
 * Resolver untuk menentukan tema aktif berdasarkan Mode, GPS Matahari, dan Histeresis.
 */
class ThemeResolver {
    private var currentDark: Boolean = true

    fun resolve(
        mode: ThemeMode,
        latitude: Double?,
        longitude: Double?,
        currentTimeMs: Long = System.currentTimeMillis(),
        calendar: Calendar = Calendar.getInstance()
    ): ThemeResult {
        when (mode) {
            ThemeMode.LIGHT -> {
                currentDark = false
                return ThemeResult(isDark = false, reason = "Manual: Light Mode")
            }
            ThemeMode.DARK -> {
                currentDark = true
                return ThemeResult(isDark = true, reason = "Manual: Dark Mode")
            }
            ThemeMode.AUTO -> {
                val candidateDark: Boolean
                val reasonText: String

                if (latitude != null && longitude != null) {
                    val sunTimes = SolarCalc.calculate(latitude, longitude, calendar)
                    val isNightTime = SolarCalc.isNight(latitude, longitude, calendar)
                    candidateDark = isNightTime
                    val sunriseTotalMin = ((sunTimes.sunriseHour + 24.0) % 24.0 * 60.0).toInt()
                    val sunsetTotalMin = ((sunTimes.sunsetHour + 24.0) % 24.0 * 60.0).toInt()
                    val sunriseStr = String.format("%02d:%02d", (sunriseTotalMin / 60) % 24, sunriseTotalMin % 60)
                    val sunsetStr = String.format("%02d:%02d", (sunsetTotalMin / 60) % 24, sunsetTotalMin % 60)
                    reasonText = if (isNightTime) {
                        "Auto: Dark (Sunrise $sunriseStr • Sunset $sunsetStr)"
                    } else {
                        "Auto: Light (Sunrise $sunriseStr • Sunset $sunsetStr)"
                    }
                } else {
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    val isNightHour = hour >= 18 || hour < 6
                    candidateDark = isNightHour
                    reasonText = if (isNightHour) "Auto: Dark (18:00 - 06:00)" else "Auto: Light (06:00 - 18:00)"
                }

                currentDark = candidateDark

                return ThemeResult(
                    isDark = currentDark,
                    reason = reasonText
                )
            }
        }
    }
}

