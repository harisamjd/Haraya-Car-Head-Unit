package com.haraya.dock.calc

import java.util.Calendar
import kotlin.math.*

/**
 * Offline Islamic Prayer Times Calculator
 * Menggunakan standar Kemenag RI (Subuh: -20°, Isya: -18°, Ihtiyat pengaman 2 menit).
 */
object PrayerCalc {

    data class PrayerTimes(
        val subuh: String,
        val terbit: String,
        val dzuhur: String,
        val ashar: String,
        val maghrib: String,
        val isya: String,
        val nextPrayerName: String,
        val nextPrayerTime: String,
        val nextPrayerRemainingMinutes: Int
    )

    fun calculate(
        latitude: Double,
        longitude: Double,
        calendar: Calendar = Calendar.getInstance()
    ): PrayerTimes {
        val timeZoneOffsetHours = calendar.timeZone.getOffset(calendar.timeInMillis) / 3600000.0
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1)

        val eqtime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma)
                - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))

        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(latitude)

        // Solar Noon (Dzuhur)
        val noonUtcMinutes = 720.0 - 4.0 * longitude - eqtime
        val dzuhurHour = (noonUtcMinutes / 60.0) + timeZoneOffsetHours + (2.0 / 60.0) // +2 menit ihtiyat

        fun hourForAngle(angleDeg: Double, morning: Boolean): Double {
            val angleRad = Math.toRadians(angleDeg)
            val cosHA = (sin(angleRad) - sin(latRad) * sin(decl)) / (cos(latRad) * cos(decl))
            if (cosHA > 1.0 || cosHA < -1.0) return dzuhurHour
            val haDeg = Math.toDegrees(acos(cosHA))
            val diffHours = haDeg / 15.0
            return if (morning) dzuhurHour - diffHours else dzuhurHour + diffHours
        }

        val subuhHour = hourForAngle(-20.0, true) + (2.0 / 60.0)
        val terbitHour = hourForAngle(-0.833, true)

        val shadowRatio = 1.0 + tan(abs(latRad - decl))
        val asharAngleRad = atan(1.0 / shadowRatio)
        val asharAngleDeg = Math.toDegrees(asharAngleRad)
        val asharHour = hourForAngle(asharAngleDeg, false) + (2.0 / 60.0)

        val maghribHour = hourForAngle(-0.833, false) + (2.0 / 60.0)
        val isyaHour = hourForAngle(-18.0, false) + (2.0 / 60.0)

        fun format(hourDec: Double): String {
            val totalMinutes = ((hourDec + 24.0) % 24.0 * 60.0).roundToInt()
            val h = (totalMinutes / 60) % 24
            val m = totalMinutes % 60
            return String.format("%02d:%02d", h, m)
        }

        val subuhStr = format(subuhHour)
        val terbitStr = format(terbitHour)
        val dzuhurStr = format(dzuhurHour)
        val asharStr = format(asharHour)
        val maghribStr = format(maghribHour)
        val isyaStr = format(isyaHour)

        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val prayerSchedule = listOf(
            Triple("Subuh", (subuhHour * 60).roundToInt(), subuhStr),
            Triple("Dzuhur", (dzuhurHour * 60).roundToInt(), dzuhurStr),
            Triple("Ashar", (asharHour * 60).roundToInt(), asharStr),
            Triple("Maghrib", (maghribHour * 60).roundToInt(), maghribStr),
            Triple("Isya", (isyaHour * 60).roundToInt(), isyaStr)
        )

        var nextName = "Subuh"
        var nextTime = subuhStr
        var remainingMin = 0
        for ((name, pMin, timeStr) in prayerSchedule) {
            if (pMin > currentMinutes) {
                nextName = name
                nextTime = timeStr
                remainingMin = pMin - currentMinutes
                break
            }
        }
        if (remainingMin == 0) {
            nextName = "Subuh"
            nextTime = subuhStr
            remainingMin = (24 * 60 - currentMinutes) + (subuhHour * 60).roundToInt()
        }

        return PrayerTimes(
            subuh = subuhStr,
            terbit = terbitStr,
            dzuhur = dzuhurStr,
            ashar = asharStr,
            maghrib = maghribStr,
            isya = isyaStr,
            nextPrayerName = nextName,
            nextPrayerTime = nextTime,
            nextPrayerRemainingMinutes = remainingMin
        )
    }
}
