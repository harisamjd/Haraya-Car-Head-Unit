package com.haraya.dock.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Busur Speedometer Atas (0..200 km/jam) bergaya kokpit AstroCar/Instrument Cluster
 * yang membingkai bagian atas ilustrasi mobil, lengkap dengan angka kecepatan,
 * satuan km/jam, jam digital, dan label GPS SPEED di paruh atas busur.
 */
class CenterSpeedArcView(context: Context) : View(context) {

    private var speedKmh: Int = 0
    private var clockText: String = "09:29"
    private var palette: GlassPalette = GlassTheme.Dark

    private val bgArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val activeArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val scaleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val speedNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val clockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }

    private val arcRect = RectF()

    fun setSpeed(kmh: Int) {
        val clamped = kmh.coerceIn(0, 200)
        if (speedKmh != clamped) {
            speedKmh = clamped
            invalidate()
        }
    }

    fun setClockText(time: String) {
        if (clockText != time) {
            clockText = time
            invalidate()
        }
    }

    fun applyTheme(newPalette: GlassPalette) {
        palette = newPalette
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Busur 200 derajat dari 170° s/d 370° (membentang di bagian atas)
        val startAngle = 170f
        val totalSweep = 200f

        val radius = min(w * 0.44f, h * 0.84f)
        val strokeW = (radius * 0.09f).coerceIn(8f, 20f)
        val cx = w / 2f
        val cy = radius + strokeW * 0.85f

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        bgArcPaint.strokeWidth = strokeW

        // 3 Zona warna lembut sesuai referensi (Hijau/Biru 0-80, Kuning/Amber 80-120, Merah 120-200)
        val zone1Color = if (palette.isDark) 0x442DD4BF else 0x5538BDF8
        val zone2Color = if (palette.isDark) 0x44F59E0B else 0x55FBBF24
        val zone3Color = if (palette.isDark) 0x44EF4444 else 0x55F87171

        val sweep1 = totalSweep * (80f / 200f)
        val sweep2 = totalSweep * (40f / 200f)
        val sweep3 = totalSweep * (80f / 200f)

        bgArcPaint.color = zone1Color
        canvas.drawArc(arcRect, startAngle, sweep1, false, bgArcPaint)

        bgArcPaint.color = zone2Color
        canvas.drawArc(arcRect, startAngle + sweep1, sweep2, false, bgArcPaint)

        bgArcPaint.color = zone3Color
        canvas.drawArc(arcRect, startAngle + sweep1 + sweep2, sweep3, false, bgArcPaint)

        // Busur aktif jika kecepatan > 0
        if (speedKmh > 0) {
            activeArcPaint.strokeWidth = strokeW
            activeArcPaint.color = when {
                speedKmh >= 120 -> 0xFFEF4444.toInt()
                speedKmh >= 80 -> 0xFFF59E0B.toInt()
                else -> if (palette.isDark) 0xFF2DD4BF.toInt() else 0xFF0284C7.toInt()
            }
            val activeSweep = totalSweep * (speedKmh / 200f)
            canvas.drawArc(arcRect, startAngle, activeSweep, false, activeArcPaint)
        }

        // Tanda skala & angka (0, 20, 40, ..., 200)
        val tickOuter = radius - strokeW * 0.55f
        val tickMajorInner = radius - strokeW * 1.22f
        val tickMinorInner = radius - strokeW * 0.92f
        val textRadius = radius - strokeW * 1.98f

        scaleTextPaint.textSize = (radius * 0.068f).coerceIn(10f, 20f)
        scaleTextPaint.color = palette.textSecondary

        for (spd in 0..200 step 10) {
            val frac = spd / 200f
            val deg = startAngle + frac * totalSweep
            val rad = Math.toRadians(deg.toDouble())
            val isMajor = spd % 20 == 0

            tickPaint.strokeWidth = if (isMajor) (strokeW * 0.18f).coerceAtLeast(2f) else (strokeW * 0.1f).coerceAtLeast(1.2f)
            tickPaint.color = if (spd >= 140) {
                0xFFEF4444.toInt()
            } else {
                palette.textSecondary
            }

            val innerR = if (isMajor) tickMajorInner else tickMinorInner
            canvas.drawLine(
                cx + (innerR * cos(rad)).toFloat(),
                cy + (innerR * sin(rad)).toFloat(),
                cx + (tickOuter * cos(rad)).toFloat(),
                cy + (tickOuter * sin(rad)).toFloat(),
                tickPaint
            )

            if (isMajor) {
                val tx = cx + (textRadius * cos(rad)).toFloat()
                val ty = cy + (textRadius * sin(rad)).toFloat() + scaleTextPaint.textSize * 0.35f
                canvas.drawText(spd.toString(), tx, ty, scaleTextPaint)
            }
        }

        // Teks Kecepatan Besar di Paruh Atas Busur (tidak tertutup gambar mobil di bawah)
        speedNumPaint.color = palette.textPrimary
        speedNumPaint.textSize = (radius * 0.29f).coerceIn(28f, 76f)
        val speedY = cy - radius * 0.45f
        canvas.drawText(speedKmh.toString(), cx, speedY, speedNumPaint)

        // Satuan km/jam
        unitTextPaint.color = palette.textSecondary
        unitTextPaint.textSize = (radius * 0.082f).coerceIn(10f, 22f)
        val unitY = speedY + unitTextPaint.textSize * 1.18f
        canvas.drawText("km/jam", cx, unitY, unitTextPaint)

        // Jam Digital (09:29 / 20:09)
        clockTextPaint.color = palette.textPrimary
        clockTextPaint.textSize = (radius * 0.145f).coerceIn(15f, 38f)
        val clockY = unitY + clockTextPaint.textSize * 1.18f
        canvas.drawText(clockText, cx, clockY, clockTextPaint)

        // Label GPS SPEED
        subLabelPaint.color = palette.textMuted
        subLabelPaint.textSize = (radius * 0.056f).coerceIn(8.5f, 15f)
        val subY = clockY + subLabelPaint.textSize * 1.35f
        canvas.drawText("GPS SPEED", cx, subY, subLabelPaint)
    }
}
