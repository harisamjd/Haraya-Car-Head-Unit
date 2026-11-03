package com.haraya.dock.calc

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SolarAndPrayerCalcTest {

    @Test
    fun testJakartaSunsetAndSunrise() {
        // Koordinat Monas Jakarta: -6.1754, 106.8272 (WIB = GMT+7)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply {
            set(2026, Calendar.NOVEMBER, 2, 12, 0, 0)
        }

        val sun = SolarCalc.calculate(-6.1754, 106.8272, cal)

        // Sunrise di Jakarta awal November berkisar antara 05:25 - 05:40 WIB
        assertTrue("Sunrise harus sekitar jam 5 pagi", sun.sunriseHour in 5.3..5.7)

        // Sunset di Jakarta awal November berkisar antara 17:40 - 18:00 WIB
        assertTrue("Sunset harus sekitar jam 17:40 - 18:00 sore", sun.sunsetHour in 17.6..18.0)

        // Jam 12 siang bukan malam
        assertFalse("Jam 12 siang tidak boleh malam", SolarCalc.isNight(-6.1754, 106.8272, cal))

        // Jam 20 malam harus malam
        cal.set(Calendar.HOUR_OF_DAY, 20)
        assertTrue("Jam 20:00 harus malam", SolarCalc.isNight(-6.1754, 106.8272, cal))
    }

    @Test
    fun testPrayerTimesJakarta() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply {
            set(2026, Calendar.NOVEMBER, 2, 11, 45, 0)
        }

        val prayer = PrayerCalc.calculate(-6.1754, 106.8272, cal)

        assertNotNull(prayer.subuh)
        assertNotNull(prayer.dzuhur)
        assertNotNull(prayer.ashar)
        assertNotNull(prayer.maghrib)
        assertNotNull(prayer.isya)

        // Pada jam 11:45 WIB di Jakarta, sholat berikutnya adalah Dzuhur (sekitar 11:40 - 11:50)
        assertTrue(prayer.nextPrayerName == "Dzuhur" || prayer.nextPrayerName == "Ashar")
        assertTrue(prayer.nextPrayerTime.isNotBlank())

        // Pada jam 16:56 WIB di Jakarta, sholat berikutnya adalah Maghrib dan waktunya sesuai maghrib
        cal.set(Calendar.HOUR_OF_DAY, 16)
        cal.set(Calendar.MINUTE, 56)
        val afternoonPrayer = PrayerCalc.calculate(-6.1754, 106.8272, cal)
        assertEquals("Maghrib", afternoonPrayer.nextPrayerName)
        assertEquals(afternoonPrayer.maghrib, afternoonPrayer.nextPrayerTime)
    }
}

