package com.haraya.dock.ui

import android.content.Context
import android.graphics.*
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.*

/**
 * Live OpenStreetMap Slippy Tile Renderer (tile.openstreetmap.org)
 * Mendukung mode mini-card maupun mode Embedded Interactive Map (geser peta dengan jari & zoom 12..18).
 */
class MapCanvasView(
    context: Context,
    private val interactivePanZoom: Boolean = false
) : View(context) {

    companion object {
        private val tileCache = object : LruCache<String, Bitmap>(96) {}
        private val inflightKeys = mutableSetOf<String>()
        private val executor = Executors.newFixedThreadPool(4)

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

    private var zoomLevel: Int = 15
    private var cachedBitmap: Bitmap? = null
    private var palette: GlassPalette = GlassTheme.Dark
    private var currentLat: Double = -6.4931
    private var currentLon: Double = 106.8056
    private var streetLabel: String = "Locating Street..."

    // Offset geser jari pada mode interaktif (dalam piksel tile)
    private var panOffsetPxX: Float = 0f
    private var panOffsetPxY: Float = 0f
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var isDragging: Boolean = false

    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun zoomIn() {
        if (zoomLevel < 18) {
            zoomLevel++
            panOffsetPxX *= 2f
            panOffsetPxY *= 2f
            invalidate()
        }
    }

    fun zoomOut() {
        if (zoomLevel > 11) {
            zoomLevel--
            panOffsetPxX *= 0.5f
            panOffsetPxY *= 0.5f
            invalidate()
        }
    }

    fun recenterOnCar() {
        panOffsetPxX = 0f
        panOffsetPxY = 0f
        invalidate()
    }

    fun getZoomLevel(): Int = zoomLevel

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactivePanZoom) {
            return super.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                if (abs(dx) > 3f || abs(dy) > 3f) {
                    isDragging = true
                }
                if (isDragging) {
                    panOffsetPxX += dx
                    panOffsetPxY += dy
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!isDragging) {
                    performClick()
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
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

        // Vehicle Car Location Marker (moves with pan offset)
        val carX = w * 0.50f + panOffsetPxX
        val carY = h * 0.50f + panOffsetPxY
        drawCarLocationMarker(canvas, carX, carY)
    }

    private fun drawCarLocationMarker(canvas: Canvas, cx: Float, cy: Float) {
        val scale = if (interactivePanZoom) 1.25f else 1.0f
        val u = GlassTheme.dpf(context, 1f) * scale

        canvas.save()
        canvas.translate(cx, cy)
        // Slight navigation angle (-15 deg) for natural road orientation
        canvas.rotate(-15f)

        // 1. Soft radar pulse halo
        overlayPaint.style = Paint.Style.FILL
        overlayPaint.color = (palette.accent and 0x00FFFFFF) or 0x38000000
        canvas.drawCircle(0f, 0f, 17f * u, overlayPaint)

        // 2. Headlight beam cone ahead of car
        val beamPath = Path().apply {
            moveTo(-4.5f * u, -10f * u)
            lineTo(-10f * u, -22f * u)
            lineTo(10f * u, -22f * u)
            lineTo(4.5f * u, -10f * u)
            close()
        }
        overlayPaint.color = (palette.accent and 0x00FFFFFF) or 0x44000000
        canvas.drawPath(beamPath, overlayPaint)

        // 3. Outer white chassis border + side mirrors
        overlayPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(RectF(-8.2f * u, -4.5f * u, 8.2f * u, -2.2f * u), 1.5f * u, 1.5f * u, overlayPaint)
        canvas.drawRoundRect(RectF(-6.8f * u, -11.5f * u, 6.8f * u, 11.5f * u), 4.2f * u, 4.2f * u, overlayPaint)

        // 4. Inner car body in theme accent color
        overlayPaint.color = palette.accent
        canvas.drawRoundRect(RectF(-5.4f * u, -10.1f * u, 5.4f * u, 10.1f * u), 3.4f * u, 3.4f * u, overlayPaint)

        // 5. Dark front windshield, roof, & rear window
        val glassColor = if (palette.isDark) 0xFF0E1318.toInt() else 0xFF1E293B.toInt()
        overlayPaint.color = glassColor
        // Front windshield
        canvas.drawRoundRect(RectF(-4.1f * u, -6.2f * u, 4.1f * u, -2.0f * u), 1.5f * u, 1.5f * u, overlayPaint)
        // Rear window
        canvas.drawRoundRect(RectF(-3.8f * u, 4.2f * u, 3.8f * u, 7.2f * u), 1.2f * u, 1.2f * u, overlayPaint)

        // 6. Roof panel highlight
        overlayPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(RectF(-3.6f * u, -1.2f * u, 3.6f * u, 3.5f * u), 1.2f * u, 1.2f * u, overlayPaint)

        canvas.restore()
    }

    private fun drawLiveOsmTiles(canvas: Canvas, w: Float, h: Float): Boolean {
        val z = zoomLevel
        val n = (1 shl z).toDouble()
        val exactX = (currentLon + 180.0) / 360.0 * n - (panOffsetPxX / 256.0)
        val latRad = Math.toRadians(currentLat.coerceIn(-85.0, 85.0))
        val exactY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n - (panOffsetPxY / 256.0)

        val centerTileX = floor(exactX).toInt()
        val centerTileY = floor(exactY).toInt()
        val offsetPxX = ((exactX - centerTileX) * 256.0).toFloat()
        val offsetPxY = ((exactY - centerTileY) * 256.0).toFloat()

        val centerX = w * 0.50f
        val centerY = h * 0.50f

        val rangeX = if (interactivePanZoom) 3 else 1
        val rangeY = if (interactivePanZoom) 2 else 1

        var drawnCount = 0
        for (dx in -rangeX..rangeX) {
            for (dy in -rangeY..rangeY) {
                val tx = centerTileX + dx
                val ty = centerTileY + dy
                if (tx < 0 || ty < 0 || tx >= n.toInt() || ty >= n.toInt()) continue

                val key = "osm_v3/$z/$tx/$ty"
                val bmp = tileCache.get(key)
                val left = centerX - offsetPxX + dx * 256f
                val top = centerY - offsetPxY + dy * 256f

                if (bmp != null && !bmp.isRecycled) {
                    canvas.drawBitmap(bmp, left, top, tilePaint)
                    drawnCount++
                } else {
                    fetchOsmTileAsync(z, tx, ty, key)
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
                    setRequestProperty("User-Agent", "HarayaDock/1.3 (https://github.com/harisamjd/Haraya-Car-Head-Unit)")
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
