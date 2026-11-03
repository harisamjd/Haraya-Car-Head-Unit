package com.haraya.dock

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Typeface
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.haraya.dock.calc.PrayerCalc
import com.haraya.dock.data.QuoteRepository
import com.haraya.dock.theme.ThemeMode
import com.haraya.dock.theme.ThemeResolver
import com.haraya.dock.ui.MapCanvasView
import com.haraya.dock.ui.RpmGaugeView
import com.haraya.dock.ui.SpeedGaugeView
import com.haraya.dock.ui.TactileSliderCard
import com.haraya.dock.ui.VectorIconView
import com.haraya.dock.ui.glass.GlassCardView
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import com.haraya.dock.ui.glass.SilkBackgroundView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : Activity(), LocationListener {

    enum class AppScreen {
        HOME,
        OBD,
        APPS
    }

    data class AppEntry(
        val label: String,
        val packageName: String,
        val resolveInfo: ResolveInfo
    )

    private val themeResolver = ThemeResolver()
    private var themeMode: ThemeMode = ThemeMode.AUTO
    private var palette: GlassPalette = GlassTheme.Dark
    private var currentScreen: AppScreen = AppScreen.HOME
    private var quoteIndex: Int = 0

    // Koordinat lokasi
    private var currentLat: Double = -6.1754
    private var currentLon: Double = 106.8272
    private var currentKmh: Int = 0
    private var lastGeocodedLat: Double = 0.0
    private var lastGeocodedLon: Double = 0.0

    // Root & Backdrop
    private lateinit var silkBackground: SilkBackgroundView
    private lateinit var sidebarLayout: LinearLayout
    private val sidebarItems = mutableListOf<SidebarItemView>()
    private lateinit var themeSidebarItem: SidebarItemView

    // Container Layar Utama
    private lateinit var homeCockpitLayout: LinearLayout
    private lateinit var obdDashboardLayout: LinearLayout
    private lateinit var appsDrawerLayout: GlassCardView

    // === KOMPONEN BERANDA (HOME) ===
    private lateinit var clockSpeedCard: GlassCardView
    private lateinit var clockTimeTv: TextView
    private lateinit var clockDateTv: TextView
    private lateinit var themeStatusTv: TextView
    private lateinit var launcherStatusBtn: TextView
    private lateinit var speedWell: FrameLayout
    private lateinit var speedGauge: SpeedGaugeView

    private lateinit var prayerQuoteCard: GlassCardView
    private lateinit var prayerTitleTv: TextView
    private lateinit var prayerCountdownTv: TextView
    private val prayerSlotBoxes = mutableListOf<LinearLayout>()
    private val prayerSlotNames = mutableListOf<TextView>()
    private val prayerSlotTimes = mutableListOf<TextView>()
    private lateinit var quoteBox: LinearLayout
    private lateinit var quoteArabicTv: TextView
    private lateinit var quoteTranslationTv: TextView
    private lateinit var quoteHintTv: TextView

    private lateinit var mapOuterCard: GlassCardView
    private lateinit var mapViewport: FrameLayout
    private lateinit var mapCanvas: MapCanvasView
    private lateinit var locOverlayBox: LinearLayout
    private lateinit var locPinIcon: VectorIconView
    private lateinit var locTitleTv: TextView
    private lateinit var locSubTv: TextView
    private lateinit var shareCtaBtn: LinearLayout
    private lateinit var shareCtaIcon: VectorIconView
    private lateinit var shareCtaTv: TextView
    private lateinit var navFooterBox: LinearLayout
    private lateinit var navFooterIcon: VectorIconView
    private lateinit var navFooterTv: TextView

    private lateinit var volumeSlider: TactileSliderCard
    private lateinit var brightnessSlider: TactileSliderCard

    // === KOMPONEN OBD DASHBOARD ===
    private lateinit var obdClusterCard: GlassCardView
    private lateinit var obdClusterTitleTv: TextView
    private lateinit var obdGearIndicatorTv: TextView
    private lateinit var obdRpmGauge: RpmGaugeView
    private lateinit var obdSpeedGauge: SpeedGaugeView
    private lateinit var obdClusterFooterTv: TextView

    private lateinit var obdSensorsCard: GlassCardView
    private lateinit var obdSensorsTitleTv: TextView
    private val obdSensorCapsules = mutableListOf<LinearLayout>()
    private val obdSensorLabels = mutableListOf<TextView>()
    private val obdSensorValues = mutableListOf<TextView>()
    private val obdSensorSubs = mutableListOf<TextView>()

    private lateinit var obdDiagCard: GlassCardView
    private lateinit var obdBluetoothStatusTv: TextView
    private lateinit var obdDtcStatusTv: TextView
    private lateinit var obdScanDtcBtn: TextView
    private lateinit var obdBluetoothSettingsBtn: TextView
    private lateinit var obdBackHomeBtn: TextView

    // === KOMPONEN APPS DRAWER GRID ===
    private lateinit var appSearchBox: LinearLayout
    private lateinit var appSearchIcon: VectorIconView
    private lateinit var appSearchEt: EditText
    private lateinit var appCountTv: TextView
    private lateinit var appBackBtn: TextView
    private lateinit var appsGridView: GridView
    private lateinit var appsEmptyTv: TextView
    private val allApps = mutableListOf<AppEntry>()
    private val filteredApps = mutableListOf<AppEntry>()
    private lateinit var appsAdapter: AppsGridAdapter

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshClockAndSchedule()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("haraya_prefs", Context.MODE_PRIVATE)
        val savedMode = prefs.getString("theme_mode", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name
        themeMode = runCatching { ThemeMode.valueOf(savedMode) }.getOrDefault(ThemeMode.AUTO)
        quoteIndex = prefs.getInt("quote_idx", 0)

        val rootFrame = FrameLayout(this)
        silkBackground = SilkBackgroundView(this)
        rootFrame.addView(
            silkBackground,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val mainShell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val pad = dp(12f)
            setPadding(pad, pad, pad, pad)
        }

        // 1. SIDEBAR KIRI (Tetap terlihat di semua layar)
        sidebarLayout = buildSidebar()
        mainShell.addView(
            sidebarLayout,
            LinearLayout.LayoutParams(dp(76f), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                rightMargin = dp(10f)
            }
        )

        // 2. KONTEN TENGAH (BERANDA, OBD, ATAU SEMUA APLIKASI)
        val contentFrame = FrameLayout(this)

        homeCockpitLayout = buildHomeCockpit()
        obdDashboardLayout = buildObdDashboard()
        appsDrawerLayout = buildAppsDrawer()

        contentFrame.addView(
            homeCockpitLayout,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        contentFrame.addView(
            obdDashboardLayout,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        contentFrame.addView(
            appsDrawerLayout,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        mainShell.addView(
            contentFrame,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        )

        rootFrame.addView(
            mainShell,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(rootFrame)

        setScreen(AppScreen.HOME)
        refreshAll()
        requestLocationPermissionIfNeeded()
        loadInstalledAppsAsync()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(timeTickReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        updateLauncherStatus()
        refreshAll()
    }

    override fun onPause() {
        try {
            unregisterReceiver(timeTickReceiver)
        } catch (_: Exception) {
        }
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Tombol kembali tidak menutup launcher, melainkan kembali ke Beranda jika di OBD atau Apps Drawer
        if (currentScreen != AppScreen.HOME) {
            setScreen(AppScreen.HOME)
        }
    }

    private fun setScreen(screen: AppScreen) {
        currentScreen = screen
        homeCockpitLayout.visibility = if (screen == AppScreen.HOME) View.VISIBLE else View.GONE
        obdDashboardLayout.visibility = if (screen == AppScreen.OBD) View.VISIBLE else View.GONE
        appsDrawerLayout.visibility = if (screen == AppScreen.APPS) View.VISIBLE else View.GONE

        if (screen == AppScreen.APPS && allApps.isEmpty()) {
            loadInstalledAppsAsync()
        }
        updateSidebarSelection()
    }

    private fun updateSidebarSelection() {
        for (i in sidebarItems.indices) {
            val isSelected = when (i) {
                0 -> currentScreen == AppScreen.HOME
                1 -> currentScreen == AppScreen.OBD
                5 -> currentScreen == AppScreen.APPS
                else -> false
            }
            sidebarItems[i].setSelectedState(isSelected, palette)
        }
    }

    // ==========================================
    // SIDEBAR NAVIGATION
    // ==========================================
    private fun buildSidebar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val padV = dp(6f)
            val padH = dp(4f)
            setPadding(padH, padV, padH, padV)
        }

        fun addItem(icon: VectorIconView.IconType, label: String, action: () -> Unit): SidebarItemView {
            val item = SidebarItemView(this, icon, label).apply {
                setOnClickListener { action() }
            }
            sidebarItems.add(item)
            bar.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            return item
        }

        addItem(VectorIconView.IconType.HOME, "Beranda") {
            setScreen(AppScreen.HOME)
        }
        addItem(VectorIconView.IconType.CAR, "OBD") {
            setScreen(AppScreen.OBD)
        }
        addItem(VectorIconView.IconType.MAPS, "Peta") {
            launchPackageOrMarket(listOf("com.google.android.apps.maps", "com.waze"), "Google Maps")
        }
        addItem(VectorIconView.IconType.MUSIC, "Musik") {
            launchPackageOrMarket(
                listOf(
                    "app.revanced.android.apps.youtube.music",
                    "com.google.android.apps.youtube.music",
                    "com.spotify.music",
                    "com.android.music"
                ),
                "YouTube Music"
            )
        }
        addItem(VectorIconView.IconType.YOUTUBE, "YouTube") {
            launchPackageOrMarket(
                listOf(
                    "app.revanced.android.youtube",
                    "com.google.android.youtube"
                ),
                "YouTube"
            )
        }
        addItem(VectorIconView.IconType.APPS_GRID, "Aplikasi") {
            setScreen(AppScreen.APPS)
        }
        themeSidebarItem = addItem(VectorIconView.IconType.THEME_MODE, themeModeLabel()) {
            cycleThemeMode()
        }

        return bar
    }

    // ==========================================
    // BERANDA (HOME COCKPIT)
    // ==========================================
    private fun buildHomeCockpit(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val leftColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        clockSpeedCard = buildClockAndSpeedCard()
        prayerQuoteCard = buildPrayerAndQuoteCard()

        leftColumn.addView(
            clockSpeedCard,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.43f).apply {
                bottomMargin = dp(10f)
            }
        )
        leftColumn.addView(
            prayerQuoteCard,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.57f)
        )
        row.addView(
            leftColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.46f).apply {
                rightMargin = dp(10f)
            }
        )

        val rightColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        mapOuterCard = buildMapAndShareCard()
        val slidersRow = buildSlidersRow()

        rightColumn.addView(
            mapOuterCard,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                bottomMargin = dp(10f)
            }
        )
        rightColumn.addView(
            slidersRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64f))
        )
        row.addView(
            rightColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.54f)
        )

        return row
    }

    private fun buildClockAndSpeedCard(): GlassCardView {
        val card = GlassCardView(this)
        val pad = dp(12f)
        card.setPadding(pad, pad, pad, pad)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        clockTimeTv = TextView(this).apply {
            textSize = 38f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = -0.03f
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }
        clockDateTv = TextView(this).apply {
            textSize = 13.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        val pillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        themeStatusTv = TextView(this).apply {
            textSize = 10.5f
            val pH = dp(7f)
            val pV = dp(3f)
            setPadding(pH, pV, pH, pV)
        }
        launcherStatusBtn = TextView(this).apply {
            textSize = 10.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            val pH = dp(7f)
            val pV = dp(3f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                requestSetDefaultLauncher()
            }
        }
        pillRow.addView(themeStatusTv)
        pillRow.addView(launcherStatusBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(6f)
        })

        leftCol.addView(clockTimeTv)
        leftCol.addView(clockDateTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(1f)
            bottomMargin = dp(6f)
        })
        leftCol.addView(pillRow)

        speedWell = FrameLayout(this)
        speedGauge = SpeedGaugeView(this)
        speedWell.addView(speedGauge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        row.addView(leftCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f).apply {
            rightMargin = dp(8f)
        })
        row.addView(speedWell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.85f))

        card.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return card
    }

    private fun buildPrayerAndQuoteCard(): GlassCardView {
        val card = GlassCardView(this)
        val pad = dp(10f)
        card.setPadding(pad, pad, pad, pad)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prayerTitleTv = TextView(this).apply {
            text = "Jadwal Sholat"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        prayerCountdownTv = TextView(this).apply {
            textSize = 11.5f
            gravity = Gravity.END
            fontFeatureSettings = "tnum"
        }
        headerRow.addView(prayerTitleTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(prayerCountdownTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(headerRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6f)
        })

        val scheduleStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val labels = listOf("Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")
        for (i in labels.indices) {
            val slot = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2f), dp(4f), dp(2f), dp(4f))
            }
            val nameTv = TextView(this).apply {
                text = labels[i]
                textSize = 10f
                gravity = Gravity.CENTER
            }
            val timeTv = TextView(this).apply {
                text = "--:--"
                textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                fontFeatureSettings = "tnum"
                gravity = Gravity.CENTER
            }
            slot.addView(nameTv)
            slot.addView(timeTv)
            prayerSlotBoxes.add(slot)
            prayerSlotNames.add(nameTv)
            prayerSlotTimes.add(timeTv)

            scheduleStrip.addView(slot, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i < labels.lastIndex) rightMargin = dp(4f)
            })
        }
        col.addView(scheduleStrip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6f)
        })

        quoteBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val p = dp(6f)
            setPadding(p, p, p, p)
            setOnClickListener {
                quoteIndex = (quoteIndex + 1) % QuoteRepository.count()
                getSharedPreferences("haraya_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("quote_idx", quoteIndex).apply()
                refreshQuote()
            }
        }

        quoteArabicTv = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        quoteTranslationTv = TextView(this).apply {
            textSize = 10.5f
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        quoteHintTv = TextView(this).apply {
            textSize = 9.5f
            gravity = Gravity.CENTER
        }

        quoteBox.addView(quoteArabicTv)
        quoteBox.addView(quoteTranslationTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(1f)
        })
        quoteBox.addView(quoteHintTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(1f)
        })

        col.addView(quoteBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        card.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return card
    }

    private fun buildMapAndShareCard(): GlassCardView {
        val card = GlassCardView(this)
        val pad = dp(8f)
        card.setPadding(pad, pad, pad, pad)

        mapViewport = FrameLayout(this)
        GlassTheme.clipRound(this, mapViewport, 18f)

        mapCanvas = MapCanvasView(this).apply {
            setOnClickListener {
                launchPackageOrMarket(listOf("com.google.android.apps.maps", "com.waze"), "Google Maps")
            }
        }
        mapViewport.addView(mapCanvas, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val topOverlayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        locOverlayBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pH = dp(10f)
            val pV = dp(6f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                requestLocationPermissionIfNeeded()
            }
        }
        locPinIcon = VectorIconView(this, VectorIconView.IconType.PIN)
        locOverlayBox.addView(locPinIcon, LinearLayout.LayoutParams(dp(18f), dp(18f)).apply {
            rightMargin = dp(6f)
        })

        val locTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        locTitleTv = TextView(this).apply {
            text = "Mencari Lokasi..."
            textSize = 13.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        locSubTv = TextView(this).apply {
            text = "GPS Terhubung"
            textSize = 10.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        locTextCol.addView(locTitleTv)
        locTextCol.addView(locSubTv)
        locOverlayBox.addView(locTextCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        topOverlayRow.addView(
            locOverlayBox,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8f)
            }
        )

        shareCtaBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pH = dp(12f)
            val pV = dp(8f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                openGoogleMapsLocationSharing()
            }
        }
        shareCtaIcon = VectorIconView(this, VectorIconView.IconType.SHARE)
        shareCtaTv = TextView(this).apply {
            text = "Bagikan Lokasi Live"
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        shareCtaBtn.addView(shareCtaIcon, LinearLayout.LayoutParams(dp(16f), dp(16f)).apply {
            rightMargin = dp(6f)
        })
        shareCtaBtn.addView(shareCtaTv)

        topOverlayRow.addView(
            shareCtaBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        mapViewport.addView(
            topOverlayRow,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
                leftMargin = dp(10f)
                rightMargin = dp(10f)
                topMargin = dp(10f)
            }
        )

        navFooterBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pH = dp(12f)
            val pV = dp(6f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                launchPackageOrMarket(listOf("com.google.android.apps.maps", "com.waze"), "Google Maps")
            }
        }
        navFooterIcon = VectorIconView(this, VectorIconView.IconType.MAPS)
        navFooterTv = TextView(this).apply {
            text = "Ketuk area peta untuk membuka navigasi Google Maps"
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        navFooterBox.addView(navFooterIcon, LinearLayout.LayoutParams(dp(16f), dp(16f)).apply {
            rightMargin = dp(6f)
        })
        navFooterBox.addView(navFooterTv)

        mapViewport.addView(
            navFooterBox,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                leftMargin = dp(10f)
                rightMargin = dp(10f)
                bottomMargin = dp(10f)
            }
        )

        card.addView(mapViewport, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return card
    }

    private fun buildSlidersRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        volumeSlider = TactileSliderCard(
            this,
            VectorIconView.IconType.VOLUME,
            "Volume Media",
            curVol.toFloat() / maxVol.toFloat()
        ) { frac ->
            val target = (frac * maxVol).roundToInt().coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }

        brightnessSlider = TactileSliderCard(
            this,
            VectorIconView.IconType.BRIGHTNESS,
            "Kecerahan",
            0.75f
        ) { frac ->
            val lp = window.attributes
            lp.screenBrightness = frac.coerceIn(0.08f, 1.0f)
            window.attributes = lp
        }

        row.addView(volumeSlider, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            rightMargin = dp(10f)
        })
        row.addView(brightnessSlider, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        return row
    }

    // ==========================================
    // DEDICATED OBD COCKPIT DASHBOARD
    // ==========================================
    private fun buildObdDashboard(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        obdClusterCard = GlassCardView(this)
        val pad = dp(12f)
        obdClusterCard.setPadding(pad, pad, pad, pad)

        val clusterCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val clusterHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        obdClusterTitleTv = TextView(this).apply {
            text = "Cluster Instrumen Kendaraan"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        obdGearIndicatorTv = TextView(this).apply {
            text = "P   R   N   [ D ]"
            textSize = 11.5f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val pH = dp(8f)
            val pV = dp(3f)
            setPadding(pH, pV, pH, pV)
        }
        clusterHeader.addView(obdClusterTitleTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        clusterHeader.addView(obdGearIndicatorTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        clusterCol.addView(clusterHeader, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8f)
        })

        val gaugesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        obdRpmGauge = RpmGaugeView(this).apply {
            setRpm(850)
        }
        obdSpeedGauge = SpeedGaugeView(this)

        val rpmWell = FrameLayout(this)
        rpmWell.addView(obdRpmGauge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val speedWellObd = FrameLayout(this)
        speedWellObd.addView(obdSpeedGauge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        gaugesRow.addView(rpmWell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            rightMargin = dp(8f)
        })
        gaugesRow.addView(speedWellObd, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        clusterCol.addView(gaugesRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(8f)
        })

        obdClusterFooterTv = TextView(this).apply {
            text = "Transmisi: Otomatis • Suhu Oli: 89°C • Sistem Pengapian: ON"
            textSize = 10.5f
            gravity = Gravity.CENTER
        }
        clusterCol.addView(obdClusterFooterTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        obdClusterCard.addView(clusterCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        row.addView(
            obdClusterCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.48f).apply {
                rightMargin = dp(10f)
            }
        )

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        obdSensorsCard = GlassCardView(this)
        obdSensorsCard.setPadding(pad, pad, pad, pad)
        val sensorsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        obdSensorsTitleTv = TextView(this).apply {
            text = "Sensor Mesin Real-Time (ECU)"
            textSize = 13.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        sensorsCol.addView(obdSensorsTitleTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8f)
        })

        val sensorsGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val sensorData = listOf(
            listOf(Triple("Suhu Radiator", "88°C", "Optimal"), Triple("Tegangan Aki", "14.2 V", "Alternator"), Triple("Beban Mesin", "22%", "Ringan")),
            listOf(Triple("Konsumsi BBM", "12.8 km/L", "Rata-rata"), Triple("Suhu Intake", "32°C", "Normal"), Triple("Bukaan Gas", "14%", "Throttle"))
        )

        for (rowIdx in sensorData.indices) {
            val rLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val list = sensorData[rowIdx]
            for (colIdx in list.indices) {
                val spec = list[colIdx]
                val capsule = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    val p = dp(6f)
                    setPadding(p, p, p, p)
                }
                val labelTv = TextView(this).apply {
                    text = spec.first
                    textSize = 10f
                    gravity = Gravity.CENTER
                }
                val valTv = TextView(this).apply {
                    text = spec.second
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    fontFeatureSettings = "tnum"
                    gravity = Gravity.CENTER
                }
                val subTv = TextView(this).apply {
                    text = spec.third
                    textSize = 9.5f
                    gravity = Gravity.CENTER
                }
                capsule.addView(labelTv)
                capsule.addView(valTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(1f)
                    bottomMargin = dp(1f)
                })
                capsule.addView(subTv)

                obdSensorCapsules.add(capsule)
                obdSensorLabels.add(labelTv)
                obdSensorValues.add(valTv)
                obdSensorSubs.add(subTv)

                rLayout.addView(capsule, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (colIdx < list.lastIndex) rightMargin = dp(6f)
                })
            }
            sensorsGrid.addView(rLayout, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                if (rowIdx < sensorData.lastIndex) bottomMargin = dp(6f)
            })
        }
        sensorsCol.addView(sensorsGrid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        obdSensorsCard.addView(sensorsCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        rightCol.addView(
            obdSensorsCard,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.62f).apply {
                bottomMargin = dp(10f)
            }
        )

        obdDiagCard = GlassCardView(this)
        obdDiagCard.setPadding(pad, pad, pad, pad)
        val diagCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val diagStatusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        obdBluetoothStatusTv = TextView(this).apply {
            text = "Bluetooth ELM327: Siap Terhubung"
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        obdDtcStatusTv = TextView(this).apply {
            text = "Check Engine: Aman (0 DTC)"
            textSize = 11.5f
            gravity = Gravity.END
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        diagStatusRow.addView(obdBluetoothStatusTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        diagStatusRow.addView(obdDtcStatusTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        diagCol.addView(diagStatusRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8f)
        })

        val diagActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        obdScanDtcBtn = TextView(this).apply {
            text = "Pindai Kode Kerusakan (DTC)"
            textSize = 11f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            val pH = dp(10f)
            val pV = dp(6f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Memindai ECU... Tidak ada kode kerusakan DTC terdeteksi.", Toast.LENGTH_SHORT).show()
            }
        }
        obdBluetoothSettingsBtn = TextView(this).apply {
            text = "Hubungkan Dongle OBD"
            textSize = 11f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            val pH = dp(10f)
            val pV = dp(6f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                runCatching {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }.onFailure {
                    Toast.makeText(this@MainActivity, "Buka Pengaturan HP > Bluetooth untuk menghubungkan ELM327", Toast.LENGTH_SHORT).show()
                }
            }
        }
        obdBackHomeBtn = TextView(this).apply {
            text = "Kembali ke Beranda"
            textSize = 11f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            val pH = dp(10f)
            val pV = dp(6f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                setScreen(AppScreen.HOME)
            }
        }

        diagActionRow.addView(obdScanDtcBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
            rightMargin = dp(6f)
        })
        diagActionRow.addView(obdBluetoothSettingsBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6f)
        })
        diagActionRow.addView(obdBackHomeBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
        diagCol.addView(diagActionRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        obdDiagCard.addView(diagCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        rightCol.addView(
            obdDiagCard,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.38f)
        )

        row.addView(
            rightCol,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.52f)
        )

        return row
    }

    // ==========================================
    // DEDICATED APPS DRAWER (GRID LAUNCHER)
    // ==========================================
    private fun buildAppsDrawer(): GlassCardView {
        val card = GlassCardView(this)
        val pad = dp(12f)
        card.setPadding(pad, pad, pad, pad)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Header Row: Kotak Pencarian + Hitungan Aplikasi + Tombol Kembali
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        appSearchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pH = dp(12f)
            val pV = dp(5f)
            setPadding(pH, pV, pH, pV)
        }
        appSearchIcon = VectorIconView(this, VectorIconView.IconType.SEARCH)
        appSearchBox.addView(appSearchIcon, LinearLayout.LayoutParams(dp(17f), dp(17f)).apply {
            rightMargin = dp(8f)
        })

        appSearchEt = EditText(this).apply {
            hint = "Cari dari semua aplikasi..."
            textSize = 12.5f
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    filterApps(s?.toString().orEmpty())
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        appSearchBox.addView(appSearchEt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        headerRow.addView(appSearchBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(10f)
        })

        appCountTv = TextView(this).apply {
            text = "0 Aplikasi"
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            val pH = dp(10f)
            val pV = dp(5f)
            setPadding(pH, pV, pH, pV)
        }
        headerRow.addView(appCountTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(8f)
        })

        appBackBtn = TextView(this).apply {
            text = "Kembali ke Beranda"
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            val pH = dp(12f)
            val pV = dp(5f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                setScreen(AppScreen.HOME)
            }
        }
        headerRow.addView(appBackBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        col.addView(headerRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10f)
        })

        // Grid Area
        val gridContainer = FrameLayout(this)
        appsGridView = GridView(this).apply {
            numColumns = 6
            horizontalSpacing = dp(8f)
            verticalSpacing = dp(10f)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            isVerticalScrollBarEnabled = true
            clipToPadding = false
            val p = dp(4f)
            setPadding(p, p, p, p)
        }
        appsAdapter = AppsGridAdapter()
        appsGridView.adapter = appsAdapter

        appsEmptyTv = TextView(this).apply {
            text = "Tidak ada aplikasi yang cocok."
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        gridContainer.addView(appsGridView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        gridContainer.addView(appsEmptyTv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        col.addView(gridContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        card.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        return card
    }

    private inner class AppsGridAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredApps.size
        override fun getItem(position: Int): AppEntry = filteredApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val entry = getItem(position)
            val container = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val p = dp(6f)
                setPadding(p, p, p, p)

                val iconIv = ImageView(this@MainActivity).apply {
                    id = 1001
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                addView(iconIv, LinearLayout.LayoutParams(dp(44f), dp(44f)))

                val labelTv = TextView(this@MainActivity).apply {
                    id = 1002
                    textSize = 10.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                addView(labelTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4f)
                })
            }

            GlassTheme.makePressable(this@MainActivity, container, palette, 12f)
            container.background = null

            val iconIv = container.findViewById<ImageView>(1001)
            val labelTv = container.findViewById<TextView>(1002)

            labelTv.text = entry.label
            labelTv.setTextColor(palette.textPrimary)

            iconIv.setImageDrawable(entry.resolveInfo.loadIcon(packageManager))

            container.setOnClickListener {
                packageManager.getLaunchIntentForPackage(entry.packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                }
            }

            return container
        }
    }

    private fun loadInstalledAppsAsync() {
        Thread {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }

            val list = resolveInfos.map {
                AppEntry(
                    label = it.loadLabel(packageManager).toString(),
                    packageName = it.activityInfo.packageName,
                    resolveInfo = it
                )
            }

            runOnUiThread {
                allApps.clear()
                allApps.addAll(list)
                filterApps(appSearchEt.text?.toString().orEmpty())
            }
        }.start()
    }

    private fun filterApps(query: String) {
        val q = query.trim().lowercase(Locale.getDefault())
        filteredApps.clear()
        if (q.isEmpty()) {
            filteredApps.addAll(allApps)
        } else {
            for (app in allApps) {
                if (app.label.lowercase(Locale.getDefault()).contains(q) ||
                    app.packageName.lowercase(Locale.getDefault()).contains(q)
                ) {
                    filteredApps.add(app)
                }
            }
        }
        appCountTv.text = "${filteredApps.size} Aplikasi"
        appsEmptyTv.visibility = if (filteredApps.isEmpty()) View.VISIBLE else View.GONE
        appsAdapter.notifyDataSetChanged()
    }

    // ==========================================
    // THEME & REFRESH LOGIC
    // ==========================================
    private fun refreshAll() {
        val themeRes = themeResolver.resolve(themeMode, currentLat, currentLon)
        palette = if (themeRes.isDark) GlassTheme.Dark else GlassTheme.Light

        // 1. Latar Sutra & Sidebar
        silkBackground.setThemeMode(palette.isDark)
        sidebarLayout.background = GlassTheme.sidebarDrawable(this, palette)
        updateSidebarSelection()

        // 2. Beranda: Jam & Speedo
        clockSpeedCard.applyTheme(palette)
        clockTimeTv.setTextColor(palette.textPrimary)
        clockDateTv.setTextColor(palette.textSecondary)
        themeStatusTv.text = if (themeMode == ThemeMode.AUTO) {
            if (themeRes.isDark) "Otomatis: Gelap" else "Otomatis: Terang"
        } else {
            themeModeLabel()
        }
        themeStatusTv.setTextColor(palette.selectFg)
        themeStatusTv.background = GlassTheme.selectDrawable(this, palette, 10f)
        updateLauncherStatus()
        speedWell.background = GlassTheme.innerDrawable(this, palette, 16f)
        speedGauge.applyTheme(palette)

        // 3. Beranda: Sholat & Ayat
        prayerQuoteCard.applyTheme(palette)
        prayerTitleTv.setTextColor(palette.textPrimary)
        prayerCountdownTv.setTextColor(palette.accentLight.takeIf { palette.isDark } ?: palette.accent)
        quoteBox.background = GlassTheme.innerDrawable(this, palette, 14f)
        GlassTheme.makePressable(this, quoteBox, palette, 14f)

        // 4. Beranda: Peta & Share
        mapOuterCard.applyTheme(palette)
        mapCanvas.applyTheme(palette)
        GlassTheme.makePressable(this, mapCanvas, palette, 18f)

        locOverlayBox.background = GlassTheme.overDrawable(this, palette, 14f)
        GlassTheme.makePressable(this, locOverlayBox, palette, 14f)
        locPinIcon.setTint(palette.accent)
        locTitleTv.setTextColor(palette.textPrimary)
        locSubTv.setTextColor(palette.textSecondary)

        shareCtaBtn.background = GlassTheme.accentButtonDrawable(this, palette, 14f)
        GlassTheme.makePressable(this, shareCtaBtn, palette, 14f)
        shareCtaIcon.setTint(if (palette.isDark) 0xFF0D1222.toInt() else 0xFFFFFFFF.toInt())
        shareCtaTv.setTextColor(if (palette.isDark) 0xFF0D1222.toInt() else 0xFFFFFFFF.toInt())

        navFooterBox.background = GlassTheme.overDrawable(this, palette, 14f)
        GlassTheme.makePressable(this, navFooterBox, palette, 14f)
        navFooterIcon.setTint(palette.accent)
        navFooterTv.setTextColor(palette.textPrimary)

        // 5. Beranda: Slider
        volumeSlider.applyTheme(palette)
        brightnessSlider.applyTheme(palette)

        // 6. OBD Dashboard
        obdClusterCard.applyTheme(palette)
        obdClusterTitleTv.setTextColor(palette.textPrimary)
        obdGearIndicatorTv.setTextColor(palette.selectFg)
        obdGearIndicatorTv.background = GlassTheme.selectDrawable(this, palette, 8f)
        obdRpmGauge.applyTheme(palette)
        obdSpeedGauge.applyTheme(palette)
        obdClusterFooterTv.setTextColor(palette.textSecondary)

        obdSensorsCard.applyTheme(palette)
        obdSensorsTitleTv.setTextColor(palette.textPrimary)
        for (i in obdSensorCapsules.indices) {
            obdSensorCapsules[i].background = GlassTheme.innerDrawable(this, palette, 12f)
            obdSensorLabels[i].setTextColor(palette.textSecondary)
            obdSensorValues[i].setTextColor(palette.textPrimary)
            obdSensorSubs[i].setTextColor(palette.textMuted)
        }

        obdDiagCard.applyTheme(palette)
        obdBluetoothStatusTv.setTextColor(palette.textSecondary)
        obdDtcStatusTv.setTextColor(if (palette.isDark) 0xFF4ADE80.toInt() else 0xFF16A34A.toInt())

        obdScanDtcBtn.background = GlassTheme.accentButtonDrawable(this, palette, 10f)
        GlassTheme.makePressable(this, obdScanDtcBtn, palette, 10f)
        obdScanDtcBtn.setTextColor(if (palette.isDark) 0xFF0D1222.toInt() else 0xFFFFFFFF.toInt())

        obdBluetoothSettingsBtn.background = GlassTheme.innerDrawable(this, palette, 10f)
        GlassTheme.makePressable(this, obdBluetoothSettingsBtn, palette, 10f)
        obdBluetoothSettingsBtn.setTextColor(palette.textPrimary)

        obdBackHomeBtn.background = GlassTheme.innerDrawable(this, palette, 10f)
        GlassTheme.makePressable(this, obdBackHomeBtn, palette, 10f)
        obdBackHomeBtn.setTextColor(palette.textSecondary)

        // 7. Apps Drawer
        appsDrawerLayout.applyTheme(palette)
        appSearchBox.background = GlassTheme.innerDrawable(this, palette, 12f)
        appSearchIcon.setTint(palette.accent)
        appSearchEt.setTextColor(palette.textPrimary)
        appSearchEt.setHintTextColor(palette.textMuted)
        appCountTv.setTextColor(palette.selectFg)
        appCountTv.background = GlassTheme.selectDrawable(this, palette, 10f)
        appBackBtn.setTextColor(palette.textSecondary)
        appBackBtn.background = GlassTheme.innerDrawable(this, palette, 10f)
        GlassTheme.makePressable(this, appBackBtn, palette, 10f)
        appsEmptyTv.setTextColor(palette.textMuted)
        appsAdapter.notifyDataSetChanged()

        refreshClockAndSchedule()
        refreshQuote()
    }

    private fun refreshClockAndSchedule() {
        val now = Date()
        val timeFmt = SimpleDateFormat("HH:mm", Locale("id", "ID"))
        val dateFmt = SimpleDateFormat("EEEE, d MMM yyyy", Locale("id", "ID"))
        clockTimeTv.text = timeFmt.format(now)
        clockDateTv.text = dateFmt.format(now)

        val prayers = PrayerCalc.calculate(currentLat, currentLon)
        val hrs = prayers.nextPrayerRemainingMinutes / 60
        val mins = prayers.nextPrayerRemainingMinutes % 60
        val countdownStr = if (hrs > 0) "${hrs}j ${mins}m" else "${mins}m"
        prayerCountdownTv.text = "${prayers.nextPrayerName} ${prayers.nextPrayerTime} • ${countdownStr} lagi"

        val timesList = listOf(prayers.subuh, prayers.dzuhur, prayers.ashar, prayers.maghrib, prayers.isya)
        val namesList = listOf("Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")

        for (i in 0 until 5) {
            prayerSlotTimes[i].text = timesList[i]
            val isNext = namesList[i] == prayers.nextPrayerName
            if (isNext) {
                prayerSlotBoxes[i].background = GlassTheme.selectDrawable(this, palette, 10f)
                prayerSlotNames[i].setTextColor(palette.selectFg)
                prayerSlotTimes[i].setTextColor(palette.selectFg)
            } else {
                prayerSlotBoxes[i].background = GlassTheme.innerDrawable(this, palette, 10f)
                prayerSlotNames[i].setTextColor(palette.textSecondary)
                prayerSlotTimes[i].setTextColor(palette.textPrimary)
            }
        }
    }

    private fun refreshQuote() {
        val quote = QuoteRepository.getQuote(quoteIndex)
        quoteArabicTv.text = quote.arabic
        quoteArabicTv.setTextColor(palette.textPrimary)
        quoteTranslationTv.text = "\"${quote.translation}\" (${quote.reference})"
        quoteTranslationTv.setTextColor(palette.textSecondary)
        quoteHintTv.text = "Ketuk kutipan untuk ayat berikutnya (${quoteIndex + 1}/${QuoteRepository.count()})"
        quoteHintTv.setTextColor(palette.textMuted)
    }

    // ==========================================
    // SYSTEM INTENTS & PERMISSIONS
    // ==========================================
    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun updateLauncherStatus() {
        val isDefault = isDefaultLauncher()
        if (isDefault) {
            launcherStatusBtn.text = "Beranda Default"
            launcherStatusBtn.setTextColor(palette.selectFg)
            launcherStatusBtn.background = GlassTheme.selectDrawable(this, palette, 10f)
        } else {
            launcherStatusBtn.text = "Setel Beranda"
            launcherStatusBtn.setTextColor(palette.accentLight.takeIf { palette.isDark } ?: palette.accent)
            launcherStatusBtn.background = GlassTheme.innerDrawable(this, palette, 10f)
        }
        GlassTheme.makePressable(this, launcherStatusBtn, palette, 10f)
    }

    private fun requestSetDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    startActivity(roleIntent)
                    return
                }
            }
        }
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Buka Pengaturan HP > Aplikasi Default > Aplikasi Beranda > pilih Haraya Dock", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cycleThemeMode() {
        themeMode = when (themeMode) {
            ThemeMode.AUTO -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.AUTO
        }
        getSharedPreferences("haraya_prefs", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", themeMode.name).apply()
        themeSidebarItem.setLabel(themeModeLabel())
        refreshAll()
    }

    private fun themeModeLabel(): String = when (themeMode) {
        ThemeMode.AUTO -> "Otomatis"
        ThemeMode.DARK -> "Gelap"
        ThemeMode.LIGHT -> "Terang"
    }

    private fun openGoogleMapsLocationSharing() {
        try {
            val mapsShareIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://maps.google.com/maps?action=share")
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(mapsShareIntent)
        } catch (_: Exception) {
            val coordLink = String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", currentLat, currentLon)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Lokasi perjalanan saya saat ini: $coordLink")
            }
            startActivity(Intent.createChooser(sendIntent, "Bagikan Lokasi Live"))
        }
    }

    private fun launchPackageOrMarket(candidates: List<String>, label: String) {
        for (pkg in candidates) {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                return
            }
        }
        Toast.makeText(this, "$label belum terpasang di perangkat ini", Toast.LENGTH_SHORT).show()
    }

    private fun requestLocationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fineGranted || !coarseGranted) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    101
                )
                return
            }
        }
        startLocationUpdatesIfPermitted()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdatesIfPermitted()
        }
    }

    private fun startLocationUpdatesIfPermitted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            locTitleTv.text = "Izin Lokasi Belum Aktif"
            locSubTv.text = "Ketuk untuk izinkan GPS & Cuaca"
            return
        }

        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var bestLoc: Location? = null
            for (provider in providers) {
                try {
                    val loc = lm.getLastKnownLocation(provider) ?: continue
                    if (bestLoc == null || loc.time > bestLoc.time) {
                        bestLoc = loc
                    }
                } catch (_: Exception) {
                }
            }

            if (bestLoc != null) {
                onLocationChanged(bestLoc)
            } else {
                locSubTv.text = "Mencari sinyal GPS/Jaringan..."
            }

            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5f, this)
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, this)
            }
        } catch (_: Exception) {
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLat = location.latitude
        currentLon = location.longitude

        currentKmh = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt() else 0
        speedGauge.setSpeed(currentKmh)
        obdSpeedGauge.setSpeed(currentKmh)

        val providerLabel = if (location.provider == LocationManager.GPS_PROVIDER) "GPS Akurat" else "Jaringan"
        locSubTv.text = String.format(Locale.US, "%.4f, %.4f • %s", currentLat, currentLon, providerLabel)

        reverseGeocodeAsync(currentLat, currentLon)
        refreshClockAndSchedule()
    }

    private fun reverseGeocodeAsync(lat: Double, lon: Double) {
        if (kotlin.math.abs(lat - lastGeocodedLat) < 0.005 && kotlin.math.abs(lon - lastGeocodedLon) < 0.005) {
            return
        }
        lastGeocodedLat = lat
        lastGeocodedLon = lon

        Thread {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(this, Locale("id", "ID"))
                    @Suppress("DEPRECATION")
                    val list = geocoder.getFromLocation(lat, lon, 1)
                    if (!list.isNullOrEmpty()) {
                        val addr = list[0]
                        val city = addr.subAdminArea ?: addr.locality ?: addr.adminArea ?: "Lokasi Terdeteksi"
                        val district = addr.locality ?: addr.subLocality ?: ""
                        val title = if (district.isNotEmpty() && district != city) "$district, $city" else city
                        runOnUiThread {
                            locTitleTv.text = title
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun dp(value: Float): Int = GlassTheme.dp(this, value)

    private inner class SidebarItemView(
        context: Context,
        iconType: VectorIconView.IconType,
        labelText: String
    ) : LinearLayout(context) {

        private val iconBox = FrameLayout(context)
        private val iconView = VectorIconView(context, iconType)
        private val labelTv = TextView(context).apply {
            text = labelText
            textSize = 9f
            gravity = Gravity.CENTER
            maxLines = 1
        }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            val p = dp(2f)
            setPadding(p, p, p, p)

            val boxSize = dp(26f)
            val iconSize = dp(16f)
            iconBox.addView(iconView, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            addView(iconBox, LayoutParams(boxSize, boxSize))
            addView(labelTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(1f)
            })
        }

        fun setLabel(newLabel: String) {
            labelTv.text = newLabel
        }

        fun setSelectedState(selected: Boolean, pal: GlassPalette) {
            GlassTheme.makePressable(context, this, pal, 14f)
            if (selected) {
                background = GlassTheme.selectDrawable(context, pal, 14f)
                iconBox.background = null
                iconView.setTint(pal.selectFg)
                labelTv.setTextColor(pal.selectFg)
                labelTv.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            } else {
                background = null
                iconBox.background = GlassTheme.solidRound(context, pal.chip, 10f)
                iconView.setTint(pal.textSecondary)
                labelTv.setTextColor(pal.textSecondary)
                labelTv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }
    }
}
