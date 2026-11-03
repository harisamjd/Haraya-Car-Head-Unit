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
 * Tachometer Instrumen: Cincin busur 270° (0 sampai 8.000 RPM)
 * dengan zona redline di atas 6.500 RPM, angka RPM tabular di tengah, dan satuan RPM.
 */
class RpmGaugeView(context: Context) : View(context) {

    private var rpm: Int = 850
    private var palette: GlassPalette = GlassTheme.Dark

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val redlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()

    fun setRpm(value: Int) {
        val clamped = value.coerceIn(0, 8000)
        if (rpm != clamped) {
            rpm = clamped
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

        val strokeW = (minDim * 0.082f).coerceAtLeast(8f)
        val radius = minDim / 2f - strokeW * 1.15f
        val cx = w / 2f
        val cy = h / 2f + strokeW * 0.25f

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Track normal (0 - 6500 RPM)
        trackPaint.strokeWidth = strokeW
        trackPaint.color = palette.track
        canvas.drawArc(arcRect, 135f, 270f * (6500f / 8000f), false, trackPaint)

        // Zona Redline (6500 - 8000 RPM)
        redlinePaint.strokeWidth = strokeW
        redlinePaint.color = if (palette.isDark) 0x66FF5252.toInt() else 0x55E53935.toInt()
        val redlineStart = 135f + 270f * (6500f / 8000f)
        val redlineSweep = 270f * (1500f / 8000f)
        canvas.drawArc(arcRect, redlineStart, redlineSweep, false, redlinePaint)

        // Tanda Skala 0 .. 8 (x1000 RPM)
        tickPaint.strokeWidth = (strokeW * 0.22f).coerceAtLeast(2f)
        val tickOuter = radius - strokeW * 0.85f
        val tickInner = radius - strokeW * 1.35f
        for (i in 0..8) {
            val deg = 135.0 + (i / 8.0) * 270.0
            val rad = Math.toRadians(deg)
            tickPaint.color = if (i >= 7) {
                if (palette.isDark) 0xFFFF5252.toInt() else 0xFFE53935.toInt()
            } else {
                palette.textMuted
            }
            canvas.drawLine(
                cx + (tickInner * cos(rad)).toFloat(),
                cy + (tickInner * sin(rad)).toFloat(),
                cx + (tickOuter * cos(rad)).toFloat(),
                cy + (tickOuter * sin(rad)).toFloat(),
                tickPaint
            )
        }

        // Busur RPM Aktif
        val sweep = 270f * (rpm / 8000f).coerceIn(0f, 1f)
        if (sweep > 0.5f) {
            fillPaint.strokeWidth = strokeW
            val isOverRedline = rpm >= 6500
            if (isOverRedline) {
                fillPaint.shader = null
                fillPaint.color = if (palette.isDark) 0xFFFF5252.toInt() else 0xFFE53935.toInt()
            } else {
                fillPaint.shader = SweepGradient(
                    cx, cy,
                    intArrayOf(palette.track, palette.accent, palette.accentLight),
                    floatArrayOf(0f, 0.5f, 1f)
                ).apply {
                    val m = Matrix()
                    m.postRotate(135f, cx, cy)
                    setLocalMatrix(m)
                }
            }
            canvas.drawArc(arcRect, 135f, sweep, false, fillPaint)
        }

        // Angka RPM di Tengah
        numberPaint.color = palette.textPrimary
        numberPaint.textSize = minDim * 0.26f
        val numY = cy + minDim * 0.07f
        canvas.drawText(rpm.toString(), cx, numY, numberPaint)

        // Satuan RPM di Bawah Angka
        unitPaint.color = palette.textSecondary
        unitPaint.textSize = minDim * 0.095f
        val unitY = numY + minDim * 0.14f
        canvas.drawText("RPM (x1000)", cx, unitY, unitPaint)
    }
}

