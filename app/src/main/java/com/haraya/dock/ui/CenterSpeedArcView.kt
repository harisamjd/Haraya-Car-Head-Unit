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
 * Dual Concentric Instrument Cluster:
 * 1. Outer Visual Speedometer Arc (0..200 km/h) with scale ticks, numbers, color zones, and active pointer dot.
 * 2. Inner Visual RPM Tachometer Arc (0..8 x1000 r/min) below/inside the speedo arc with scale ticks (0..8),
 *    color zones, and active pointer dot.
 * 3. Center Hub: Prominent Gear Position indicator (replacing numeric km/h) + Large Digital Clock.
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

    private val rpmScaleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val gearBadgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gearBadgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val gearTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val clockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
    }

    private val metaLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }

    private val outerArcRect = RectF()
    private val innerArcRect = RectF()
    private val gearPillRect = RectF()

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

        val radius = min(w * 0.45f, h * 0.85f)
        val speedStrokeW = (radius * 0.072f).coerceIn(7f, 16f)
        val cx = w / 2f
        val cy = radius + speedStrokeW * 0.85f

        // ==========================================================
        // 1. OUTER ARC: SPEEDOMETER (0 .. 200 km/h)
        // ==========================================================
        outerArcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        bgArcPaint.strokeWidth = speedStrokeW

        val zone1Color = if (palette.isDark) 0x442DD4BF else 0x5538BDF8
        val zone2Color = if (palette.isDark) 0x44F59E0B else 0x55FBBF24
        val zone3Color = if (palette.isDark) 0x44EF4444 else 0x55F87171

        val speedSweep1 = totalSweep * (80f / 200f)
        val speedSweep2 = totalSweep * (40f / 200f)
        val speedSweep3 = totalSweep * (80f / 200f)

        bgArcPaint.color = zone1Color
        canvas.drawArc(outerArcRect, startAngle, speedSweep1, false, bgArcPaint)
        bgArcPaint.color = zone2Color
        canvas.drawArc(outerArcRect, startAngle + speedSweep1, speedSweep2, false, bgArcPaint)
        bgArcPaint.color = zone3Color
        canvas.drawArc(outerArcRect, startAngle + speedSweep1 + speedSweep2, speedSweep3, false, bgArcPaint)

        // Active Speed Arc + Pointer Dot
        val speedColor = when {
            speedKmh >= 120 -> 0xFFEF4444.toInt()
            speedKmh >= 80 -> 0xFFF59E0B.toInt()
            else -> if (palette.isDark) 0xFF2DD4BF.toInt() else 0xFF0284C7.toInt()
        }
        val speedFrac = (speedKmh / 200f).coerceIn(0f, 1f)
        if (speedKmh > 0) {
            activeArcPaint.strokeWidth = speedStrokeW
            activeArcPaint.color = speedColor
            canvas.drawArc(outerArcRect, startAngle, totalSweep * speedFrac, false, activeArcPaint)
        }

        // Speed ticks & numbers (0, 20, 40, ..., 200)
        val speedTickOuter = radius - speedStrokeW * 0.52f
        val speedTickMajorInner = radius - speedStrokeW * 1.15f
        val speedTickMinorInner = radius - speedStrokeW * 0.85f
        val speedTextRadius = radius - speedStrokeW * 1.85f

        scaleTextPaint.textSize = (radius * 0.062f).coerceIn(9.5f, 18f)
        for (spd in 0..200 step 10) {
            val frac = spd / 200f
            val deg = startAngle + frac * totalSweep
            val rad = Math.toRadians(deg.toDouble())
            val isMajor = spd % 20 == 0

            tickPaint.strokeWidth = if (isMajor) (speedStrokeW * 0.19f).coerceAtLeast(1.8f) else (speedStrokeW * 0.11f).coerceAtLeast(1.1f)
            tickPaint.color = if (spd >= 140) 0xFFEF4444.toInt() else palette.textSecondary

            val innerR = if (isMajor) speedTickMajorInner else speedTickMinorInner
            canvas.drawLine(
                cx + (innerR * cos(rad)).toFloat(),
                cy + (innerR * sin(rad)).toFloat(),
                cx + (speedTickOuter * cos(rad)).toFloat(),
                cy + (speedTickOuter * sin(rad)).toFloat(),
                tickPaint
            )

            if (isMajor) {
                scaleTextPaint.color = if (spd >= 140) 0xFFF87171.toInt() else palette.textSecondary
                val tx = cx + (speedTextRadius * cos(rad)).toFloat()
                val ty = cy + (speedTextRadius * sin(rad)).toFloat() + scaleTextPaint.textSize * 0.35f
                canvas.drawText(spd.toString(), tx, ty, scaleTextPaint)
            }
        }

        // Speed Pointer Dot on Outer Arc
        val speedAngleRad = Math.toRadians((startAngle + speedFrac * totalSweep).toDouble())
        val speedDotX = cx + (radius * cos(speedAngleRad)).toFloat()
        val speedDotY = cy + (radius * sin(speedAngleRad)).toFloat()
        pointerPaint.color = speedColor
        canvas.drawCircle(speedDotX, speedDotY, speedStrokeW * 0.68f, pointerPaint)
        pointerStrokePaint.color = 0xFFFFFFFF.toInt()
        pointerStrokePaint.strokeWidth = (speedStrokeW * 0.18f).coerceAtLeast(1.5f)
        canvas.drawCircle(speedDotX, speedDotY, speedStrokeW * 0.68f, pointerStrokePaint)

        // ==========================================================
        // 2. INNER ARC: RPM TACHOMETER (0 .. 8 x1000 r/min)
        // ==========================================================
        val rpmRadius = radius * 0.72f
        val rpmStrokeW = (radius * 0.054f).coerceIn(5.5f, 12f)
        innerArcRect.set(cx - rpmRadius, cy - rpmRadius, cx + rpmRadius, cy + rpmRadius)

        bgArcPaint.strokeWidth = rpmStrokeW
        // RPM zones: 0..4k (Teal), 4k..6k (Amber), 6k..8k (Redline)
        val rpmSweep1 = totalSweep * (4000f / 8000f)
        val rpmSweep2 = totalSweep * (2000f / 8000f)
        val rpmSweep3 = totalSweep * (2000f / 8000f)

        bgArcPaint.color = zone1Color
        canvas.drawArc(innerArcRect, startAngle, rpmSweep1, false, bgArcPaint)
        bgArcPaint.color = zone2Color
        canvas.drawArc(innerArcRect, startAngle + rpmSweep1, rpmSweep2, false, bgArcPaint)
        bgArcPaint.color = zone3Color
        canvas.drawArc(innerArcRect, startAngle + rpmSweep1 + rpmSweep2, rpmSweep3, false, bgArcPaint)

        // Active RPM Arc + Pointer Dot
        val rpmColor = when {
            rpmValue >= 6000 -> 0xFFEF4444.toInt()
            rpmValue >= 4000 -> 0xFFF59E0B.toInt()
            else -> palette.accent
        }
        val rpmFrac = (rpmValue / 8000f).coerceIn(0f, 1f)
        if (rpmValue > 0) {
            activeArcPaint.strokeWidth = rpmStrokeW
            activeArcPaint.color = rpmColor
            canvas.drawArc(innerArcRect, startAngle, totalSweep * rpmFrac, false, activeArcPaint)
        }

        // RPM ticks & numbers (0, 1, 2, 3, 4, 5, 6, 7, 8)
        val rpmTickOuter = rpmRadius - rpmStrokeW * 0.52f
        val rpmTickMajorInner = rpmRadius - rpmStrokeW * 1.18f
        val rpmTickMinorInner = rpmRadius - rpmStrokeW * 0.85f
        val rpmTextRadius = rpmRadius - rpmStrokeW * 1.88f

        rpmScaleTextPaint.textSize = (radius * 0.052f).coerceIn(8.5f, 15f)
        for (rpmStep in 0..16) { // each step = 500 RPM
            val frac = rpmStep / 16f
            val deg = startAngle + frac * totalSweep
            val rad = Math.toRadians(deg.toDouble())
            val isMajor = rpmStep % 2 == 0
            val kVal = rpmStep / 2

            tickPaint.strokeWidth = if (isMajor) (rpmStrokeW * 0.20f).coerceAtLeast(1.5f) else (rpmStrokeW * 0.11f).coerceAtLeast(1f)
            tickPaint.color = when {
                kVal >= 6 -> 0xFFEF4444.toInt()
                kVal >= 4 -> 0xFFF59E0B.toInt()
                else -> palette.textSecondary
            }

            val innerR = if (isMajor) rpmTickMajorInner else rpmTickMinorInner
            canvas.drawLine(
                cx + (innerR * cos(rad)).toFloat(),
                cy + (innerR * sin(rad)).toFloat(),
                cx + (rpmTickOuter * cos(rad)).toFloat(),
                cy + (rpmTickOuter * sin(rad)).toFloat(),
                tickPaint
            )

            if (isMajor) {
                rpmScaleTextPaint.color = when {
                    kVal >= 6 -> 0xFFF87171.toInt()
                    kVal >= 4 -> 0xFFFBBF24.toInt()
                    else -> palette.textSecondary
                }
                val tx = cx + (rpmTextRadius * cos(rad)).toFloat()
                val ty = cy + (rpmTextRadius * sin(rad)).toFloat() + rpmScaleTextPaint.textSize * 0.35f
                canvas.drawText(kVal.toString(), tx, ty, rpmScaleTextPaint)
            }
        }

        // RPM Pointer Dot on Inner Arc
        val rpmAngleRad = Math.toRadians((startAngle + rpmFrac * totalSweep).toDouble())
        val rpmDotX = cx + (rpmRadius * cos(rpmAngleRad)).toFloat()
        val rpmDotY = cy + (rpmRadius * sin(rpmAngleRad)).toFloat()
        pointerPaint.color = rpmColor
        canvas.drawCircle(rpmDotX, rpmDotY, rpmStrokeW * 0.72f, pointerPaint)
        pointerStrokePaint.color = 0xFFFFFFFF.toInt()
        pointerStrokePaint.strokeWidth = (rpmStrokeW * 0.20f).coerceAtLeast(1.2f)
        canvas.drawCircle(rpmDotX, rpmDotY, rpmStrokeW * 0.72f, pointerStrokePaint)

        // ==========================================================
        // 3. CENTER HUB: GEAR POSITION (REPLACING KM/H) + LARGE CLOCK
        // ==========================================================
        // Gear Badge + Readout where speed/kmh used to be
        val gearBadgeW = (radius * 0.34f).coerceIn(48f, 92f)
        val gearBadgeH = (radius * 0.17f).coerceIn(24f, 44f)
        val gearCenterY = cy - rpmRadius * 0.43f
        gearPillRect.set(
            cx - gearBadgeW / 2f,
            gearCenterY - gearBadgeH / 2f,
            cx + gearBadgeW / 2f,
            gearCenterY + gearBadgeH / 2f
        )

        gearBadgeFillPaint.color = palette.selectBottom
        canvas.drawRoundRect(gearPillRect, gearBadgeH * 0.38f, gearBadgeH * 0.38f, gearBadgeFillPaint)
        gearBadgeStrokePaint.strokeWidth = GlassTheme.dpf(context, 1.2f)
        gearBadgeStrokePaint.color = palette.accent
        canvas.drawRoundRect(gearPillRect, gearBadgeH * 0.38f, gearBadgeH * 0.38f, gearBadgeStrokePaint)

        gearTextPaint.color = palette.selectFg
        gearTextPaint.textSize = (gearBadgeH * 0.64f).coerceIn(14f, 28f)
        val gearBaselineY = gearPillRect.centerY() + gearTextPaint.textSize * 0.35f
        canvas.drawText(gearText, cx, gearBaselineY, gearTextPaint)

        // Subtle "GEAR • KM/H & x1000 RPM" legend right below Gear badge
        metaLabelPaint.color = palette.textMuted
        metaLabelPaint.textSize = (radius * 0.045f).coerceIn(7.5f, 12f)
        val shortSrc = if (sourceLabel.contains("OBD")) "OBD-II" else "GPS"
        val metaY = gearPillRect.bottom + metaLabelPaint.textSize * 1.25f
        canvas.drawText("GEAR • $shortSrc", cx, metaY, metaLabelPaint)

        // Large Digital Clock below Gear Position
        clockTextPaint.color = palette.textPrimary
        clockTextPaint.textSize = (radius * 0.21f).coerceIn(22f, 54f)
        val clockY = metaY + clockTextPaint.textSize * 1.05f
        canvas.drawText(clockText, cx, clockY, clockTextPaint)
    }
}
