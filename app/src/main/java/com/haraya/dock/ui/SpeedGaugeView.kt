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
 * Speedometer instrumen: Cincin busur 270° (0 sampai 140 km/j) dengan tanda skala,
 * angka kecepatan tabular besar di tengah, dan satuan km/j.
 */
class SpeedGaugeView(context: Context) : View(context) {

    private var speedKmh: Int = 0
    private var palette: GlassPalette = GlassTheme.Dark

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()

    fun setSpeed(kmh: Int) {
        val clamped = kmh.coerceIn(0, 199)
        if (speedKmh != clamped) {
            speedKmh = clamped
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
        val minDim = min(w, h)
        if (minDim <= 0f) return

        val strokeW = (minDim * 0.088f).coerceAtLeast(8f)
        val radius = minDim / 2f - strokeW * 1.15f
        val cx = w / 2f
        val cy = h / 2f + strokeW * 0.25f

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        trackPaint.strokeWidth = strokeW
        trackPaint.color = palette.track
        canvas.drawArc(arcRect, 135f, 270f, false, trackPaint)

        // Gambar tanda titik skala tiap 20 km/j
        tickPaint.strokeWidth = (strokeW * 0.22f).coerceAtLeast(2f)
        tickPaint.color = palette.textMuted
        val tickOuter = radius - strokeW * 0.85f
        val tickInner = radius - strokeW * 1.35f
        for (i in 0..7) {
            val deg = 135.0 + (i / 7.0) * 270.0
            val rad = Math.toRadians(deg)
            canvas.drawLine(
                cx + (tickInner * cos(rad)).toFloat(),
                cy + (tickInner * sin(rad)).toFloat(),
                cx + (tickOuter * cos(rad)).toFloat(),
                cy + (tickOuter * sin(rad)).toFloat(),
                tickPaint
            )
        }

        // Busur progres kecepatan aktif
        val sweep = 270f * (speedKmh / 140f).coerceIn(0f, 1f)
        if (sweep > 0.5f) {
            fillPaint.strokeWidth = strokeW
            fillPaint.shader = SweepGradient(
                cx, cy,
                intArrayOf(palette.accent, palette.accentLight, palette.accent),
                null
            )
            canvas.drawArc(arcRect, 135f, sweep, false, fillPaint)
        }

        // Angka kecepatan
        numberPaint.color = palette.textPrimary
        numberPaint.textSize = minDim * 0.36f
        canvas.drawText(speedKmh.toString(), cx, cy + minDim * 0.09f, numberPaint)

        // Label km/h
        unitPaint.color = palette.textSecondary
        unitPaint.textSize = minDim * 0.105f
        canvas.drawText("km/h", cx, cy + minDim * 0.26f, unitPaint)
    }
}

