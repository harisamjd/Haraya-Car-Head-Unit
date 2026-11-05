package com.haraya.dock.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Dual Concentric Instrument Cluster:
 * 1. Outer Visual Speedometer Arc (0..200 km/h) with thick block sweep fill, scale ticks, and leading block bar.
 * 2. Inner Visual RPM Tachometer Arc (0..8 x1000 r/min) below/inside the speedo arc with thick block sweep fill.
 * The center space inside the inner RPM arc frames the raised Vehicle Illustration.
 */
class CenterSpeedArcView(context: Context) : View(context) {

    private var targetSpeedKmh: Int = 0
    private var targetRpmValue: Int = 0

    private var animSpeed: Float = 0f
    private var animRpm: Float = 0f
    private var valueAnimator: ValueAnimator? = null
    private var isIntroSweeping: Boolean = false

    private var palette: GlassPalette = GlassTheme.Dark

    private val bgArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val thickBlockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val activeRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
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

    private val needleBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val outerArcRect = RectF()
    private val outerBlockRect = RectF()
    private val innerArcRect = RectF()
    private val innerBlockRect = RectF()

    fun setSpeed(kmh: Int) {
        val clamped = kmh.coerceIn(0, 200)
        if (targetSpeedKmh != clamped) {
            targetSpeedKmh = clamped
            animateToTargets()
        }
    }

    fun setObdTelemetry(speed: Int, rpm: Int) {
        val clampedSpd = speed.coerceIn(0, 200)
        val clampedRpm = rpm.coerceIn(0, 8000)
        if (targetSpeedKmh != clampedSpd || targetRpmValue != clampedRpm) {
            targetSpeedKmh = clampedSpd
            targetRpmValue = clampedRpm
            animateToTargets()
        }
    }

