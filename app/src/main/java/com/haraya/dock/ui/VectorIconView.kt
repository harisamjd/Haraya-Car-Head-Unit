package com.haraya.dock.ui

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom Vector Icon Renderer menggunakan Canvas Path murni.
 * Menggantikan penggunaan emoji agar tampilan tajam di semua DPI dan mengikuti warna tema.
 */
class VectorIconView(
    context: Context,
    private var iconType: IconType,
    private var tintColor: Int = Color.WHITE
) : View(context) {

    enum class IconType {
        HOME,
        MAPS,
        MUSIC,
        YOUTUBE,
        PHONE,
        APPS_GRID,
        SHARE,
        THEME_MODE,
        VOLUME,
        BRIGHTNESS,
        PIN,
        CAR,
        GAUGE,
        SEARCH,
        OIL_DROP,
        GEAR_COG,
        SNOWFLAKE,
        WRENCH,
        CLOUD_SUN,
        PREV_TRACK,
        PLAY_PAUSE,
        PAUSE,
        NEXT_TRACK,
        ROUTE,
        BELL,
        LAYOUT_SWITCH,
        LOCK
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val path = Path()
    private val rect = RectF()

    fun setTint(color: Int) {
        if (tintColor != color) {
            tintColor = color
            invalidate()
        }
    }

    fun setIcon(type: IconType) {
        if (iconType != type) {
            iconType = type
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        if (size <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val u = size / 24f // 24x24 virtual viewport

        strokePaint.color = tintColor
        strokePaint.strokeWidth = (2.1f * u).coerceAtLeast(2f)
        fillPaint.color = tintColor
        path.reset()

        when (iconType) {
            IconType.HOME -> {
                // Rumah / Beranda modern
                path.moveTo(cx - 8f * u, cy - 1f * u)
                path.lineTo(cx, cy - 8.5f * u)
                path.lineTo(cx + 8f * u, cy - 1f * u)
                path.moveTo(cx - 6f * u, cy - 2.2f * u)
                path.lineTo(cx - 6f * u, cy + 7.5f * u)
                path.lineTo(cx + 6f * u, cy + 7.5f * u)
                path.lineTo(cx + 6f * u, cy - 2.2f * u)
                canvas.drawPath(path, strokePaint)
            }

            IconType.MAPS -> {
                // Navigasi panah kompas presisi
                path.moveTo(cx, cy - 8.5f * u)
                path.lineTo(cx + 7.5f * u, cy + 7.5f * u)
                path.lineTo(cx, cy + 3.8f * u)
                path.lineTo(cx - 7.5f * u, cy + 7.5f * u)
                path.close()
                canvas.drawPath(path, strokePaint)
            }

            IconType.MUSIC -> {
                // Tangga nada musik ganda
                path.moveTo(cx - 3.5f * u, cy + 4.5f * u)
                path.lineTo(cx - 3.5f * u, cy - 6.5f * u)
                path.lineTo(cx + 6.5f * u, cy - 8f * u)
                path.lineTo(cx + 6.5f * u, cy + 3f * u)
                canvas.drawPath(path, strokePaint)
                canvas.drawCircle(cx - 5.8f * u, cy + 4.8f * u, 2.5f * u, fillPaint)
                canvas.drawCircle(cx + 4.2f * u, cy + 3.3f * u, 2.5f * u, fillPaint)
            }

            IconType.YOUTUBE -> {
                // Layar video + segitiga putar
                rect.set(cx - 8.5f * u, cy - 6f * u, cx + 8.5f * u, cy + 6f * u)
                canvas.drawRoundRect(rect, 3.2f * u, 3.2f * u, strokePaint)
                path.moveTo(cx - 2f * u, cy - 3f * u)
                path.lineTo(cx + 3.5f * u, cy)
                path.lineTo(cx - 2f * u, cy + 3f * u)
                path.close()
                canvas.drawPath(path, fillPaint)
            }

            IconType.PHONE -> {
                // Gagang telepon lengkung
                rect.set(cx - 6.5f * u, cy - 6.5f * u, cx + 6.5f * u, cy + 6.5f * u)
                path.moveTo(cx - 6f * u, cy - 4f * u)
                path.quadTo(cx - 6.5f * u, cy + 6.5f * u, cx + 4f * u, cy + 6f * u)
                path.lineTo(cx + 6.5f * u, cy + 3.5f * u)
                path.lineTo(cx + 3.5f * u, cy + 1f * u)
                path.lineTo(cx + 1.5f * u, cy + 2.8f * u)
                path.quadTo(cx - 2.8f * u, cy + 1f * u, cx - 2.8f * u, cy - 1.5f * u)
                path.lineTo(cx - 1f * u, cy - 3.5f * u)
                path.lineTo(cx - 3.5f * u, cy - 6.5f * u)
                path.close()
                canvas.drawPath(path, strokePaint)
            }

            IconType.APPS_GRID -> {
                // Grid 2x2 kotak bersudut bulat
                val r = 2.8f * u
                val gap = 1.6f * u
                val box = 5.4f * u
                rect.set(cx - gap - box, cy - gap - box, cx - gap, cy - gap)
                canvas.drawRoundRect(rect, r, r, strokePaint)
                rect.set(cx + gap, cy - gap - box, cx + gap + box, cy - gap)
                canvas.drawRoundRect(rect, r, r, strokePaint)
                rect.set(cx - gap - box, cy + gap, cx - gap, cy + gap + box)
                canvas.drawRoundRect(rect, r, r, strokePaint)
                rect.set(cx + gap, cy + gap, cx + gap + box, cy + gap + box)
                canvas.drawRoundRect(rect, r, r, strokePaint)
            }

            IconType.SHARE -> {
                // Simpul bagi lokasi / sinyal pancar
                canvas.drawCircle(cx - 5.5f * u, cy, 2.3f * u, strokePaint)
                canvas.drawCircle(cx + 5f * u, cy - 5.5f * u, 2.3f * u, strokePaint)
                canvas.drawCircle(cx + 5f * u, cy + 5.5f * u, 2.3f * u, strokePaint)
                canvas.drawLine(cx - 3.3f * u, cy - 1.1f * u, cx + 2.9f * u, cy - 4.4f * u, strokePaint)
                canvas.drawLine(cx - 3.3f * u, cy + 1.1f * u, cx + 2.9f * u, cy + 4.4f * u, strokePaint)
            }

            IconType.THEME_MODE -> {
                // Bulan sabit & matahari komposit
                rect.set(cx - 6.5f * u, cy - 6.5f * u, cx + 6.5f * u, cy + 6.5f * u)
                canvas.drawArc(rect, 40f, 280f, false, strokePaint)
                canvas.drawCircle(cx + 3.5f * u, cy - 3.5f * u, 1.6f * u, fillPaint)
            }

            IconType.VOLUME -> {
                // Speaker + gelombang suara
                path.moveTo(cx - 7.5f * u, cy - 2.8f * u)
                path.lineTo(cx - 4f * u, cy - 2.8f * u)
                path.lineTo(cx, cy - 6.5f * u)
                path.lineTo(cx, cy + 6.5f * u)
                path.lineTo(cx - 4f * u, cy + 2.8f * u)
                path.lineTo(cx - 7.5f * u, cy + 2.8f * u)
                path.close()
                canvas.drawPath(path, strokePaint)

                rect.set(cx - 1f * u, cy - 4.2f * u, cx + 4.8f * u, cy + 4.2f * u)
                canvas.drawArc(rect, -50f, 100f, false, strokePaint)
                rect.set(cx - 2f * u, cy - 7f * u, cx + 7.8f * u, cy + 7f * u)
                canvas.drawArc(rect, -50f, 100f, false, strokePaint)
            }

            IconType.BRIGHTNESS -> {
                // Matahari + 8 pancaran sinar
                canvas.drawCircle(cx, cy, 3.8f * u, strokePaint)
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val r1 = 6.2f * u
                    val r2 = 8.4f * u
                    canvas.drawLine(
                        cx + (r1 * cos(angle)).toFloat(),
                        cy + (r1 * sin(angle)).toFloat(),
                        cx + (r2 * cos(angle)).toFloat(),
                        cy + (r2 * sin(angle)).toFloat(),
                        strokePaint
                    )
                }
            }

            IconType.PIN -> {
                // Pin peta
                path.moveTo(cx, cy + 8f * u)
                path.quadTo(cx - 6.5f * u, cy + 1f * u, cx - 6.5f * u, cy - 2.5f * u)
                rect.set(cx - 6.5f * u, cy - 8.5f * u, cx + 6.5f * u, cy + 3.5f * u)
                path.arcTo(rect, 180f, 180f, false)
                path.quadTo(cx + 6.5f * u, cy + 1f * u, cx, cy + 8f * u)
                path.close()
                canvas.drawPath(path, strokePaint)
                canvas.drawCircle(cx, cy - 2.5f * u, 2.2f * u, fillPaint)
            }

            IconType.CAR -> {
                // Siluet mobil depan / samping tactile
                path.moveTo(cx - 6.5f * u, cy + 2f * u)
                path.lineTo(cx - 4.5f * u, cy - 3.8f * u)
                path.lineTo(cx + 4.5f * u, cy - 3.8f * u)
                path.lineTo(cx + 6.5f * u, cy + 2f * u)
                path.lineTo(cx + 8f * u, cy + 3.2f * u)
                path.lineTo(cx + 8f * u, cy + 5f * u)
                path.lineTo(cx - 8f * u, cy + 5f * u)
                path.lineTo(cx - 8f * u, cy + 3.2f * u)
                path.close()
                canvas.drawPath(path, strokePaint)

                // Roda
                canvas.drawCircle(cx - 4.8f * u, cy + 5f * u, 1.8f * u, fillPaint)
                canvas.drawCircle(cx + 4.8f * u, cy + 5f * u, 1.8f * u, fillPaint)
            }

            IconType.GAUGE -> {
                // Dial meter 220 derajat
                rect.set(cx - 6.5f * u, cy - 6.5f * u, cx + 6.5f * u, cy + 6.5f * u)
                canvas.drawArc(rect, 150f, 240f, false, strokePaint)
                // Jarum penunjuk
                canvas.drawLine(cx, cy, cx + 3.8f * u, cy - 3.8f * u, strokePaint)
                canvas.drawCircle(cx, cy, 1.6f * u, fillPaint)
            }

            IconType.SEARCH -> {
                // Kaca pembesar
                val glassR = 4f * u
                val glassCx = cx - 1.8f * u
                val glassCy = cy - 1.8f * u
                canvas.drawCircle(glassCx, glassCy, glassR, strokePaint)
                canvas.drawLine(
                    glassCx + glassR * 0.707f,
                    glassCy + glassR * 0.707f,
                    cx + 6f * u,
                    cy + 6f * u,
                    strokePaint
                )
            }

            IconType.OIL_DROP -> {
                // Tetesan oli mesin
                path.moveTo(cx, cy - 7.5f * u)
                path.quadTo(cx + 6f * u, cy + 0.5f * u, cx + 5f * u, cy + 4.5f * u)
                rect.set(cx - 5f * u, cy - 1f * u, cx + 5f * u, cy + 7.5f * u)
                path.arcTo(rect, 0f, 180f, false)
                path.quadTo(cx - 6f * u, cy + 0.5f * u, cx, cy - 7.5f * u)
                path.close()
                canvas.drawPath(path, strokePaint)
            }

            IconType.GEAR_COG -> {
                // Roda gigi transmisi / setelan
                canvas.drawCircle(cx, cy, 3.2f * u, strokePaint)
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val r1 = 4.6f * u
                    val r2 = 7.4f * u
                    canvas.drawLine(
                        cx + (r1 * cos(angle)).toFloat(),
                        cy + (r1 * sin(angle)).toFloat(),
                        cx + (r2 * cos(angle)).toFloat(),
                        cy + (r2 * sin(angle)).toFloat(),
                        strokePaint
                    )
                }
            }

            IconType.SNOWFLAKE -> {
                // Kristal es AC
                for (i in 0 until 6) {
                    val angle = Math.toRadians((i * 60).toDouble())
                    canvas.drawLine(
                        cx - (7f * u * cos(angle)).toFloat(),
                        cy - (7f * u * sin(angle)).toFloat(),
                        cx + (7f * u * cos(angle)).toFloat(),
                        cy + (7f * u * sin(angle)).toFloat(),
                        strokePaint
                    )
                }
            }

            IconType.WRENCH -> {
                // Kunci pas servis / tune-up
                canvas.drawCircle(cx - 3.2f * u, cy - 3.2f * u, 3.6f * u, strokePaint)
                canvas.drawLine(cx - 0.5f * u, cy - 0.5f * u, cx + 6.5f * u, cy + 6.5f * u, strokePaint)
            }

            IconType.CLOUD_SUN -> {
                // Awan cuaca & matahari
                canvas.drawCircle(cx - 2.5f * u, cy - 3f * u, 3.2f * u, strokePaint)
                rect.set(cx - 6.5f * u, cy - 1f * u, cx + 6.5f * u, cy + 5.5f * u)
                canvas.drawRoundRect(rect, 3.2f * u, 3.2f * u, fillPaint)
            }

            IconType.PREV_TRACK -> {
                // Lagu sebelumnya
                canvas.drawLine(cx - 5.5f * u, cy - 5f * u, cx - 5.5f * u, cy + 5f * u, strokePaint)
                path.moveTo(cx + 5f * u, cy - 5f * u)
                path.lineTo(cx - 3f * u, cy)
                path.lineTo(cx + 5f * u, cy + 5f * u)
                path.close()
                canvas.drawPath(path, fillPaint)
            }

            IconType.PLAY_PAUSE -> {
                // Segitiga Play
                path.moveTo(cx - 3.5f * u, cy - 5.5f * u)
                path.lineTo(cx + 5.5f * u, cy)
                path.lineTo(cx - 3.5f * u, cy + 5.5f * u)
                path.close()
                canvas.drawPath(path, fillPaint)
            }

            IconType.PAUSE -> {
                // Dua batang vertikal Pause (||)
                rect.set(cx - 5f * u, cy - 5.5f * u, cx - 1.6f * u, cy + 5.5f * u)
                canvas.drawRoundRect(rect, 1.2f * u, 1.2f * u, fillPaint)
                rect.set(cx + 1.6f * u, cy - 5.5f * u, cx + 5f * u, cy + 5.5f * u)
                canvas.drawRoundRect(rect, 1.2f * u, 1.2f * u, fillPaint)
            }

            IconType.NEXT_TRACK -> {
                // Lagu berikutnya
                canvas.drawLine(cx + 5.5f * u, cy - 5f * u, cx + 5.5f * u, cy + 5f * u, strokePaint)
                path.moveTo(cx - 5f * u, cy - 5f * u)
                path.lineTo(cx + 3f * u, cy)
                path.lineTo(cx - 5f * u, cy + 5f * u)
                path.close()
                canvas.drawPath(path, fillPaint)
            }

            IconType.ROUTE -> {
                // Jalur S-curve perjalanan
                canvas.drawCircle(cx - 5f * u, cy + 5f * u, 2f * u, fillPaint)
                canvas.drawCircle(cx + 5f * u, cy - 5f * u, 2f * u, fillPaint)
                path.moveTo(cx - 5f * u, cy + 5f * u)
                path.cubicTo(cx - 5f * u, cy - 4f * u, cx + 5f * u, cy + 4f * u, cx + 5f * u, cy - 5f * u)
                canvas.drawPath(path, strokePaint)
            }

            IconType.BELL -> {
                // Lonceng pengingat
                path.moveTo(cx - 5.5f * u, cy + 3.5f * u)
                path.lineTo(cx - 4.5f * u, cy - 2.5f * u)
                rect.set(cx - 4.5f * u, cy - 6.5f * u, cx + 4.5f * u, cy + 1.5f * u)
                path.arcTo(rect, 180f, 180f, false)
                path.lineTo(cx + 5.5f * u, cy + 3.5f * u)
                path.close()
                canvas.drawPath(path, strokePaint)
                canvas.drawCircle(cx, cy + 6f * u, 1.5f * u, fillPaint)
            }

            IconType.LAYOUT_SWITCH -> {
                // Ikon tata letak 3 kolom kokpit
                rect.set(cx - 7.5f * u, cy - 5.5f * u, cx + 7.5f * u, cy + 5.5f * u)
                canvas.drawRoundRect(rect, 2f * u, 2f * u, strokePaint)
                canvas.drawLine(cx - 2.5f * u, cy - 5.5f * u, cx - 2.5f * u, cy + 5.5f * u, strokePaint)
                canvas.drawLine(cx + 2.5f * u, cy - 5.5f * u, cx + 2.5f * u, cy + 5.5f * u, strokePaint)
            }

            IconType.LOCK -> {
                // Gembok PIN
                rect.set(cx - 6f * u, cy - 1f * u, cx + 6f * u, cy + 7.5f * u)
                canvas.drawRoundRect(rect, 2f * u, 2f * u, strokePaint)
                rect.set(cx - 3.8f * u, cy - 7.5f * u, cx + 3.8f * u, cy + 0.5f * u)
                canvas.drawArc(rect, 180f, 180f, false, strokePaint)
                canvas.drawCircle(cx, cy + 3.2f * u, 1.4f * u, fillPaint)
            }
        }
    }
}

