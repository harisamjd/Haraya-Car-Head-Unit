package com.haraya.dock.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.*
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import kotlin.math.min

/**
 * Komponen Modal / Pop-up kustom Haraya Dock yang mengikuti tema aplikasi (Dark Carbon / Light Alabaster)
 * secara 100%, menggantikan AlertDialog bawaan sistem Android yang kaku.
 */
object HarayaDialog {

    data class ActionButton(
        val label: String,
        val isPrimary: Boolean = false,
        val isDestructive: Boolean = false,
        val onClick: (Dialog) -> Unit
    )

    fun showCustomModal(
        context: Context,
        palette: GlassPalette,
        title: String,
        subtitle: String? = null,
        contentBuilder: (LinearLayout, Dialog) -> Unit,
        actions: List<ActionButton>
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dp = { v: Float -> GlassTheme.dp(context, v) }

        val outerCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val modalBg = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(palette.cardTop, palette.cardBottom)
            ).apply {
                cornerRadius = GlassTheme.dpf(context, 18f)
                setStroke(dp(1.2f), palette.stroke)
            }
            background = modalBg
            setPadding(dp(18f), dp(14f), dp(18f), dp(14f))
        }

        // Header Row (Title + Subtitle + Close X)
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val titleTv = TextView(context).apply {
            text = title
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(palette.textPrimary)
            includeFontPadding = false
        }
        titleCol.addView(titleTv)

        if (!subtitle.isNullOrBlank()) {
            val subTv = TextView(context).apply {
                text = subtitle
                textSize = 10.5f
                setTextColor(palette.textSecondary)
                includeFontPadding = false
            }
            titleCol.addView(subTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2f)
            })
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(palette.textSecondary)
            gravity = Gravity.CENTER
            background = GlassTheme.innerDrawable(context, palette, 8f)
            setPadding(dp(9f), dp(4f), dp(9f), dp(4f))
            GlassTheme.makePressable(context, this, palette, 8f)
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(10f)
        })
        headerRow.addView(closeBtn)
        outerCard.addView(headerRow)

        // Divider
        val divider = View(context).apply {
            setBackgroundColor(palette.stroke)
        }
        outerCard.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)).apply {
            topMargin = dp(10f)
            bottomMargin = dp(10f)
        })

        // Scrollable Body Area (agar aman di semua resolusi landscape head unit)
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
        }
        val bodyContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentBuilder(bodyContainer, dialog)
        scrollView.addView(bodyContainer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        outerCard.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Action Buttons Row
        if (actions.isNotEmpty()) {
            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            for ((idx, act) in actions.withIndex()) {
                val btn = TextView(context).apply {
                    text = act.label
                    textSize = 11f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
                    includeFontPadding = false

                    when {
                        act.isPrimary -> {
                            background = GlassTheme.accentButtonDrawable(context, palette, 10f)
                            setTextColor(if (palette.isDark) 0xFF0B0E11.toInt() else 0xFFFFFFFF.toInt())
                        }
                        act.isDestructive -> {
                            background = GradientDrawable().apply {
                                cornerRadius = GlassTheme.dpf(context, 10f)
                                setColor(if (palette.isDark) 0x33EF4444 else 0x22EF4444)
                                setStroke(dp(1f), 0xFFEF4444.toInt())
                            }
                            setTextColor(if (palette.isDark) 0xFFFCA5A5.toInt() else 0xFFB91C1C.toInt())
                        }
                        else -> {
                            background = GlassTheme.innerDrawable(context, palette, 10f)
                            setTextColor(palette.textPrimary)
                        }
                    }
                    GlassTheme.makePressable(context, this, palette, 10f)
                    setOnClickListener { act.onClick(dialog) }
                }
                actionRow.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (idx > 0) leftMargin = dp(8f)
                })
            }
            outerCard.addView(actionRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12f)
            })
        }

        dialog.setContentView(outerCard)
        dialog.window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val dm = context.resources.displayMetrics
            val targetW = min((dm.widthPixels * 0.64f).toInt(), dp(520f))
            val maxH = (dm.heightPixels * 0.88f).toInt()
            win.setLayout(targetW, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.attributes = win.attributes.apply {
                dimAmount = 0.68f
                height = min(ViewGroup.LayoutParams.WRAP_CONTENT, maxH)
            }
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.show()
        return dialog
    }

    fun buildStyledInput(
        context: Context,
        palette: GlassPalette,
        hintText: String,
        initialText: String = "",
        inputTypeFlags: Int,
        maxLength: Int = 60,
        centerText: Boolean = false
    ): EditText {
        val dp = { v: Float -> GlassTheme.dp(context, v) }
        return EditText(context).apply {
            setText(initialText)
            hint = hintText
            inputType = inputTypeFlags
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textMuted)
            gravity = if (centerText) Gravity.CENTER else (Gravity.START or Gravity.CENTER_VERTICAL)
            background = GlassTheme.innerDrawable(context, palette, 10f)
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            setSingleLine(true)
        }
    }

    fun buildOptionRow(
        context: Context,
        palette: GlassPalette,
        title: String,
        subtitle: String,
        badgeText: String,
        badgePositive: Boolean = false,
        onClick: () -> Unit
    ): LinearLayout {
        val dp = { v: Float -> GlassTheme.dp(context, v) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GlassTheme.innerDrawable(context, palette, 10f)
            setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
            GlassTheme.makePressable(context, this, palette, 10f)
            setOnClickListener { onClick() }

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val tTv = TextView(context).apply {
                text = title
                textSize = 11.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(palette.textPrimary)
                includeFontPadding = false
            }
            val sTv = TextView(context).apply {
                text = subtitle
                textSize = 9.5f
                setTextColor(palette.textSecondary)
                includeFontPadding = false
            }
            textCol.addView(tTv)
            textCol.addView(sTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2f)
            })

            val badgeTv = TextView(context).apply {
                text = badgeText
                textSize = 9.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setPadding(dp(9f), dp(4f), dp(9f), dp(4f))
                includeFontPadding = false
                if (badgePositive) {
                    background = GradientDrawable().apply {
                        cornerRadius = GlassTheme.dpf(context, 8f)
                        setColor(if (palette.isDark) 0x2E10B981 else 0x22059669)
                        setStroke(dp(1f), if (palette.isDark) 0xFF34D399.toInt() else 0xFF059669.toInt())
                    }
                    setTextColor(if (palette.isDark) 0xFF34D399.toInt() else 0xFF059669.toInt())
                } else {
                    background = GlassTheme.selectDrawable(context, palette, 8f)
                    setTextColor(palette.selectFg)
                }
            }

            addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8f)
            })
            addView(badgeTv)
        }
    }
}

