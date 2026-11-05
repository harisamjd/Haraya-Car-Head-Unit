package com.haraya.dock.ui

import android.content.Context
import android.graphics.*
import android.util.LruCache
import android.view.View
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.*

/**
 * Live OpenStreetMap Slippy Tile Renderer (tile.openstreetmap.org, z=15)
 * tanpa butuh API Key, lengkap dengan filter Dark-Slate otomotif (0% ungu)
 * dan fallback vektor offline.
 */
class MapCanvasView(context: Context) : View(context) {

    companion object {
        private const val ZOOM = 15
        private val tileCache = object : LruCache<String, Bitmap>(64) {}
        private val inflightKeys = mutableSetOf<String>()
        private val executor = Executors.newFixedThreadPool(3)

        // Filter matriks warna netral Carbon-Slate (tanpa bias ungu) untuk peta Dark Mode
        private val DARK_MAP_FILTER = ColorMatrixColorFilter(
            floatArrayOf(
                -0.55f, -0.22f, -0.05f, 0f, 206f,
                -0.18f, -0.58f, -0.05f, 0f, 212f,
                -0.12f, -0.20f, -0.48f, 0f, 216f,
                 0.00f,  0.00f,  0.00f, 1f,   0f
            )
        )
    }

    private var cachedBitmap: Bitmap? = null
    private var palette: GlassPalette = GlassTheme.Dark
    private var currentLat: Double = -6.4931
    private var currentLon: Double = 106.8056
    private var streetLabel: String = "Mencari Jalan..."

    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun updateLocation(lat: Double, lon: Double, label: String? = null) {
        val moved = abs(lat - currentLat) > 0.0001 || abs(lon - currentLon) > 0.0001
        val labelChanged = !label.isNullOrBlank() && label != streetLabel
        currentLat = lat
        currentLon = lon
        if (!label.isNullOrBlank()) {
            streetLabel = label
        }
        if (moved || labelChanged) {
            invalidate()
        }
    }

    fun applyTheme(newPalette: GlassPalette) {
        val themeChanged = palette.isDark != newPalette.isDark
        palette = newPalette
        tilePaint.colorFilter = if (palette.isDark) DARK_MAP_FILTER else null
        if (themeChanged || cachedBitmap == null) {
            renderFallbackMap(width, height)
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        tilePaint.colorFilter = if (palette.isDark) DARK_MAP_FILTER else null
        renderFallbackMap(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Latar dasar peta
        canvas.drawColor(if (palette.isDark) 0xFF12161B.toInt() else 0xFFEAE8E3.toInt())

        // Gambar tile OpenStreetMap asli
        val hasRealTiles = drawLiveOsmTiles(canvas, w, h)
        if (!hasRealTiles) {
            cachedBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }

        // Gambar titik posisi kendaraan di tengah peta
        val carX = w * 0.50f
        val carY = h * 0.50f
        overlayPaint.style = Paint.Style.FILL
        overlayPaint.color = (palette.accent and 0x00FFFFFF) or 0x44000000
        canvas.drawCircle(carX, carY, GlassTheme.dp(context, 15f).toFloat(), overlayPaint)

        overlayPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(carX, carY, GlassTheme.dp(context, 6.8f).toFloat(), overlayPaint)

        overlayPaint.color = palette.accent
        canvas.drawCircle(carX, carY, GlassTheme.dp(context, 4.6f).toFloat(), overlayPaint)
    }

    private fun drawLiveOsmTiles(canvas: Canvas, w: Float, h: Float): Boolean {
        val n = (1 shl ZOOM).toDouble()
        val exactX = (currentLon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(currentLat.coerceIn(-85.0, 85.0))
        val exactY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n

        val centerTileX = floor(exactX).toInt()
        val centerTileY = floor(exactY).toInt()
        val offsetPxX = ((exactX - centerTileX) * 256.0).toFloat()
        val offsetPxY = ((exactY - centerTileY) * 256.0).toFloat()

        val centerX = w * 0.50f
        val centerY = h * 0.50f

        var drawnCount = 0
        for (dx in -1..1) {
            for (dy in -1..1) {
                val tx = centerTileX + dx
                val ty = centerTileY + dy
                if (tx < 0 || ty < 0 || tx >= n.toInt() || ty >= n.toInt()) continue

                val key = "osm_v3/$ZOOM/$tx/$ty"
                val bmp = tileCache.get(key)
                val left = centerX - offsetPxX + dx * 256f
                val top = centerY - offsetPxY + dy * 256f

                if (bmp != null && !bmp.isRecycled) {
                    canvas.drawBitmap(bmp, left, top, tilePaint)
                    drawnCount++
                } else {
                    fetchOsmTileAsync(ZOOM, tx, ty, key)
                }
            }
        }
        return drawnCount > 0
    }

    private fun fetchOsmTileAsync(z: Int, x: Int, y: Int, cacheKey: String) {
        synchronized(inflightKeys) {
            if (inflightKeys.contains(cacheKey)) return
            inflightKeys.add(cacheKey)
        }
        executor.execute {
            try {
                val urlStr = "https://tile.openstreetmap.org/$z/$x/$y.png"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "HarayaDock/1.2 (https://github.com/harisamjd/Haraya-Car-Head-Unit)")
                }
                if (conn.responseCode == 200) {
                    val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                    if (bmp != null) {
                        tileCache.put(cacheKey, bmp)
                        postInvalidate()
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
            } finally {
                synchronized(inflightKeys) {
                    inflightKeys.remove(cacheKey)
                }
            }
        }
    }

    private fun renderFallbackMap(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        cachedBitmap?.recycle()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        paintCartography(c, w.toFloat(), h.toFloat(), palette)
        cachedBitmap = bmp
    }

    private fun paintCartography(c: Canvas, w: Float, h: Float, pal: GlassPalette) {
        val d = pal.isDark
        val base = if (d) 0xFF12161B.toInt() else 0xFFEAE8E3.toInt()
        val park = if (d) 0xFF182622.toInt() else 0xFFCDE3C3.toInt()
        val water = if (d) 0xFF172736.toInt() else 0xFFB6D4EE.toInt()
        val minorRoad = if (d) 0xFF202730.toInt() else 0xFFF7F6F2.toInt()
        val majorRoad = if (d) 0xFF2B3440.toInt() else 0xFFFFFFFF.toInt()
        val roadEdge = if (d) 0xFF1A2027.toInt() else 0xFFD5D4CE.toInt()
        val routeColor = pal.accent

        c.drawColor(base)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.style = Paint.Style.FILL
        p.color = park
        c.drawRoundRect(RectF(w * 0.12f, h * 0.15f, w * 0.34f, h * 0.45f), 18f, 18f, p)
        c.drawRoundRect(RectF(w * 0.68f, h * 0.52f, w * 0.92f, h * 0.84f), 22f, 22f, p)

        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = water
        p.strokeWidth = h * 0.065f
        val river = Path().apply {
            moveTo(w * 0.18f, -h * 0.1f)
            cubicTo(w * 0.35f, h * 0.28f, w * 0.62f, h * 0.22f, w * 1.05f, h * 0.48f)
        }
        c.drawPath(river, p)

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

        p.color = routeColor
        p.strokeWidth = h * 0.028f
        c.drawLine(w * 0.52f, h * 0.95f, w * 0.52f, h * 0.22f, p)
        c.drawLine(w * 0.52f, h * 0.22f, w * 0.88f, h * 0.22f, p)

        c.restore()
    }
}
