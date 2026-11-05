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
 * Center Instrument Speedometer Arc (0..200 km/h) framing the top of the vehicle illustration,
 * with digital speed readout, km/h unit, digital clock, and a compact Mini RPM Tachometer
 * + Gear Indicator (P / N / D1..D6) directly below the main speedometer.
 */
class CenterSpeedArcView(context: Context) : View(context) {

    private var speedKmh: Int = 0
    private var rpmValue: Int = 0
    private var gearText: String = "P"
    private var sourceLabel: String = "GPS SPEED"
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
        letterSpacing = 0.06f
    }

    private val miniFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val miniStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val gearTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()
    private val pillRect = RectF()
    private val barRect = RectF()

    fun setSpeed(kmh: Int) {
        val clamped = kmh.coerceIn(0, 200)
        if (speedKmh != clamped) {
            speedKmh = clamped
            invalidate()
        }
    }

    fun setObdTelemetry(speed: Int, rpm: Int, gear: String, src: String) {
        val clampedSpd = speed.coerceIn(0, 200)
        val clampedRpm = rpm.coerceIn(0, 8000)
        if (speedKmh != clampedSpd || rpmValue != clampedRpm || gearText != gear || sourceLabel != src) {
            speedKmh = clampedSpd
            rpmValue = clampedRpm
            gearText = gear
            sourceLabel = src
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

        // 200-degree upper arc (170° to 370°)
        val startAngle = 170f
        val totalSweep = 200f

        val radius = min(w * 0.44f, h * 0.84f)
        val strokeW = (radius * 0.09f).coerceIn(8f, 20f)
        val cx = w / 2f
        val cy = radius + strokeW * 0.85f

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        bgArcPaint.strokeWidth = strokeW

        // 3 Speed zones (Teal 0-80, Amber 80-120, Red 120-200)
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

        // Active speed arc
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

        // Tick marks & numbers (0, 20, 40, ..., 200)
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

        // 1. Large Speed Readout in upper half of arc
        speedNumPaint.color = palette.textPrimary
        speedNumPaint.textSize = (radius * 0.27f).coerceIn(26f, 70f)
        val speedY = cy - radius * 0.49f
        canvas.drawText(speedKmh.toString(), cx, speedY, speedNumPaint)

        // 2. Unit "km/h"
        unitTextPaint.color = palette.textSecondary
        unitTextPaint.textSize = (radius * 0.076f).coerceIn(9.5f, 20f)
        val unitY = speedY + unitTextPaint.textSize * 1.12f
        canvas.drawText("km/h", cx, unitY, unitTextPaint)

        // 3. Digital Clock (09:29 / 20:09)
        clockTextPaint.color = palette.textPrimary
        clockTextPaint.textSize = (radius * 0.132f).coerceIn(14f, 34f)
        val clockY = unitY + clockTextPaint.textSize * 1.14f
        canvas.drawText(clockText, cx, clockY, clockTextPaint)

        // 4. Compact Mini RPM Gauge + Gear Indicator Capsule right below the clock
        val capsuleW = (radius * 1.02f).coerceIn(135f, 230f)
        val capsuleH = (radius * 0.165f).coerceIn(20f, 34f)
        val capsuleTop = clockY + capsuleH * 0.28f
        pillRect.set(cx - capsuleW / 2f, capsuleTop, cx + capsuleW / 2f, capsuleTop + capsuleH)

        // Capsule background & border
        miniFillPaint.color = palette.innerBottom
        canvas.drawRoundRect(pillRect, capsuleH / 2f, capsuleH / 2f, miniFillPaint)
        miniStrokePaint.strokeWidth = GlassTheme.dpf(context, 1f)
        miniStrokePaint.color = palette.stroke
        canvas.drawRoundRect(pillRect, capsuleH / 2f, capsuleH / 2f, miniStrokePaint)

        // Left Badge inside Capsule: Gear Indicator (P, N, D1..D6)
        val gearBadgeW = capsuleH * 1.45f
        val pad = capsuleH * 0.14f
        barRect.set(
            pillRect.left + pad,
            pillRect.top + pad,
            pillRect.left + pad + gearBadgeW,
            pillRect.bottom - pad
        )
        miniFillPaint.color = palette.selectBottom
        canvas.drawRoundRect(barRect, (capsuleH - pad * 2f) / 2f, (capsuleH - pad * 2f) / 2f, miniFillPaint)

        gearTextPaint.textSize = (capsuleH * 0.46f).coerceIn(9f, 15f)
        gearTextPaint.color = palette.selectFg
        val gearTextY = barRect.centerY() + gearTextPaint.textSize * 0.35f
        canvas.drawText(gearText, barRect.centerX(), gearTextY, gearTextPaint)

        // Middle-Right inside Capsule: Mini Segmented RPM Bar (0..8k RPM) + RPM Readout
        val rpmAreaLeft = barRect.right + capsuleH * 0.32f
        val rpmAreaRight = pillRect.right - capsuleH * 0.42f
        val rpmAreaW = (rpmAreaRight - rpmAreaLeft).coerceAtLeast(40f)

        // Top row of RPM area: numeric RPM + source tag
        subLabelPaint.textSize = (capsuleH * 0.34f).coerceIn(7.5f, 11.5f)
        subLabelPaint.color = palette.textPrimary
        subLabelPaint.textAlign = Paint.Align.LEFT
        val rpmStr = if (rpmValue > 0) "$rpmValue RPM" else "0 RPM"
        canvas.drawText(rpmStr, rpmAreaLeft, pillRect.top + capsuleH * 0.44f, subLabelPaint)

        subLabelPaint.color = palette.textMuted
        subLabelPaint.textAlign = Paint.Align.RIGHT
        val shortSrc = if (sourceLabel.contains("OBD")) "OBD" else "GPS"
        canvas.drawText(shortSrc, rpmAreaRight, pillRect.top + capsuleH * 0.44f, subLabelPaint)
        subLabelPaint.textAlign = Paint.Align.CENTER

        // Bottom row of RPM area: 12-segment tachometer bar
        val segments = 12
        val segGap = GlassTheme.dpf(context, 2f)
        val segW = (rpmAreaW - segGap * (segments - 1)) / segments
        val segTop = pillRect.top + capsuleH * 0.58f
        val segBottom = pillRect.bottom - capsuleH * 0.18f
        val activeSegments = ((rpmValue / 7000f) * segments).toInt().coerceIn(0, segments)

        for (i in 0 until segments) {
            val sLeft = rpmAreaLeft + i * (segW + segGap)
            barRect.set(sLeft, segTop, sLeft + segW, segBottom)
            val isActive = i < activeSegments || (rpmValue > 0 && i == 0)
            miniFillPaint.color = if (isActive) {
                when {
                    i >= 9 -> 0xFFEF4444.toInt() // Redline zone (6k+ RPM)
                    i >= 6 -> 0xFFF59E0B.toInt() // Powerband amber
                    else -> palette.accent       // Normal cruise
                }
            } else {
                if (palette.isDark) 0x3394A3B8 else 0x3364748B
            }
            val r = (segBottom - segTop) / 2f
            canvas.drawRoundRect(barRect, r, r, miniFillPaint)
        }
    }
}
