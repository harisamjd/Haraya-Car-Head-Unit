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

    // Mode Terang: Clean Alabaster Matte + Kartu Putih Tajam
    val Light = GlassPalette(
        isDark = false,
        windowBg = 0xFFF1F4F9.toInt(),
        textPrimary = 0xFF111827.toInt(),
        textSecondary = 0xFF4B5563.toInt(),
        textMuted = 0xFF6B7280.toInt(),
        accent = 0xFF2563EB.toInt(),
        accentLight = 0xFF3B82F6.toInt(),
        track = 0x20000000,
        line = 0x18000000,
        stroke = 0xFFE2E8F0.toInt(),
        ripple = 0x1F000000,
        cardTop = 0xFFFFFFFF.toInt(),
        cardBottom = 0xFFF8FAFC.toInt(),
        innerTop = 0xFFF1F5F9.toInt(),
        innerBottom = 0xFFEFF4F8.toInt(),
        overTop = 0xFAFFFFFF.toInt(),
        overBottom = 0xF5F8FAFC.toInt(),
        sidebarTop = 0xFFFFFFFF.toInt(),
        sidebarBottom = 0xFFF8FAFC.toInt(),
        selectTop = 0xFFDBEAFE.toInt(),
        selectBottom = 0xFFBFDBFE.toInt(),
        selectFg = 0xFF1D4ED8.toInt(),
        chip = 0xFFE2E8F0.toInt()
    )

    // Mode Gelap: Carbon Obsidian Matte + Kartu Malam Presisi
    val Dark = GlassPalette(
        isDark = true,
        windowBg = 0xFF0D0F17.toInt(),
        textPrimary = 0xFFF3F4F8.toInt(),
        textSecondary = 0xFF9CA3AF.toInt(),
        textMuted = 0xFF6B7280.toInt(),
        accent = 0xFF6366F1.toInt(),
        accentLight = 0xFF818CF8.toInt(),
        track = 0x2EFFFFFF,
        line = 0x1FFFFFFF,
        stroke = 0xFF232738.toInt(),
        ripple = 0x2EFFFFFF,
        cardTop = 0xFF161925.toInt(),
        cardBottom = 0xFF131620.toInt(),
        innerTop = 0xFF1E2231.toInt(),
        innerBottom = 0xFF1A1E2C.toInt(),
        overTop = 0xF21A1D2B.toInt(),
        overBottom = 0xEB151824.toInt(),
        sidebarTop = 0xFF161925.toInt(),
        sidebarBottom = 0xFF12151F.toInt(),
        selectTop = 0xFF282C4E.toInt(),
        selectBottom = 0xFF212442.toInt(),
        selectFg = 0xFFC7D2FE.toInt(),
        chip = 0xFF232738.toInt()
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
