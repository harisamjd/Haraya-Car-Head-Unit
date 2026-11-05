package com.haraya.dock.ui.glass

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewOutlineProvider

data class GlassPalette(
    val isDark: Boolean,
    val windowBg: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textMuted: Int,
    val accent: Int,
    val accentLight: Int,
    val track: Int,
    val line: Int,
    val stroke: Int,
    val ripple: Int,
    val cardTop: Int,
    val cardBottom: Int,
    val innerTop: Int,
    val innerBottom: Int,
    val overTop: Int,
    val overBottom: Int,
    val sidebarTop: Int,
    val sidebarBottom: Int,
    val selectTop: Int,
    val selectBottom: Int,
    val selectFg: Int,
    val chip: Int
)

object GlassTheme {

    // Mode Terang: Warm Alabaster Stone + Instrument Teal (Anti-Glare Siang Hari, 0% Ungu)
    val Light = GlassPalette(
        isDark = false,
        windowBg = 0xFFECEAE4.toInt(),
        textPrimary = 0xFF14171A.toInt(),
        textSecondary = 0xFF4A525A.toInt(),
        textMuted = 0xFF68707A.toInt(),
        accent = 0xFF0F766E.toInt(),
        accentLight = 0xFF0D9488.toInt(),
        track = 0x20000000,
        line = 0x18000000,
        stroke = 0xFFD8D4CA.toInt(),
        ripple = 0x1F000000,
        cardTop = 0xFFFAF9F5.toInt(),
        cardBottom = 0xFFF3F1EC.toInt(),
        innerTop = 0xFFEAE7DF.toInt(),
        innerBottom = 0xFFE4E0D7.toInt(),
        overTop = 0xFAFAF9F5.toInt(),
        overBottom = 0xF5F3F1EC.toInt(),
        sidebarTop = 0xFFFAF9F5.toInt(),
        sidebarBottom = 0xFFF3F1EC.toInt(),
        selectTop = 0xFFD5EDE9.toInt(),
        selectBottom = 0xFFC4E5DF.toInt(),
        selectFg = 0xFF0F534E.toInt(),
        chip = 0xFFE2DFD7.toInt()
    )

    // Mode Gelap: Carbon Slate Matte + Warm Automotive Amber (0% AI-Slop Purple)
    val Dark = GlassPalette(
        isDark = true,
        windowBg = 0xFF0B0E11.toInt(),
        textPrimary = 0xFFF3F5F7.toInt(),
        textSecondary = 0xFF9BA5B1.toInt(),
        textMuted = 0xFF6E7885.toInt(),
        accent = 0xFFE59A28.toInt(),
        accentLight = 0xFFF5B041.toInt(),
        track = 0x2EFFFFFF,
        line = 0x1FFFFFFF,
        stroke = 0xFF242C35.toInt(),
        ripple = 0x2EFFFFFF,
        cardTop = 0xFF13171C.toInt(),
        cardBottom = 0xFF101418.toInt(),
        innerTop = 0xFF1A2027.toInt(),
        innerBottom = 0xFF171C22.toInt(),
        overTop = 0xF5151A20.toInt(),
        overBottom = 0xF011151A.toInt(),
        sidebarTop = 0xFF13171C.toInt(),
        sidebarBottom = 0xFF101418.toInt(),
        selectTop = 0xFF282115.toInt(),
        selectBottom = 0xFF201A11.toInt(),
        selectFg = 0xFFFBBF24.toInt(),
        chip = 0xFF202730.toInt()
    )

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    fun dpf(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density

    fun glassPanel(context: Context, palette: GlassPalette, radiusDp: Float, topColor: Int, bottomColor: Int): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topColor, bottomColor)).apply {
            cornerRadius = dpf(context, radiusDp)
            setStroke(dp(context, 1f), palette.stroke)
        }
    }

    fun cardDrawable(context: Context, palette: GlassPalette): GradientDrawable =
        glassPanel(context, palette, 16f, palette.cardTop, palette.cardBottom)

    fun sidebarDrawable(context: Context, palette: GlassPalette): GradientDrawable =
        glassPanel(context, palette, 18f, palette.sidebarTop, palette.sidebarBottom)

    fun innerDrawable(context: Context, palette: GlassPalette, radiusDp: Float = 12f): GradientDrawable =
        glassPanel(context, palette, radiusDp, palette.innerTop, palette.innerBottom)

    fun overDrawable(context: Context, palette: GlassPalette, radiusDp: Float = 12f): GradientDrawable =
        glassPanel(context, palette, radiusDp, palette.overTop, palette.overBottom)

    fun selectDrawable(context: Context, palette: GlassPalette, radiusDp: Float = 12f): GradientDrawable =
        glassPanel(context, palette, radiusDp, palette.selectTop, palette.selectBottom)

    fun accentButtonDrawable(context: Context, palette: GlassPalette, radiusDp: Float = 12f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(palette.accentLight, palette.accent)
        ).apply {
            cornerRadius = dpf(context, radiusDp)
            setStroke(dp(context, 1f), 0x33FFFFFF)
        }
    }

    fun solidRound(context: Context, color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpf(context, radiusDp)
        }
    }

    fun makePressable(context: Context, view: View, palette: GlassPalette, radiusDp: Float) {
        val mask = solidRound(context, 0xFFFFFFFF.toInt(), radiusDp)
        view.foreground = RippleDrawable(ColorStateList.valueOf(palette.ripple), null, mask)
        view.isClickable = true
        view.isFocusable = true
    }

    fun clipRound(context: Context, view: View, radiusDp: Float) {
        val r = dpf(context, radiusDp)
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, r)
            }
        }
        view.clipToOutline = true
    }
}
