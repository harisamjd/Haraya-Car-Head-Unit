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
 * Menggunakan palet Carbon Slate & Warm Amber (tanpa warna ungu AI).
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
                    0xFF0E1216.toInt(),
                    0xFF0A0D10.toInt(),
                    0xFF141A20.toInt(),
                    0xFF0C1014.toInt()
                )
            } else {
                intArrayOf(
                    0xFFF0EAE0.toInt(),
                    0xFFE2D8C8.toInt(),
                    0xFFEDE6DA.toInt(),
                    0xFFDCCFBE.toInt()
                )
            }

            val lightRgb = if (dark) 0x5C6B7A else 0xFFFAF2
            val shadeRgb = if (dark) 0x040608 else 0x8A6A48
            val lightMax = if (dark) 80f else 185f
            val shadeMax = if (dark) 140f else 95f

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
            p.strokeWidth = max(1.2f, h / 520f)
            p.color = 0x55475569
            for (f in FOLDS) {
                path.reset()
                path.moveTo(x0, f[0] * h)
                path.cubicTo(x1, f[1] * h, x2, f[2] * h, x3, f[3] * h)
                c.drawPath(path, p)
            }
        }
    }
}
