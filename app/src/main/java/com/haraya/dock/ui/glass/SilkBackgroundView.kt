package com.haraya.dock.ui.glass

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Latar belakang kanvas sutra berlapis (Cubic Bézier folds), dirender sekali ke Bitmap
 * saat ukuran layar atau tema berubah sehingga 0% pemakaian CPU saat diam.
 * Terinspirasi dari arsitektur GoldenDash (MIT License).
 */
class SilkBackgroundView(context: Context) : View(context) {

    private var cachedBitmap: Bitmap? = null
    private var isDarkTheme: Boolean = true

    fun setThemeMode(isDark: Boolean) {
        if (this.isDarkTheme != isDark || cachedBitmap == null) {
            this.isDarkTheme = isDark
            renderBitmap(width, height)
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        renderBitmap(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        cachedBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    private fun renderBitmap(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        cachedBitmap?.recycle()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawSilkFolds(c, w.toFloat(), h.toFloat(), isDarkTheme)
        cachedBitmap = bmp
    }

    companion object {
        private val FOLDS = arrayOf(
            floatArrayOf(0.22f, -0.34f, 0.42f, -0.26f, 0.24f),
            floatArrayOf(0.62f, 0.02f, 0.78f, -0.06f, 0.32f),
            floatArrayOf(0.98f, 0.40f, 0.98f, 0.22f, 0.34f),
            floatArrayOf(1.28f, 0.86f, 1.12f, 0.56f, 0.32f)
        )
        private const val STEPS = 72

        private fun bump(k: Float, center: Float, width: Float): Float {
            val d = (k - center) / width
            return exp((-d * d).toDouble()).toFloat()
        }

        private fun drawSilkFolds(c: Canvas, w: Float, h: Float, dark: Boolean) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            val baseColors = if (dark) {
                intArrayOf(
                    0xFF1D1D31.toInt(),
                    0xFF121220.toInt(),
                    0xFF24243D.toInt(),
                    0xFF181A2E.toInt()
                )
            } else {
                intArrayOf(
                    0xFFF2E9DC.toInt(),
                    0xFFDCC7AC.toInt(),
                    0xFFEFE4D5.toInt(),
                    0xFFD6BE9F.toInt()
                )
            }

            val lightRgb = if (dark) 0x8A8CEB else 0xFFFAF2
            val shadeRgb = if (dark) 0x05050C else 0x8A6A48
            val lightMax = if (dark) 110f else 200f
            val shadeMax = if (dark) 150f else 105f

            p.shader = LinearGradient(
                0f, 0f, w, h,
                baseColors,
                floatArrayOf(0f, 0.38f, 0.66f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w, h, p)
            p.shader = null

            p.isAntiAlias = false
            p.style = Paint.Style.FILL
            val path = Path()
            val x0 = -0.1f * w
            val x1 = 0.32f * w
            val x2 = 0.62f * w
            val x3 = 1.1f * w

            for (f in FOLDS) {
                val thick = f[4] * h
                val step = thick / STEPS
                for (i in 0 until STEPS) {
                    val k = (i + 0.5f) / STEPS
                    val a = i * step
                    val b = a + step
                    path.reset()
                    path.moveTo(x0, f[0] * h + a)
                    path.cubicTo(x1, f[1] * h + a, x2, f[2] * h + a, x3, f[3] * h + a)
                    path.lineTo(x3, f[3] * h + b)
                    path.cubicTo(x2, f[2] * h + b, x1, f[1] * h + b, x0, f[0] * h + b)
                    path.close()

                    val light = (lightMax * bump(k, 0.34f, 0.17f)).roundToInt()
                    val shade = (shadeMax * bump(k, 0.80f, 0.14f)).roundToInt()

                    if (light > 2) {
                        p.color = (light shl 24) or lightRgb
                        c.drawPath(path, p)
                    }
                    if (shade > 2) {
                        p.color = (shade shl 24) or shadeRgb
                        c.drawPath(path, p)
                    }
                }
            }

            if (!dark) return
            p.isAntiAlias = true
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1.5f, h / 480f)
            p.color = 0xB39EA3FF.toInt()
            for (f in FOLDS) {
                path.reset()
                path.moveTo(x0, f[0] * h)
                path.cubicTo(x1, f[1] * h, x2, f[2] * h, x3, f[3] * h)
                c.drawPath(path, p)
            }
        }
    }
}

