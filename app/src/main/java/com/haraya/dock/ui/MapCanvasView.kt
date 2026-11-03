package com.haraya.dock.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import kotlin.math.max

/**
 * Permukaan kanvas peta perkotaan (jalan arteri, taman, sungai, bundaran, dan penanda posisi kendaraan)
 * dirender sekali ke Bitmap saat ukuran atau tema berubah.
 */
class MapCanvasView(context: Context) : View(context) {

    private var cachedBitmap: Bitmap? = null
    private var palette: GlassPalette = GlassTheme.Dark

    fun applyTheme(newPalette: GlassPalette) {
        if (palette.isDark != newPalette.isDark || cachedBitmap == null) {
            palette = newPalette
            renderMap(width, height)
            invalidate()
        } else {
            palette = newPalette
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        renderMap(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        cachedBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    private fun renderMap(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        cachedBitmap?.recycle()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        paintCartography(c, w.toFloat(), h.toFloat(), palette)
        cachedBitmap = bmp
    }

    private fun paintCartography(c: Canvas, w: Float, h: Float, pal: GlassPalette) {
        val d = pal.isDark
        val base = if (d) 0xFF1A1C2E.toInt() else 0xFFEAE8E3.toInt()
        val park = if (d) 0xFF1F3532.toInt() else 0xFFCDE3C3.toInt()
        val water = if (d) 0xFF223659.toInt() else 0xFFB6D4EE.toInt()
        val minorRoad = if (d) 0xFF2B2E48.toInt() else 0xFFF7F6F2.toInt()
        val majorRoad = if (d) 0xFF393D5E.toInt() else 0xFFFFFFFF.toInt()
        val roadEdge = if (d) 0xFF252840.toInt() else 0xFFD5D4CE.toInt()
        val routeColor = pal.accent

        c.drawColor(base)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Blok taman hijau
        p.style = Paint.Style.FILL
        p.color = park
        c.drawRoundRect(RectF(w * 0.12f, h * 0.15f, w * 0.34f, h * 0.45f), 18f, 18f, p)
        c.drawRoundRect(RectF(w * 0.68f, h * 0.52f, w * 0.92f, h * 0.84f), 22f, 22f, p)

        // Aliran sungai kurva Bézier
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = water
        p.strokeWidth = h * 0.065f
        val river = Path().apply {
            moveTo(w * 0.18f, -h * 0.1f)
            cubicTo(w * 0.35f, h * 0.28f, w * 0.62f, h * 0.22f, w * 1.05f, h * 0.48f)
        }
        c.drawPath(river, p)

        // Grid jalan miring -12 derajat
        c.save()
        c.rotate(-12f, w / 2f, h / 2f)

        p.color = minorRoad
        p.strokeWidth = max(2f, h * 0.018f)
        val step = h * 0.14f
        var x = -w * 0.5f
        while (x < w * 1.5f) {
            c.drawLine(x, -h * 0.5f, x, h * 1.5f, p)
            x += step * 1.35f
        }
        var y = -h * 0.5f
        while (y < h * 1.5f) {
            c.drawLine(-w * 0.5f, y, w * 1.5f, y, p)
            y += step
        }

        // Jalan raya utama
        val highways = arrayOf(
            floatArrayOf(-w * 0.5f, h * 0.54f, w * 1.5f, h * 0.54f),
            floatArrayOf(w * 0.52f, -h * 0.5f, w * 0.52f, h * 1.5f),
            floatArrayOf(-w * 0.5f, h * 0.22f, w * 1.5f, h * 0.22f)
        )
        for (r in highways) {
            p.color = roadEdge
            p.strokeWidth = h * 0.062f
            c.drawLine(r[0], r[1], r[2], r[3], p)
            p.color = majorRoad
            p.strokeWidth = h * 0.046f
            c.drawLine(r[0], r[1], r[2], r[3], p)
        }

        // Garis rute aktif melewati titik kendaraan
        p.color = routeColor
        p.strokeWidth = h * 0.028f
        c.drawLine(w * 0.52f, h * 0.95f, w * 0.52f, h * 0.22f, p)
        c.drawLine(w * 0.52f, h * 0.22f, w * 0.88f, h * 0.22f, p)

        c.restore()

        // Titik posisi kendaraan di tengah agak bawah
        val carX = w * 0.50f
        val carY = h * 0.54f
        p.style = Paint.Style.FILL
        p.color = (pal.accent and 0x00FFFFFF) or 0x38000000
        c.drawCircle(carX, carY, h * 0.11f, p)

        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(carX, carY, h * 0.048f, p)

        p.color = pal.accent
        c.drawCircle(carX, carY, h * 0.034f, p)
    }
}

