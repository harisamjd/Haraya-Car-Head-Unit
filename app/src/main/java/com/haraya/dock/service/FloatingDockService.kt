package com.haraya.dock.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.haraya.dock.MainActivity
import com.haraya.dock.ui.VectorIconView
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent Floating Dock Overlay (SYSTEM_ALERT_WINDOW) yang tetap tampil di bagian bawah layar
 * saat pengguna membuka aplikasi eksternal (YouTube, Spotify, Google Maps, Waze, dll.),
 * sehingga semua aplikasi terasa "embedded" di dalam bingkai Haraya Dock.
 */
class FloatingDockService : Service() {

    companion object {
        const val ACTION_SHOW_OVERLAY = "com.haraya.dock.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.haraya.dock.HIDE_OVERLAY"
        const val ACTION_UPDATE_STATE = "com.haraya.dock.UPDATE_OVERLAY_STATE"

        @Volatile
        var currentSpeedKmh: Int = 0

        @Volatile
        var isDarkTheme: Boolean = true

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun showOverlayWhenLeavingApp(context: Context, speedKmh: Int, isDark: Boolean) {
            currentSpeedKmh = speedKmh
            isDarkTheme = isDark
            if (!canDrawOverlays(context)) return
            runCatching {
                context.startService(Intent(context, FloatingDockService::class.java).apply {
                    action = ACTION_SHOW_OVERLAY
                })
            }
        }

        fun hideOverlayInMainApp(context: Context) {
            if (!canDrawOverlays(context)) return
            runCatching {
                context.startService(Intent(context, FloatingDockService::class.java).apply {
                    action = ACTION_HIDE_OVERLAY
                })
            }
        }

        fun notifyStateUpdated(context: Context, speedKmh: Int, isDark: Boolean) {
            currentSpeedKmh = speedKmh
            isDarkTheme = isDark
            if (!canDrawOverlays(context)) return
            runCatching {
                context.startService(Intent(context, FloatingDockService::class.java).apply {
                    action = ACTION_UPDATE_STATE
                })
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var rootOverlayContainer: FrameLayout? = null
    private var expandedBar: LinearLayout? = null
    private var collapsedPill: LinearLayout? = null
    private var isCollapsed: Boolean = false

    private var speedClockTv: TextView? = null
    private var mediaTitleTv: TextView? = null
    private var playPauseIcon: VectorIconView? = null
    private var collapsedTextTv: TextView? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val periodicTicker = object : Runnable {
        override fun run() {
            refreshOverlayContent()
            mainHandler.postDelayed(this, 2000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                ensureOverlayCreated()
                rootOverlayContainer?.visibility = View.VISIBLE
                applyThemeAndRefresh()
                mainHandler.removeCallbacks(periodicTicker)
                mainHandler.post(periodicTicker)
            }
            ACTION_HIDE_OVERLAY -> {
                mainHandler.removeCallbacks(periodicTicker)
                rootOverlayContainer?.visibility = View.GONE
            }
            ACTION_UPDATE_STATE -> {
                if (rootOverlayContainer?.visibility == View.VISIBLE) {
                    applyThemeAndRefresh()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(periodicTicker)
        removeOverlayView()
        super.onDestroy()
    }

    private fun ensureOverlayCreated() {
        if (rootOverlayContainer != null) return

        val dp = { v: Float -> GlassTheme.dp(this, v) }
        val container = FrameLayout(this)

        // 1. Expanded Floating Dock Bar (Tengah Bawah)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        }

        // Tombol Beranda Haraya
        val homeBtn = buildActionPill("Beranda", VectorIconView.IconType.HOME, isHighlight = true) {
            openHarayaScreen(MainActivity.EXTRA_TARGET_HOME)
        }
        bar.addView(homeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(8f)
        })

        // Indikator Kecepatan & Jam Live
        val scTv = TextView(this).apply {
            text = "0 km/j • --:--"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
        }
        speedClockTv = scTv
        bar.addView(scTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(8f)
        })

        // Kontrol Media Mini (Judul Lagu + Prev/Play/Next)
        val mediaGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(4f), dp(6f), dp(4f))
        }
        val mTv = TextView(this).apply {
            text = "Kontrol Media"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(155f)
            setOnClickListener {
                launchExternalMusicApp()
            }
        }
        mediaTitleTv = mTv
        mediaGroup.addView(mTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(6f)
        })

        val prevBtn = buildMiniIconBtn(VectorIconView.IconType.PREV_TRACK) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        val playPair = buildMiniIconBtnWithRef(VectorIconView.IconType.PLAY_PAUSE) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        playPauseIcon = playPair.second
        val nextBtn = buildMiniIconBtn(VectorIconView.IconType.NEXT_TRACK) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        mediaGroup.addView(prevBtn, LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { rightMargin = dp(4f) })
        mediaGroup.addView(playPair.first, LinearLayout.LayoutParams(dp(26f), dp(26f)).apply { rightMargin = dp(4f) })
        mediaGroup.addView(nextBtn, LinearLayout.LayoutParams(dp(24f), dp(24f)))

        bar.addView(mediaGroup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(8f)
        })

        // Tombol Peta, OBD, & Semua Aplikasi
        val mapBtn = buildActionPill("Peta", VectorIconView.IconType.MAPS, isHighlight = false) {
            openHarayaScreen(MainActivity.EXTRA_TARGET_MAP)
        }
        val obdBtn = buildActionPill("OBD", VectorIconView.IconType.GAUGE, isHighlight = false) {
            openHarayaScreen(MainActivity.EXTRA_TARGET_OBD)
        }
        val appsBtn = buildActionPill("Aplikasi", VectorIconView.IconType.APPS_GRID, isHighlight = false) {
            openHarayaScreen(MainActivity.EXTRA_TARGET_APPS)
        }
        bar.addView(mapBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(6f)
        })
        bar.addView(obdBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(6f)
        })
        bar.addView(appsBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(6f)
        })

        // Tombol Kecilkan Dock (Minimize)
        val minBtn = TextView(this).apply {
            text = "▾"
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            setOnClickListener {
                isCollapsed = true
                updateCollapseVisibility()
            }
        }
        bar.addView(minBtn)

        // 2. Collapsed Mini Pill (Saat dikecilkan agar tidak menghalangi tombol bawah aplikasi)
        val miniPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            visibility = View.GONE
            setOnClickListener {
                isCollapsed = false
                updateCollapseVisibility()
            }
        }
        val miniIcon = VectorIconView(this, VectorIconView.IconType.HOME)
        val miniTv = TextView(this).apply {
            text = "Haraya Dock • 0 km/j ▴"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
        }
        collapsedTextTv = miniTv
        miniPill.addView(miniIcon, LinearLayout.LayoutParams(dp(14f), dp(14f)).apply { rightMargin = dp(6f) })
        miniPill.addView(miniTv)

        expandedBar = bar
        collapsedPill = miniPill

        container.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        container.addView(miniPill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(6f)
        }

        runCatching {
            windowManager?.addView(container, lp)
            rootOverlayContainer = container
            applyThemeAndRefresh()
        }
    }

    private fun updateCollapseVisibility() {
        expandedBar?.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        collapsedPill?.visibility = if (isCollapsed) View.VISIBLE else View.GONE
    }

    private fun buildActionPill(label: String, iconType: VectorIconView.IconType, isHighlight: Boolean, onClick: () -> Unit): LinearLayout {
        val dp = { v: Float -> GlassTheme.dp(this, v) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
            tag = if (isHighlight) "highlight" else "normal"
            val ic = VectorIconView(this@FloatingDockService, iconType)
            val tv = TextView(this@FloatingDockService).apply {
                text = label
                textSize = 9.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                includeFontPadding = false
            }
            addView(ic, LinearLayout.LayoutParams(dp(13f), dp(13f)).apply { rightMargin = dp(5f) })
            addView(tv)
            setOnClickListener { onClick() }
        }
    }

    private fun buildMiniIconBtn(iconType: VectorIconView.IconType, onClick: () -> Unit): FrameLayout {
        return buildMiniIconBtnWithRef(iconType, onClick).first
    }

    private fun buildMiniIconBtnWithRef(iconType: VectorIconView.IconType, onClick: () -> Unit): Pair<FrameLayout, VectorIconView> {
        val dp = { v: Float -> GlassTheme.dp(this, v) }
        val ic = VectorIconView(this, iconType)
        val box = FrameLayout(this).apply {
            addView(ic, FrameLayout.LayoutParams(dp(12f), dp(12f), Gravity.CENTER))
            setOnClickListener { onClick() }
        }
        return box to ic
    }

    private fun applyThemeAndRefresh() {
        val pal: GlassPalette = if (isDarkTheme) GlassTheme.Dark else GlassTheme.Light
        val dp = { v: Float -> GlassTheme.dp(this, v) }

        expandedBar?.let { bar ->
            bar.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(pal.overTop, pal.overBottom)
            ).apply {
                cornerRadius = GlassTheme.dpf(this@FloatingDockService, 16f)
                setStroke(dp(1.2f), pal.stroke)
            }

            for (i in 0 until bar.childCount) {
                val child = bar.getChildAt(i)
                when {
                    child === speedClockTv -> {
                        (child as TextView).background = GlassTheme.innerDrawable(this, pal, 8f)
                        child.setTextColor(pal.accent)
                    }
                    child is LinearLayout && child.tag == "highlight" -> {
                        child.background = GlassTheme.accentButtonDrawable(this, pal, 9f)
                        val fg = if (pal.isDark) 0xFF0B0E11.toInt() else 0xFFFFFFFF.toInt()
                        (child.getChildAt(0) as? VectorIconView)?.setTint(fg)
                        (child.getChildAt(1) as? TextView)?.setTextColor(fg)
                    }
                    child is LinearLayout && child.tag == "normal" -> {
                        child.background = GlassTheme.innerDrawable(this, pal, 9f)
                        (child.getChildAt(0) as? VectorIconView)?.setTint(pal.textPrimary)
                        (child.getChildAt(1) as? TextView)?.setTextColor(pal.textPrimary)
                    }
                    child is LinearLayout -> {
                        // Media group
                        child.background = GlassTheme.innerDrawable(this, pal, 9f)
                        mediaTitleTv?.setTextColor(pal.textPrimary)
                        for (j in 1 until child.childCount) {
                            val btn = child.getChildAt(j) as? FrameLayout ?: continue
                            btn.background = GlassTheme.selectDrawable(this, pal, 12f)
                            (btn.getChildAt(0) as? VectorIconView)?.setTint(pal.selectFg)
                        }
                    }
                    child is TextView -> {
                        // Minimize button
                        child.background = GlassTheme.innerDrawable(this, pal, 8f)
                        child.setTextColor(pal.textSecondary)
                    }
                }
            }
        }

        collapsedPill?.let { pill ->
            pill.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(pal.overTop, pal.overBottom)
            ).apply {
                cornerRadius = GlassTheme.dpf(this@FloatingDockService, 14f)
                setStroke(dp(1.2f), pal.accent)
            }
            (pill.getChildAt(0) as? VectorIconView)?.setTint(pal.accent)
            collapsedTextTv?.setTextColor(pal.textPrimary)
        }

        refreshOverlayContent()
    }

    private fun refreshOverlayContent() {
        val clockStr = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date())
        speedClockTv?.text = "$currentSpeedKmh km/j • $clockStr"
        collapsedTextTv?.text = "Haraya • $currentSpeedKmh km/j • $clockStr ▴"

        val playing = NotificationCollectorService.isCurrentlyPlaying(this)
        playPauseIcon?.setIcon(
            if (playing) VectorIconView.IconType.PAUSE else VectorIconView.IconType.PLAY_PAUSE
        )
        val title = NotificationCollectorService.currentMediaTitle
        mediaTitleTv?.text = if (!title.isNullOrBlank()) {
            (if (playing) "▶ " else "⏸ ") + title
        } else {
            if (playing) "▶ Musik Aktif" else "Kontrol Media"
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        mainHandler.postDelayed({
            NotificationCollectorService.pollNow(this)
            refreshOverlayContent()
        }, 400L)
    }

    private fun openHarayaScreen(targetScreen: String) {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_TARGET_SCREEN, targetScreen)
            }
            startActivity(intent)
        }
    }

    private fun launchExternalMusicApp() {
        val pkgs = listOf(
            "app.revanced.android.apps.youtube.music",
            "com.google.android.apps.youtube.music",
            "com.spotify.music",
            "com.android.music"
        )
        for (p in pkgs) {
            val i = packageManager.getLaunchIntentForPackage(p)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return
            }
        }
    }

    private fun removeOverlayView() {
        rootOverlayContainer?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        rootOverlayContainer = null
        expandedBar = null
        collapsedPill = null
    }
}

