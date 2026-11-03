package com.haraya.dock.ui.glass

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Kartu Kaca Utama dengan gradien translusen atas-bawah dan tepi spekular tipis.
 */
class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        applyTheme(GlassTheme.Dark)
    }

    fun applyTheme(palette: GlassPalette) {
        background = GlassTheme.cardDrawable(context, palette)
    }
}
