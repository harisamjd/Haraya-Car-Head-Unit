package com.haraya.dock.theme

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ThemeResolverTest {

    @Test
    fun testManualModes() {
        val resolver = ThemeResolver()
        val lightResult = resolver.resolve(ThemeMode.LIGHT, null, null)
        assertFalse(lightResult.isDark)

        val darkResult = resolver.resolve(ThemeMode.DARK, null, null)
        assertTrue(darkResult.isDark)
    }

    @Test
    fun testHysteresisPreventsFlicker() {
        // Interval histeresis 10 menit (600.000 ms)
        val resolver = ThemeResolver(minSwitchIntervalMs = 600_000L)

        val calNoon = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply {
            set(Calendar.HOUR_OF_DAY, 12)
        }
        val calNight = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply {
            set(Calendar.HOUR_OF_DAY, 21)
        }

        // T0: Siang hari -> Terang
        val t0 = 1_000_000L
        val r0 = resolver.resolve(ThemeMode.AUTO, -6.1754, 106.8272, currentTimeMs = t0, calendar = calNoon)
        assertFalse(r0.isDark)

        // T1: 1 menit kemudian (misal masuk terowongan gelap) -> tema TIDAK boleh langsung berubah karena histeresis
        val t1 = t0 + 60_000L
        val r1 = resolver.resolve(ThemeMode.AUTO, -6.1754, 106.8272, currentTimeMs = t1, calendar = calNight)
        assertFalse("Histeresis harus mencegah kedip tema dalam rentang < 10 menit", r1.isDark)

        // T2: 11 menit kemudian -> baru diizinkan berubah ke Gelap
        val t2 = t0 + 660_000L
        val r2 = resolver.resolve(ThemeMode.AUTO, -6.1754, 106.8272, currentTimeMs = t2, calendar = calNight)
        assertTrue("Setelah lewat 10 menit, tema berhasil beralih", r2.isDark)
    }
}

