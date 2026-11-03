package com.haraya.dock.ui

import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme

/**
 * Kartu slider sentuh bergaya instrumen untuk Volume dan Kecerahan.
 * Menggambar batang progres mulus dengan ikon vektor di kiri dan nilai persentase di kanan.
 */
class TactileSliderCard(
    context: Context,
    iconType: VectorIconView.IconType,
    private val titleText: String,
    initialFraction: Float,
    private val onValueChanged: (Float) -> Unit
) : FrameLayout(context) {

    private var fraction: Float = initialFraction.coerceIn(0f, 1f)
    private var palette: GlassPalette = GlassTheme.Dark

    private val iconView = VectorIconView(context, iconType, palette.textPrimary)
    private val labelTv = TextView(context).apply {
        text = titleText
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val valueTv = TextView(context).apply {
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val trackBar = SliderBarView(context)

    init {
        val padH = GlassTheme.dp(context, 14f)
        val padV = GlassTheme.dp(context, 10f)
        setPadding(padH, padV, padH, padV)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBoxSize = GlassTheme.dp(context, 24f)
        row.addView(iconView, LinearLayout.LayoutParams(iconBoxSize, iconBoxSize).apply {
            rightMargin = GlassTheme.dp(context, 10f)
        })

        val rightCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(labelTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(valueTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        rightCol.addView(headerRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = GlassTheme.dp(context, 6f)
        })
        rightCol.addView(trackBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, GlassTheme.dp(context, 12f)))

        row.addView(rightCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL))

        updateValueLabel()
        applyTheme(palette)
    }

    fun setFraction(newFraction: Float, notify: Boolean = false) {
        fraction = newFraction.coerceIn(0f, 1f)
        updateValueLabel()
        trackBar.invalidate()
        if (notify) {
            onValueChanged(fraction)
        }
    }

    private fun updateValueLabel() {
        valueTv.text = "${(fraction * 100).toInt()}%"
    }

    fun applyTheme(newPalette: GlassPalette) {
        palette = newPalette
        background = GlassTheme.cardDrawable(context, palette)
        iconView.setTint(palette.accent)
        labelTv.setTextColor(palette.textSecondary)
        valueTv.setTextColor(palette.textPrimary)
        trackBar.invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val pad = GlassTheme.dpf(context, 44f)
                val usableW = (width - pad - GlassTheme.dpf(context, 14f)).coerceAtLeast(1f)
                val relX = (event.x - pad) / usableW
                setFraction(relX, notify = true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private inner class SliderBarView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = h / 2f

            // Track latar
            paint.shader = null
            paint.color = palette.track
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, r, r, paint)

            // Isi aktif
            val fillW = (w * fraction).coerceAtLeast(h)
            if (fraction > 0.01f) {
                paint.shader = LinearGradient(0f, 0f, fillW, 0f, palette.accent, palette.accentLight, Shader.TileMode.CLAMP)
                rect.set(0f, 0f, fillW.coerceAtMost(w), h)
                canvas.drawRoundRect(rect, r, r, paint)
            }
        }
    }
}