    private fun animateToTargets() {
        if (isIntroSweeping) return
        val startSpd = animSpeed
        val startRpm = animRpm
        val endSpd = targetSpeedKmh.toFloat()
        val endRpm = targetRpmValue.toFloat()

        valueAnimator?.cancel()
        valueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val f = va.animatedValue as Float
                animSpeed = startSpd + (endSpd - startSpd) * f
                animRpm = startRpm + (endRpm - startRpm) * f
                invalidate()
            }
            start()
        }
    }

    /**
     * Plays a dramatic gauge check sweep (0 -> full scale -> live reading) when the cockpit opens
     * so the thick block visualization is showcased immediately on startup.
     */
    fun playGaugeSweepIntro() {
        isIntroSweeping = true
        valueAnimator?.cancel()
        valueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1150L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                if (t <= 0.5f) {
                    val up = t / 0.5f
                    animSpeed = 200f * up
                    animRpm = 8000f * up
                } else {
                    val down = (t - 0.5f) / 0.5f
                    animSpeed = 200f + (targetSpeedKmh - 200f) * down
                    animRpm = 8000f + (targetRpmValue - 8000f) * down
                }
                invalidate()
                if (t >= 1f) {
                    isIntroSweeping = false
                }
            }
            start()
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

        val radius = min(w * 0.43f, h * 0.79f)
        val speedStrokeW = (radius * 0.068f).coerceIn(6.5f, 15f)
        val cx = w / 2f
        val cy = radius + speedStrokeW * 1.85f

        val zone1Color = if (palette.isDark) 0x382DD4BF else 0x4838BDF8
        val zone2Color = if (palette.isDark) 0x38F59E0B else 0x48FBBF24
        val zone3Color = if (palette.isDark) 0x38EF4444 else 0x48F87171

        // ==========================================================
        // 1. OUTER ARC: SPEEDOMETER (0 .. 200 km/h) WITH THICK BLOCKING
        // ==========================================================
        outerArcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        bgArcPaint.strokeWidth = speedStrokeW

        val speedSweep1 = totalSweep * (80f / 200f)
        val speedSweep2 = totalSweep * (40f / 200f)
        val speedSweep3 = totalSweep * (80f / 200f)

        bgArcPaint.color = zone1Color
        canvas.drawArc(outerArcRect, startAngle, speedSweep1, false, bgArcPaint)
        bgArcPaint.color = zone2Color
        canvas.drawArc(outerArcRect, startAngle + speedSweep1, speedSweep2, false, bgArcPaint)
        bgArcPaint.color = zone3Color
        canvas.drawArc(outerArcRect, startAngle + speedSweep1 + speedSweep2, speedSweep3, false, bgArcPaint)

        val speedTickOuter = radius - speedStrokeW * 0.45f
        val speedTickMajorInner = radius - speedStrokeW * 1.45f
        val speedTickMinorInner = radius - speedStrokeW * 1.05f
        val speedTextRadius = radius - speedStrokeW * 2.15f

        val speedFrac = (animSpeed / 200f).coerceIn(0f, 1f)
        val speedColor = when {
            animSpeed >= 120f -> 0xFFEF4444.toInt()
            animSpeed >= 80f -> 0xFFF59E0B.toInt()
            else -> if (palette.isDark) 0xFF2DD4BF.toInt() else 0xFF0284C7.toInt()
        }
        val speedBlockColor = (speedColor and 0x00FFFFFF) or 0x7A000000

        // Thick Block Sweep Band (fills both the outer arc and the tick-mark band)
        val speedBlockOuterR = radius + speedStrokeW * 0.48f
        val speedBlockInnerR = speedTickMajorInner
        val speedBlockW = speedBlockOuterR - speedBlockInnerR
        val speedBlockMidR = (speedBlockOuterR + speedBlockInnerR) / 2f
        outerBlockRect.set(cx - speedBlockMidR, cy - speedBlockMidR, cx + speedBlockMidR, cy + speedBlockMidR)

        val activeSpeedSweep = (totalSweep * speedFrac).coerceAtLeast(if (animSpeed > 0.2f) 2.5f else 1.5f)
        thickBlockPaint.strokeWidth = speedBlockW
        thickBlockPaint.color = speedBlockColor
        canvas.drawArc(outerBlockRect, startAngle, activeSpeedSweep, false, thickBlockPaint)

        // Bright Outer Rim of the Thick Block
        activeRimPaint.strokeWidth = speedStrokeW
        activeRimPaint.color = speedColor
        canvas.drawArc(outerArcRect, startAngle, activeSpeedSweep, false, activeRimPaint)

        // Speed ticks & numbers (0, 20, 40, ..., 200)
        scaleTextPaint.textSize = (radius * 0.060f).coerceIn(9.5f, 17f)
        for (spd in 0..200 step 10) {
            val frac = spd / 200f
            val deg = startAngle + frac * totalSweep
            val rad = Math.toRadians(deg.toDouble())
            val isMajor = spd % 20 == 0
            val isPassed = spd <= animSpeed + 0.5f

            tickPaint.strokeWidth = if (isMajor) (speedStrokeW * 0.22f).coerceAtLeast(1.8f) else (speedStrokeW * 0.12f).coerceAtLeast(1.1f)
            tickPaint.color = when {
                isPassed -> 0xFFFFFFFF.toInt()
                spd >= 140 -> 0xFFEF4444.toInt()
                else -> palette.textSecondary
            }

            val innerR = if (isMajor) speedTickMajorInner else speedTickMinorInner
            canvas.drawLine(
                cx + (innerR * cos(rad)).toFloat(),
                cy + (innerR * sin(rad)).toFloat(),
                cx + (speedTickOuter * cos(rad)).toFloat(),
                cy + (speedTickOuter * sin(rad)).toFloat(),
                tickPaint
            )

            if (isMajor) {
                scaleTextPaint.color = when {
                    isPassed && spd > 0 -> palette.textPrimary
                    spd >= 140 -> 0xFFF87171.toInt()
                    else -> palette.textSecondary
                }
                val tx = cx + (speedTextRadius * cos(rad)).toFloat()
                val ty = cy + (speedTextRadius * sin(rad)).toFloat() + scaleTextPaint.textSize * 0.35f
                canvas.drawText(spd.toString(), tx, ty, scaleTextPaint)
            }
        }

        // Leading Thick Block Bar / Needle on Outer Speedometer Arc
        val speedLeadRad = Math.toRadians((startAngle + activeSpeedSweep).toDouble())
        needleBarPaint.strokeWidth = (speedStrokeW * 0.52f).coerceAtLeast(3.5f)
        needleBarPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawLine(
            cx + ((speedBlockInnerR - speedStrokeW * 0.15f) * cos(speedLeadRad)).toFloat(),
            cy + ((speedBlockInnerR - speedStrokeW * 0.15f) * sin(speedLeadRad)).toFloat(),
            cx + ((speedBlockOuterR + speedStrokeW * 0.1f) * cos(speedLeadRad)).toFloat(),
            cy + ((speedBlockOuterR + speedStrokeW * 0.1f) * sin(speedLeadRad)).toFloat(),
            needleBarPaint
        )

        // ==========================================================
        // 2. INNER ARC: RPM TACHOMETER (0 .. 8 x1000 r/min) WITH THICK BLOCKING
        // ==========================================================
        val rpmRadius = radius * 0.71f
        val rpmStrokeW = (radius * 0.052f).coerceIn(5.5f, 12f)
        innerArcRect.set(cx - rpmRadius, cy - rpmRadius, cx + rpmRadius, cy + rpmRadius)

        bgArcPaint.strokeWidth = rpmStrokeW
        val rpmSweep1 = totalSweep * (4000f / 8000f)
        val rpmSweep2 = totalSweep * (2000f / 8000f)
        val rpmSweep3 = totalSweep * (2000f / 8000f)

        bgArcPaint.color = zone1Color
        canvas.drawArc(innerArcRect, startAngle, rpmSweep1, false, bgArcPaint)
        bgArcPaint.color = zone2Color
        canvas.drawArc(innerArcRect, startAngle + rpmSweep1, rpmSweep2, false, bgArcPaint)
        bgArcPaint.color = zone3Color
        canvas.drawArc(innerArcRect, startAngle + rpmSweep1 + rpmSweep2, rpmSweep3, false, bgArcPaint)

        val rpmTickOuter = rpmRadius - rpmStrokeW * 0.45f
        val rpmTickMajorInner = rpmRadius - rpmStrokeW * 1.48f
        val rpmTickMinorInner = rpmRadius - rpmStrokeW * 1.05f
        val rpmTextRadius = rpmRadius - rpmStrokeW * 2.18f

        val rpmFrac = (animRpm / 8000f).coerceIn(0f, 1f)
        val rpmColor = when {
            animRpm >= 6000f -> 0xFFEF4444.toInt()
            animRpm >= 4000f -> 0xFFF59E0B.toInt()
            else -> palette.accent
        }
        val rpmBlockColor = (rpmColor and 0x00FFFFFF) or 0x7A000000

        // Thick Block Sweep Band for RPM
        val rpmBlockOuterR = rpmRadius + rpmStrokeW * 0.48f
        val rpmBlockInnerR = rpmTickMajorInner
        val rpmBlockW = rpmBlockOuterR - rpmBlockInnerR
        val rpmBlockMidR = (rpmBlockOuterR + rpmBlockInnerR) / 2f
        innerBlockRect.set(cx - rpmBlockMidR, cy - rpmBlockMidR, cx + rpmBlockMidR, cy + rpmBlockMidR)

        val activeRpmSweep = (totalSweep * rpmFrac).coerceAtLeast(if (animRpm > 20f) 2.5f else 1.5f)
        thickBlockPaint.strokeWidth = rpmBlockW
        thickBlockPaint.color = rpmBlockColor
        canvas.drawArc(innerBlockRect, startAngle, activeRpmSweep, false, thickBlockPaint)

        // Bright Outer Rim of RPM Thick Block
        activeRimPaint.strokeWidth = rpmStrokeW
        activeRimPaint.color = rpmColor
        canvas.drawArc(innerArcRect, startAngle, activeRpmSweep, false, activeRimPaint)

        // RPM ticks & numbers (0, 1, 2, 3, 4, 5, 6, 7, 8)
        rpmScaleTextPaint.textSize = (radius * 0.050f).coerceIn(8.5f, 14.5f)
        for (rpmStep in 0..16) {
            val frac = rpmStep / 16f
            val deg = startAngle + frac * totalSweep
            val rad = Math.toRadians(deg.toDouble())
            val isMajor = rpmStep % 2 == 0
            val kVal = rpmStep / 2
            val isPassed = (rpmStep * 500f) <= animRpm + 25f

            tickPaint.strokeWidth = if (isMajor) (rpmStrokeW * 0.22f).coerceAtLeast(1.5f) else (rpmStrokeW * 0.12f).coerceAtLeast(1f)
            tickPaint.color = when {
                isPassed -> 0xFFFFFFFF.toInt()
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
                    isPassed && kVal > 0 -> palette.textPrimary
                    kVal >= 6 -> 0xFFF87171.toInt()
                    kVal >= 4 -> 0xFFFBBF24.toInt()
                    else -> palette.textSecondary
                }
                val tx = cx + (rpmTextRadius * cos(rad)).toFloat()
                val ty = cy + (rpmTextRadius * sin(rad)).toFloat() + rpmScaleTextPaint.textSize * 0.35f
                canvas.drawText(kVal.toString(), tx, ty, rpmScaleTextPaint)
            }
        }

        // Leading Thick Block Bar / Needle on Inner RPM Arc
        val rpmLeadRad = Math.toRadians((startAngle + activeRpmSweep).toDouble())
        needleBarPaint.strokeWidth = (rpmStrokeW * 0.54f).coerceAtLeast(3f)
        needleBarPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawLine(
            cx + ((rpmBlockInnerR - rpmStrokeW * 0.15f) * cos(rpmLeadRad)).toFloat(),
            cy + ((rpmBlockInnerR - rpmStrokeW * 0.15f) * sin(rpmLeadRad)).toFloat(),
            cx + ((rpmBlockOuterR + rpmStrokeW * 0.1f) * cos(rpmLeadRad)).toFloat(),
            cy + ((rpmBlockOuterR + rpmStrokeW * 0.1f) * sin(rpmLeadRad)).toFloat(),
            needleBarPaint
        )
    }
}
