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

    // Mode Terang: Kain Sutra Krem Keemasan + Kaca Putih Hangat
    val Light = GlassPalette(
        isDark = false,
        windowBg = 0xFFE9DCCB.toInt(),
        textPrimary = 0xFF16161A.toInt(),
        textSecondary = 0xFF5E5850.toInt(),
        textMuted = 0xFF8C8479.toInt(),
        accent = 0xFF1A6DEB.toInt(),
        accentLight = 0xFF4C93F7.toInt(),
        track = 0x24000000,
        line = 0x1F000000,
        stroke = 0xB8FFFFFF.toInt(),
        ripple = 0x26000000,
        cardTop = 0x99FFFFFF.toInt(),
        cardBottom = 0x6BFFFFFF,
        innerTop = 0xB8FFFFFF.toInt(),
        innerBottom = 0x94FFFFFF.toInt(),
        overTop = 0xF5FFFFFF.toInt(),
        overBottom = 0xEBFFFFFF.toInt(),
        sidebarTop = 0x99FFFFFF.toInt(),
        sidebarBottom = 0x73FFFFFF,
        selectTop = 0xF2EEF5FE.toInt(),
        selectBottom = 0xE6DCE9FC.toInt(),
        selectFg = 0xFF1A6DEB.toInt(),
        chip = 0x7AFFFFFF
    )

    // Mode Gelap: Lipatan Sutra Biru-Obsidian + Kaca Malam Periwinkle
    val Dark = GlassPalette(
        isDark = true,
        windowBg = 0xFF12131F.toInt(),
        textPrimary = 0xFFF1F0F8.toInt(),
        textSecondary = 0xFFB3B2CA.toInt(),
        textMuted = 0xFF7F7E99.toInt(),
        accent = 0xFF7076F0.toInt(),
        accentLight = 0xFF9EA3FF.toInt(),
        track = 0x33FFFFFF,
        line = 0x24FFFFFF,
        stroke = 0x33FFFFFF,
        ripple = 0x33FFFFFF,
        cardTop = 0xA623243A.toInt(),
        cardBottom = 0x9115162A.toInt(),
        innerTop = 0x24FFFFFF,
        innerBottom = 0x12FFFFFF,
        overTop = 0xEB23253C.toInt(),
        overBottom = 0xE01A1C30.toInt(),
        sidebarTop = 0xB8141524.toInt(),
        sidebarBottom = 0x9410111E.toInt(),
        selectTop = 0x597076F0,
        selectBottom = 0x387076F0,
        selectFg = 0xFFD8DAFF.toInt(),
        chip = 0x22FFFFFF
    )

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    fun dpf(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density

    fun glassPanel(context: Context, palette: GlassPalette, radiusDp: Float, topColor: Int, bottomColor: Int): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topColor, bottomColor)).apply {
            cornerRadius = dpf(context, radiusDp)
            setStroke(dp(context, 1.4f), palette.stroke)
        }
    }

    fun cardDrawable(context: Context, palette: GlassPalette): GradientDrawable =
        glassPanel(context, palette, 26f, palette.cardTop, palette.cardBottom)

    fun sidebarDrawable(context: Context, palette: GlassPalette): GradientDrawable =
        glassPanel(context, palette, 24f, palette.sidebarTop, palette.sidebarBottom)

    fun innerDrawable(context: Context, palette: GlassPalette, radiusDp: Float = 18f): GradientDrawable =
        glassPanel(context, palette, radiusDp, palette.innerTop, palette.innerBottom)

    fun overDrawable(context: Context, palette: GlassPalette, radiusDp: Float = 16f): GradientDrawable =
        glassPanel(context, palette, radiusDp, palette.overTop, palette.overBottom)

    fun selectDrawable(context: Context, palette: GlassPalette, radiusDp: Float = 16f): GradientDrawable =
        glassPanel(context, palette, radiusDp, palette.selectTop, palette.selectBottom)

    fun accentButtonDrawable(context: Context, palette: GlassPalette, radiusDp: Float = 14f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(palette.accentLight, palette.accent)
        ).apply {
            cornerRadius = dpf(context, radiusDp)
            setStroke(dp(context, 1f), 0x4DFFFFFF)
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
