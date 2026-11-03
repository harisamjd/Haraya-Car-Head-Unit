package com.haraya.dock.calc

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/**
 * Astronomical Solar Calculator (NOAA Algorithm)
 * Menghitung waktu Sunrise, Sunset, dan Solar Noon secara offline tanpa koneksi internet.
 */
object SolarCalc {

    data class SunTimes(
        val sunriseHour: Double, // dalam jam desimal lokal (misal: 5.75 = 05:45)
        val sunsetHour: Double,  // dalam jam desimal lokal (misal: 17.85 = 17:51)
        val isAlwaysDay: Boolean = false,
        val isAlwaysNight: Boolean = false
    )

    /**
     * Hitung waktu matahari terbit & terbenam berdasarkan koordinat GPS dan kalender.
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        calendar: Calendar = Calendar.getInstance()
    ): SunTimes {
        val timeZoneOffsetHours = calendar.timeZone.getOffset(calendar.timeInMillis) / 3600000.0

        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        // Fractional year in radians
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1 + (calendar.get(Calendar.HOUR_OF_DAY) - 12) / 24.0)

        // Equation of time (in minutes)
        val eqtime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma)
                - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))

        // Solar declination angle (in radians)
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(latitude)
        val zenithRad = Math.toRadians(90.833) // Standard solar zenith with refraction correction

        // Hour angle
        val cosHourAngle = (cos(zenithRad) / (cos(latRad) * cos(decl))) - (tan(latRad) * tan(decl))

        if (cosHourAngle > 1.0) {
            // Polar night (selalu malam)
            return SunTimes(0.0, 0.0, isAlwaysNight = true)
        }
        if (cosHourAngle < -1.0) {
            // Midnight sun (selalu siang)
            return SunTimes(0.0, 0.0, isAlwaysDay = true)
        }

        val haRad = acos(cosHourAngle)
        val haDeg = Math.toDegrees(haRad)

        // Sunrise and sunset in UTC minutes from midnight
        val sunriseMinutesUtc = 720.0 - 4.0 * longitude - eqtime - haDeg * 4.0
        val sunsetMinutesUtc = 720.0 - 4.0 * longitude - eqtime + haDeg * 4.0

        val sunriseLocalHour = (sunriseMinutesUtc / 60.0) + timeZoneOffsetHours
        val sunsetLocalHour = (sunsetMinutesUtc / 60.0) + timeZoneOffsetHours

        return SunTimes(
            sunriseHour = (sunriseLocalHour + 24.0) % 24.0,
            sunsetHour = (sunsetLocalHour + 24.0) % 24.0
        )
    }

    /**
     * Cek apakah saat ini waktu malam (setelah sunset atau sebelum sunrise).
     */
    fun isNight(
        latitude: Double,
        longitude: Double,
        calendar: Calendar = Calendar.getInstance()
    ): Boolean {
        val sun = calculate(latitude, longitude, calendar)
        if (sun.isAlwaysNight) return true
        if (sun.isAlwaysDay) return false

        val currentHour = calendar.get(Calendar.HOUR_OF_DAY) +
                calendar.get(Calendar.MINUTE) / 60.0 +
                calendar.get(Calendar.SECOND) / 3600.0

        return if (sun.sunsetHour > sun.sunriseHour) {
            currentHour >= sun.sunsetHour || currentHour < sun.sunriseHour
        } else {
            currentHour >= sun.sunsetHour && currentHour < sun.sunriseHour
        }
    }
}

