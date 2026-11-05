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
import android.view.MotionEvent
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
import kotlin.math.abs

/**
 * Persistent Floating Dock Overlay (SYSTEM_ALERT_WINDOW) with AssistiveTouch-style
 * collapsible & freely draggable circular orb so it never blocks bottom menus in
 * WhatsApp, YouTube, Instagram, or Maps.
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
    private var windowParams: WindowManager.LayoutParams? = null
    private var expandedBar: LinearLayout? = null
    private var assistiveOrbBox: FrameLayout? = null
    private var assistiveOrbIcon: VectorIconView? = null
    private var collapseShortcutBtn: FrameLayout? = null
    private var collapseShortcutIcon: VectorIconView? = null
    private var isCollapsed: Boolean = false

    // Saved AssistiveTouch orb position (offset from TOP|START)
    private var orbPosX: Int = 24
    private var orbPosY: Int = 180

    private var speedClockTv: TextView? = null
    private var mediaTitleTv: TextView? = null
    private var playPauseIcon: VectorIconView? = null

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
        val prefs = getSharedPreferences("haraya_prefs", Context.MODE_PRIVATE)
        val dm = resources.displayMetrics
        orbPosX = prefs.getInt("assistive_orb_x", (dm.widthPixels - GlassTheme.dp(this, 64f)).coerceAtLeast(16))
        orbPosY = prefs.getInt("assistive_orb_y", (dm.heightPixels * 0.55f).toInt())
        isCollapsed = prefs.getBoolean("assistive_orb_collapsed", false)
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

        // 1. Expanded Floating Dock Bar (Bottom Center)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        }

        // Home Button
        val homeBtn = buildActionPill("Home", VectorIconView.IconType.HOME, isHighlight = true) {
            openHarayaScreen(MainActivity.EXTRA_TARGET_HOME)
        }
        bar.addView(homeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(8f)
        })

        // Live Speed & Clock Indicator
        val scTv = TextView(this).apply {
            text = "0 km/h • --:--"
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

        // Mini Media Controls (Song Title + Prev/Play/Next)
        val mediaGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(4f), dp(6f), dp(4f))
        }
        val mTv = TextView(this).apply {
            text = "Media Control"
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

        // Map, OBD, & Apps Shortcuts
        val mapBtn = buildActionPill("Map", VectorIconView.IconType.MAPS, isHighlight = false) {
            openHarayaScreen(MainActivity.EXTRA_TARGET_MAP)
        }
        val obdBtn = buildActionPill("OBD", VectorIconView.IconType.GAUGE, isHighlight = false) {
            openHarayaScreen(MainActivity.EXTRA_TARGET_OBD)
        }
        val appsBtn = buildActionPill("Apps", VectorIconView.IconType.APPS_GRID, isHighlight = false) {
            openHarayaScreen(MainActivity.EXTRA_TARGET_APPS)
        }
        bar.addView(mapBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(6f)
        })
        bar.addView(obdBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(6f)
        })
        bar.addView(appsBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(8f)
        })

        // Circular AssistiveTouch Collapse Shortcut Button
        val collapseIcon = VectorIconView(this, VectorIconView.IconType.ASSISTIVE_ORB)
        val collapseBtn = FrameLayout(this).apply {
            tag = "collapse_orb_shortcut"
            addView(collapseIcon, FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER))
            setOnClickListener {
                setCollapsedState(true)
            }
        }
        collapseShortcutBtn = collapseBtn
        collapseShortcutIcon = collapseIcon
        bar.addView(collapseBtn, LinearLayout.LayoutParams(dp(30f), dp(30f)))

        // 2. Collapsed AssistiveTouch Floating Circle (46dp x 46dp, freely draggable!)
        val orbIcon = VectorIconView(this, VectorIconView.IconType.ASSISTIVE_ORB)
        assistiveOrbIcon = orbIcon
        val orbCircle = FrameLayout(this).apply {
            visibility = View.GONE
            addView(orbIcon, FrameLayout.LayoutParams(dp(26f), dp(26f), Gravity.CENTER))
        }
        setupAssistiveOrbDragAndTap(orbCircle)
        assistiveOrbBox = orbCircle

        expandedBar = bar

        container.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(orbCircle, FrameLayout.LayoutParams(dp(46f), dp(46f)))

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
        )
        windowParams = lp

        runCatching {
            windowManager?.addView(container, lp)
            rootOverlayContainer = container
            updateCollapseVisibility()
            applyThemeAndRefresh()
        }
    }

    private fun setupAssistiveOrbDragAndTap(orbView: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startOrbX = 0
        var startOrbY = 0
        var dragging = false
        val touchSlop = GlassTheme.dp(this, 6f)

        orbView.setOnTouchListener { _, event ->
            val lp = windowParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startOrbX = lp.x
                    startOrbY = lp.y
                    dragging = false
                    orbView.alpha = 1.0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        val dm = resources.displayMetrics
                        val maxW = (dm.widthPixels - GlassTheme.dp(this, 48f)).coerceAtLeast(0)
                        val maxH = (dm.heightPixels - GlassTheme.dp(this, 48f)).coerceAtLeast(0)
                        orbPosX = (startOrbX + dx).coerceIn(0, maxW)
                        orbPosY = (startOrbY + dy).coerceIn(0, maxH)
                        lp.x = orbPosX
                        lp.y = orbPosY
                        runCatching { windowManager?.updateViewLayout(rootOverlayContainer, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        // Snap to nearest horizontal screen edge like AssistiveTouch
                        val dm = resources.displayMetrics
                        val edgeMargin = GlassTheme.dp(this, 10f)
                        val maxW = (dm.widthPixels - GlassTheme.dp(this, 56f)).coerceAtLeast(edgeMargin)
                        orbPosX = if (orbPosX + GlassTheme.dp(this, 23f) < dm.widthPixels / 2) {
                            edgeMargin
                        } else {
                            maxW
                        }
                        lp.x = orbPosX
                        runCatching { windowManager?.updateViewLayout(rootOverlayContainer, lp) }
                        getSharedPreferences("haraya_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putInt("assistive_orb_x", orbPosX)
                            .putInt("assistive_orb_y", orbPosY)
                            .apply()
                        orbView.alpha = 0.88f
                    } else {
                        setCollapsedState(false)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setCollapsedState(collapsed: Boolean) {
        isCollapsed = collapsed
        getSharedPreferences("haraya_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("assistive_orb_collapsed", isCollapsed)
            .apply()
        updateCollapseVisibility()
    }

    private fun updateCollapseVisibility() {
        val lp = windowParams ?: return
        val dp = { v: Float -> GlassTheme.dp(this, v) }
        if (isCollapsed) {
            expandedBar?.visibility = View.GONE
            assistiveOrbBox?.visibility = View.VISIBLE
            assistiveOrbBox?.alpha = 0.88f
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = orbPosX
            lp.y = orbPosY
        } else {
            expandedBar?.visibility = View.VISIBLE
            assistiveOrbBox?.visibility = View.GONE
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.x = 0
            lp.y = dp(6f)
        }
        if (rootOverlayContainer != null) {
            runCatching { windowManager?.updateViewLayout(rootOverlayContainer, lp) }
        }
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
                    child === collapseShortcutBtn -> {
                        child.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(pal.innerBottom)
                            setStroke(dp(1.2f), pal.accent)
                        }
                        collapseShortcutIcon?.setTint(pal.accent)
                    }
                }
            }
        }

        assistiveOrbBox?.let { orb ->
            orb.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(pal.overTop, pal.overBottom)
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1.6f), pal.accent)
            }
            assistiveOrbIcon?.setTint(pal.accent)
        }

        refreshOverlayContent()
    }

    private fun refreshOverlayContent() {
        val clockStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        speedClockTv?.text = "$currentSpeedKmh km/h • $clockStr"

        val playing = NotificationCollectorService.isCurrentlyPlaying(this)
        playPauseIcon?.setIcon(
            if (playing) VectorIconView.IconType.PAUSE else VectorIconView.IconType.PLAY_PAUSE
        )
        val title = NotificationCollectorService.currentMediaTitle
        mediaTitleTv?.text = if (!title.isNullOrBlank()) {
            (if (playing) "▶ " else "⏸ ") + title
        } else {
            if (playing) "▶ Active Audio" else "Media Control"
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
        assistiveOrbBox = null
    }
}
