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
class ThemeResolver(
    private val minSwitchIntervalMs: Long = 10 * 60 * 1000L // 10 menit histeresis
) {
    private var lastSwitchTimeMs: Long = 0L
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
                return ThemeResult(isDark = false, reason = "Manual: Terang")
            }
            ThemeMode.DARK -> {
                currentDark = true
                return ThemeResult(isDark = true, reason = "Manual: Gelap")
            }
            ThemeMode.AUTO -> {
                val candidateDark: Boolean
                val reasonText: String

                if (latitude != null && longitude != null) {
                    val sunTimes = SolarCalc.calculate(latitude, longitude, calendar)
                    val isNightTime = SolarCalc.isNight(latitude, longitude, calendar)
                    candidateDark = isNightTime
                    val sunsetStr = String.format("%02d:%02d", sunTimes.sunsetHour.toInt(), ((sunTimes.sunsetHour % 1.0) * 60).toInt())
                    val sunriseStr = String.format("%02d:%02d", sunTimes.sunriseHour.toInt(), ((sunTimes.sunriseHour % 1.0) * 60).toInt())
                    reasonText = if (isNightTime) {
                        "Otomatis: Gelap (Sunset $sunsetStr)"
                    } else {
                        "Otomatis: Terang (Sunrise $sunriseStr)"
                    }
                } else {
                    // Fallback jam tetap jika belum ada sinyal GPS (18:00 - 06:00)
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    val isNightHour = hour >= 18 || hour < 6
                    candidateDark = isNightHour
                    reasonText = if (isNightHour) "Otomatis: Gelap (Jadwal malam)" else "Otomatis: Terang (Jadwal siang)"
                }

                // Cek Histeresis: jangan ubah tema jika baru saja berganti kurang dari 10 menit
                // (mencegah kedip di terowongan atau kolong jembatan)
                if (candidateDark != currentDark) {
                    if (lastSwitchTimeMs == 0L || (currentTimeMs - lastSwitchTimeMs) >= minSwitchIntervalMs) {
                        currentDark = candidateDark
                        lastSwitchTimeMs = currentTimeMs
                    }
                }

                return ThemeResult(
                    isDark = currentDark,
                    reason = reasonText
                )
            }
        }
    }
}

