package com.haraya.dock

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.haraya.dock.calc.PrayerCalc
import com.haraya.dock.data.AdhanPlayerManager
import com.haraya.dock.data.ObdConnectionManager
import com.haraya.dock.data.QuoteRepository
import com.haraya.dock.data.TripAndServiceManager
import com.haraya.dock.data.WeatherFetcher
import com.haraya.dock.service.FloatingDockService
import com.haraya.dock.service.NotificationCollectorService
import com.haraya.dock.theme.ThemeMode
import com.haraya.dock.theme.ThemeResolver
import com.haraya.dock.ui.CarCockpitView
import com.haraya.dock.ui.HarayaDialog
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
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity(), LocationListener {

    companion object {
        const val EXTRA_TARGET_SCREEN = "extra_target_screen"
        const val EXTRA_TARGET_HOME = "HOME"
        const val EXTRA_TARGET_MAP = "MAP"
        const val EXTRA_TARGET_OBD = "OBD"
        const val EXTRA_TARGET_APPS = "APPS"

        private const val REQUEST_CODE_LOCATION = 101
        private const val REQUEST_CODE_BLUETOOTH = 102
        private const val REQUEST_CODE_PICK_CAR_IMAGE = 202
    }

    enum class AppScreen {
        HOME,
        MAP,
        OBD,
        APPS
    }

    enum class HomeStyle {
        CAR_COCKPIT,
        CLASSIC_SILK
    }

    data class AppEntry(
        val label: String,
        val packageName: String,
        val cachedIcon: Drawable
    )

    private val themeResolver = ThemeResolver()
    private var themeMode: ThemeMode = ThemeMode.AUTO
    private var homeStyle: HomeStyle = HomeStyle.CAR_COCKPIT
    private var palette: GlassPalette = GlassTheme.Dark
    private var currentScreen: AppScreen = AppScreen.HOME
    private var quoteIndex: Int = 0
    private var floatingDockEnabled: Boolean = true

    private lateinit var tripServiceManager: TripAndServiceManager
    private lateinit var adhanPlayerManager: AdhanPlayerManager
    private lateinit var obdConnectionManager: ObdConnectionManager
    private lateinit var weatherFetcher: WeatherFetcher

    // Location coordinates
    private var currentLat: Double = -6.1754
    private var currentLon: Double = 106.8272
    private var lastPrayerCalcLat: Double = -6.1754
    private var lastPrayerCalcLon: Double = 106.8272
    private var currentKmh: Int = 0
    private var lastGeocodedLat: Double = 0.0
    private var lastGeocodedLon: Double = 0.0
    private var currentStreetLabel: String = "Locating Street..."

    // Root & Backdrop
    private lateinit var rootFrame: FrameLayout
    private lateinit var silkBackground: SilkBackgroundView
    private lateinit var mainShell: LinearLayout
    private lateinit var contentFrame: FrameLayout
    private lateinit var sidebarLayout: LinearLayout
    private val sidebarItems = mutableListOf<SidebarItemView>()
    private lateinit var themeSidebarItem: SidebarItemView

    // Main Containers
    private lateinit var carCockpitView: CarCockpitView
    private lateinit var homeCockpitLayout: LinearLayout
    private lateinit var obdDashboardLayout: LinearLayout
    private lateinit var appsDrawerLayout: GlassCardView

    // === CLASSIC HOME COMPONENTS ===
    private lateinit var clockSpeedCard: GlassCardView
    private lateinit var clockTimeTv: TextView
    private lateinit var clockDateTv: TextView
    private lateinit var themeStatusTv: TextView
    private lateinit var launcherStatusBtn: TextView
    private lateinit var switchToCarModeBtn: TextView
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

    // === OBD DASHBOARD COMPONENTS ===
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

    // === APPS DRAWER GRID COMPONENTS ===
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
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                carCockpitView.updateGpsStatus(isGpsProviderActive())
                if (isGpsProviderActive()) {
                    startLocationUpdatesIfPermitted()
                }
            }
            refreshClockAndSchedule()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("haraya_prefs", Context.MODE_PRIVATE)
        val savedMode = prefs.getString("theme_mode", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name
        themeMode = runCatching { ThemeMode.valueOf(savedMode) }.getOrDefault(ThemeMode.AUTO)
        val savedStyle = prefs.getString("home_style", HomeStyle.CAR_COCKPIT.name) ?: HomeStyle.CAR_COCKPIT.name
        homeStyle = runCatching { HomeStyle.valueOf(savedStyle) }.getOrDefault(HomeStyle.CAR_COCKPIT)
        quoteIndex = prefs.getInt("quote_idx", 0)
        floatingDockEnabled = prefs.getBoolean("floating_dock_enabled", true)

        tripServiceManager = TripAndServiceManager(this)
        adhanPlayerManager = AdhanPlayerManager(this).apply {
            onPlaybackStateChanged = {
                runOnUiThread {
                    refreshClockAndSchedule()
                }
            }
        }
        obdConnectionManager = ObdConnectionManager(this).apply {
            onTelemetryUpdated = { telemetry ->
                runOnUiThread {
                    applyObdTelemetryToUi(telemetry)
                }
            }
        }
        weatherFetcher = WeatherFetcher(this)

        rootFrame = FrameLayout(this)
        silkBackground = SilkBackgroundView(this)
        rootFrame.addView(
            silkBackground,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // 1. CENTER VEHICLE COCKPIT VIEW (3 Columns + Top Bar + Bottom Dock + Embedded Stages)
        carCockpitView = CarCockpitView(
            context = this,
            tripServiceManager = tripServiceManager,
            adhanPlayerManager = adhanPlayerManager,
            onSwitchThemeMode = { targetMode ->
                themeMode = targetMode
                prefs.edit().putString("theme_mode", themeMode.name).apply()
                themeSidebarItem.setLabel(themeModeLabel())
                refreshAll()
            },
            onSwitchToClassicLayout = {
                setHomeStyle(HomeStyle.CLASSIC_SILK)
            },
            onSelectHomeStage = {
                setScreen(AppScreen.HOME)
            },
            onSelectMapStage = {
                setScreen(AppScreen.MAP)
            },
            onOpenExternalMaps = {
                launchPackageOrMarket(listOf("com.google.android.apps.maps", "com.waze"), "Google Maps")
            },
            onShareLiveLocation = {
                openGoogleMapsLocationSharing()
            },
            onGpsPillClicked = {
                handleGpsPillClick()
            },
            onOpenMusic = {
                launchPackageOrMarket(
                    listOf(
                        "app.revanced.android.apps.youtube.music",
                        "com.google.android.apps.youtube.music",
                        "com.spotify.music",
                        "com.android.music"
                    ),
                    "YouTube Music"
                )
            },
            onOpenYouTube = {
                launchPackageOrMarket(
                    listOf(
                        "app.revanced.android.youtube",
                        "com.google.android.youtube"
                    ),
                    "YouTube"
                )
            },
            onOpenPhone = {
                openPhoneDialer()
            },
            onOpenObdScreen = {
                setScreen(AppScreen.OBD)
            },
            onOpenObdSettingsModal = {
                openObdConfigurationModal()
            },
            onOpenSettingsDialog = {
                showCockpitSettingsDialog()
            },
            onOpenAppsScreen = {
                setScreen(AppScreen.APPS)
            }
        )
        carCockpitView.updateWeather(weatherFetcher.getCachedWeather())

        // 2. CLASSIC SHELL + OBD & APPS DRAWER VIEWS
        mainShell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val pad = dp(12f)
            setPadding(pad, pad, pad, pad)
        }

        sidebarLayout = buildSidebar()
        mainShell.addView(
            sidebarLayout,
            LinearLayout.LayoutParams(dp(76f), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                rightMargin = dp(10f)
            }
        )

        contentFrame = FrameLayout(this)

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
        rootFrame.addView(
            carCockpitView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(rootFrame)

        setScreen(AppScreen.HOME)
        handleIncomingDockIntent(intent)
        adhanPlayerManager.scheduleStartupTravelPrayerIfNeeded()
        QuoteRepository.loadCachedOrDefault(this)
        QuoteRepository.autoUpdateIfDue(this, intervalMinutes = 5) {
            refreshAll()
        }
        refreshAll()
        applyObdTelemetryToUi(obdConnectionManager.getCurrentTelemetry())
        carCockpitView.post {
            carCockpitView.playOpeningIntroAnimation()
        }
        requestLocationPermissionIfNeeded()
        loadInstalledAppsAsync()
        weatherFetcher.fetchIfNeeded(currentLat, currentLon) { weather ->
            runOnUiThread {
                carCockpitView.updateWeather(weather)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDockIntent(intent)
    }

    private fun handleIncomingDockIntent(intent: Intent?) {
        val target = intent?.getStringExtra(EXTRA_TARGET_SCREEN) ?: return
        when (target) {
            EXTRA_TARGET_HOME -> setScreen(AppScreen.HOME)
            EXTRA_TARGET_MAP -> launchPackageOrMarket(listOf("com.google.android.apps.maps", "com.waze"), "Google Maps")
            EXTRA_TARGET_OBD -> setScreen(AppScreen.OBD)
            EXTRA_TARGET_APPS -> setScreen(AppScreen.APPS)
        }
        intent.removeExtra(EXTRA_TARGET_SCREEN)
    }

    override fun onResume() {
        super.onResume()
        FloatingDockService.hideOverlayInMainApp(this)
        registerReceiver(timeTickReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        })
        updateLauncherStatus()
        refreshAll()
    }

    override fun onPause() {
        try {
            unregisterReceiver(timeTickReceiver)
        } catch (_: Exception) {
        }
        if (floatingDockEnabled && FloatingDockService.canDrawOverlays(this)) {
            FloatingDockService.showOverlayWhenLeavingApp(this, currentKmh, palette.isDark)
        }
        super.onPause()
    }

    override fun onDestroy() {
        adhanPlayerManager.stopAdhanPlayback(notify = false)
        obdConnectionManager.disconnect()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentScreen != AppScreen.HOME) {
            setScreen(AppScreen.HOME)
        }
    }

    private fun setHomeStyle(style: HomeStyle) {
        homeStyle = style
        getSharedPreferences("haraya_prefs", Context.MODE_PRIVATE)
            .edit().putString("home_style", style.name).apply()
        setScreen(currentScreen)
        refreshAll()
    }

    private fun setScreen(screen: AppScreen) {
        currentScreen = screen

        if (homeStyle == HomeStyle.CAR_COCKPIT) {
            carCockpitView.visibility = View.VISIBLE
            mainShell.visibility = View.GONE
            silkBackground.visibility = View.GONE

            val stage = when (screen) {
                AppScreen.HOME -> CarCockpitView.CockpitStage.HOME
                AppScreen.MAP -> CarCockpitView.CockpitStage.MAP
                AppScreen.OBD -> CarCockpitView.CockpitStage.OBD
                AppScreen.APPS -> CarCockpitView.CockpitStage.APPS
            }
            carCockpitView.setCockpitStage(stage, obdDashboardLayout, appsDrawerLayout)
        } else {
            carCockpitView.visibility = View.GONE
            mainShell.visibility = View.VISIBLE
            silkBackground.visibility = View.VISIBLE

            if (obdDashboardLayout.parent !== contentFrame) {
                (obdDashboardLayout.parent as? ViewGroup)?.removeView(obdDashboardLayout)
                contentFrame.addView(
                    obdDashboardLayout,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }
            if (appsDrawerLayout.parent !== contentFrame) {
                (appsDrawerLayout.parent as? ViewGroup)?.removeView(appsDrawerLayout)
                contentFrame.addView(
                    appsDrawerLayout,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }

            homeCockpitLayout.visibility = if (screen == AppScreen.HOME || screen == AppScreen.MAP) View.VISIBLE else View.GONE
            obdDashboardLayout.visibility = if (screen == AppScreen.OBD) View.VISIBLE else View.GONE
            appsDrawerLayout.visibility = if (screen == AppScreen.APPS) View.VISIBLE else View.GONE
        }

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
    // SIDEBAR NAVIGATION (CLASSIC MODE)
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

        addItem(VectorIconView.IconType.HOME, "Home") {
            setScreen(AppScreen.HOME)
        }
        addItem(VectorIconView.IconType.CAR, "OBD") {
            setScreen(AppScreen.OBD)
        }
        addItem(VectorIconView.IconType.MAPS, "Maps") {
            launchPackageOrMarket(listOf("com.google.android.apps.maps", "com.waze"), "Google Maps")
        }
        addItem(VectorIconView.IconType.MUSIC, "Music") {
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
        addItem(VectorIconView.IconType.APPS_GRID, "Apps") {
            setScreen(AppScreen.APPS)
        }
        themeSidebarItem = addItem(VectorIconView.IconType.THEME_MODE, themeModeLabel()) {
            cycleThemeMode()
        }

        return bar
    }

    // ==========================================
    // CLASSIC HOME COCKPIT
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
        switchToCarModeBtn = TextView(this).apply {
            text = "Cockpit Mode"
            textSize = 10.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            val pH = dp(7f)
            val pV = dp(3f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                setHomeStyle(HomeStyle.CAR_COCKPIT)
            }
        }
        pillRow.addView(themeStatusTv)
        pillRow.addView(launcherStatusBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(6f)
        })
        pillRow.addView(switchToCarModeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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
            text = "Prayer Times"
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
        val labels = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
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
                QuoteRepository.fetchNextFromAlQuranApiAsync(this@MainActivity) {
                    refreshQuote()
                }
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
            text = "Locating..."
            textSize = 13.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        locSubTv = TextView(this).apply {
            text = "GPS Connected"
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
            text = "Share Live Location"
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
            text = "Tap map area to launch Google Maps navigation"
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
            "Media Volume",
            curVol.toFloat() / maxVol.toFloat()
        ) { frac ->
            val target = (frac * maxVol).roundToInt().coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }

        brightnessSlider = TactileSliderCard(
            this,
            VectorIconView.IconType.BRIGHTNESS,
            "Brightness",
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
            text = "Vehicle Instrument Cluster"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        obdGearIndicatorTv = TextView(this).apply {
            text = "[ P ]   R   N   D"
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
            text = "Transmission: Automatic • Oil Temp: 89°C • Ignition: ON"
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
            text = "Real-Time Engine Sensors (ECU)"
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
            listOf(Triple("Coolant Temp", "88°C", "Optimal"), Triple("Battery Voltage", "14.2 V", "Alternator"), Triple("Engine Load", "22%", "Light")),
            listOf(Triple("Fuel Economy", "12.8 km/L", "Average"), Triple("Intake Temp", "32°C", "Normal"), Triple("Throttle Pos", "14%", "Sensor"))
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
            text = "Bluetooth ELM327: Ready to Connect"
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        obdDtcStatusTv = TextView(this).apply {
            text = "Check Engine: OK (0 DTC)"
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
            text = "Scan Fault Codes (DTC)"
            textSize = 11f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            val pH = dp(10f)
            val pV = dp(6f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Scanning ECU (Mode 03)... 0 Fault Codes (DTC) detected.", Toast.LENGTH_SHORT).show()
            }
        }
        obdBluetoothSettingsBtn = TextView(this).apply {
            text = "OBD & Speedo Settings"
            textSize = 11f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            val pH = dp(10f)
            val pV = dp(6f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                openObdConfigurationModal()
            }
        }
        obdBackHomeBtn = TextView(this).apply {
            text = "Back to Home"
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

        diagActionRow.addView(obdScanDtcBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f).apply {
            rightMargin = dp(6f)
        })
        diagActionRow.addView(obdBluetoothSettingsBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f).apply {
            rightMargin = dp(6f)
        })
        diagActionRow.addView(obdBackHomeBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f))
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

    private fun applyObdTelemetryToUi(telemetry: ObdConnectionManager.ObdTelemetry) {
        currentKmh = telemetry.speedKmh
        speedGauge.setSpeed(telemetry.speedKmh)
        obdSpeedGauge.setSpeed(telemetry.speedKmh)
        obdRpmGauge.setRpm(telemetry.rpm.coerceAtLeast(if (telemetry.connected) 780 else 0))
        carCockpitView.updateObdTelemetry(telemetry)
        FloatingDockService.notifyStateUpdated(this, currentKmh, palette.isDark)

        val g = telemetry.gearLabel
        obdGearIndicatorTv.text = when {
            g.startsWith("D") -> "P   R   N   [ $g ]"
            g == "N" -> "P   R   [ N ]   D"
            g == "R" -> "P   [ R ]   N   D"
            else -> "[ P ]   R   N   D"
        }

        if (obdSensorValues.size >= 6) {
            obdSensorValues[0].text = "${telemetry.coolantTempC}°C"
            obdSensorValues[1].text = String.format(Locale.US, "%.1f V", telemetry.batteryVoltage)
            obdSensorValues[2].text = "${telemetry.engineLoadPct}%"
            obdSensorValues[4].text = "${telemetry.intakeTempC}°C"
            obdSensorValues[5].text = "${telemetry.throttlePct}%"
        }

        obdBluetoothStatusTv.text = if (telemetry.connected) {
            "● Connected: ${telemetry.deviceName} (${telemetry.sourceLabel})"
        } else {
            "Bluetooth ELM327: Disconnected (${telemetry.sourceLabel})"
        }
    }

    private fun openObdConfigurationModal() {
        obdConnectionManager.showObdSettingsModal(this, palette) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    ),
                    REQUEST_CODE_BLUETOOTH
                )
            }
        }
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
            hint = "Search installed apps..."
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
            text = "0 Apps"
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
            text = "Back to Home"
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
            text = "No matching applications found."
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

    private class AppGridViewHolder(
        val container: LinearLayout,
        val iconIv: ImageView,
        val labelTv: TextView
    )

    private inner class AppsGridAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredApps.size
        override fun getItem(position: Int): AppEntry = filteredApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val entry = getItem(position)
            val holder: AppGridViewHolder
            if (convertView == null) {
                val iconIv = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                val labelTv = TextView(this@MainActivity).apply {
                    textSize = 10.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                val container = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    val p = dp(6f)
                    setPadding(p, p, p, p)
                    addView(iconIv, LinearLayout.LayoutParams(dp(44f), dp(44f)))
                    addView(labelTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(4f)
                    })
                }
                GlassTheme.makePressable(this@MainActivity, container, palette, 12f)
                holder = AppGridViewHolder(container, iconIv, labelTv)
                container.tag = holder
            } else {
                holder = convertView.tag as AppGridViewHolder
            }

            holder.labelTv.text = entry.label
            holder.labelTv.setTextColor(palette.textPrimary)
            holder.iconIv.setImageDrawable(entry.cachedIcon)

            holder.container.setOnClickListener {
                packageManager.getLaunchIntentForPackage(entry.packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                }
            }

            return holder.container
        }
    }

    private fun renderCompactIcon(raw: Drawable, targetPx: Int): Drawable {
        if (raw is BitmapDrawable && raw.bitmap != null &&
            raw.bitmap.width <= targetPx && raw.bitmap.height <= targetPx
        ) {
            return raw
        }
        return runCatching {
            val bmp = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            raw.setBounds(0, 0, targetPx, targetPx)
            raw.draw(canvas)
            BitmapDrawable(resources, bmp)
        }.getOrDefault(raw)
    }

    private fun loadInstalledAppsAsync() {
        Thread {
            val iconPx = dp(46f)
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
                .filter { it.activityInfo.packageName != packageName }

            val list = resolveInfos.map {
                val lbl = it.loadLabel(packageManager).toString()
                val rawIcon = it.loadIcon(packageManager)
                AppEntry(
                    label = lbl,
                    packageName = it.activityInfo.packageName,
                    cachedIcon = renderCompactIcon(rawIcon, iconPx)
                )
            }.sortedBy { it.label.lowercase(Locale.getDefault()) }

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
        appCountTv.text = "${filteredApps.size} Apps"
        appsEmptyTv.visibility = if (filteredApps.isEmpty()) View.VISIBLE else View.GONE
        appsAdapter.notifyDataSetChanged()
    }

    // ==========================================
    // THEME & REFRESH LOGIC
    // ==========================================
    private fun refreshAll() {
        val themeRes = themeResolver.resolve(themeMode, currentLat, currentLon)
        palette = if (themeRes.isDark) GlassTheme.Dark else GlassTheme.Light

        // 1. Matte Backdrop & Sidebar
        rootFrame.setBackgroundColor(palette.windowBg)
        val showCarCockpit = (homeStyle == HomeStyle.CAR_COCKPIT)
        silkBackground.visibility = if (showCarCockpit) View.GONE else View.VISIBLE
        if (!showCarCockpit) {
            silkBackground.setThemeMode(palette.isDark)
        }
        sidebarLayout.background = GlassTheme.sidebarDrawable(this, palette)
        updateSidebarSelection()

        // 2. Classic Home: Clock & Speedo
        clockSpeedCard.applyTheme(palette)
        clockTimeTv.setTextColor(palette.textPrimary)
        clockDateTv.setTextColor(palette.textSecondary)
        themeStatusTv.text = if (themeMode == ThemeMode.AUTO) {
            if (themeRes.isDark) "Auto: Dark" else "Auto: Light"
        } else {
            themeModeLabel()
        }
        themeStatusTv.setTextColor(palette.selectFg)
        themeStatusTv.background = GlassTheme.selectDrawable(this, palette, 10f)
        updateLauncherStatus()
        switchToCarModeBtn.setTextColor(palette.textPrimary)
        switchToCarModeBtn.background = GlassTheme.innerDrawable(this, palette, 10f)
        GlassTheme.makePressable(this, switchToCarModeBtn, palette, 10f)
        speedWell.background = GlassTheme.innerDrawable(this, palette, 16f)
        speedGauge.applyTheme(palette)

        // 3. Classic Home: Prayer & Verse
        prayerQuoteCard.applyTheme(palette)
        prayerTitleTv.setTextColor(palette.textPrimary)
        prayerCountdownTv.setTextColor(palette.accentLight.takeIf { palette.isDark } ?: palette.accent)
        quoteBox.background = GlassTheme.innerDrawable(this, palette, 14f)
        GlassTheme.makePressable(this, quoteBox, palette, 14f)

        // 4. Classic Home: Map & Share
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

        // 5. Sliders
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

        // 8. Center Car Cockpit View
        carCockpitView.applyTheme(palette, themeMode, isGpsProviderActive())

        refreshClockAndSchedule()
        refreshQuote()
    }

    private fun refreshClockAndSchedule() {
        val now = Date()
        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
        val dateFmt = SimpleDateFormat("EEEE, d MMM yyyy", Locale.US)
        val clockStr = timeFmt.format(now)
        clockTimeTv.text = clockStr
        clockDateTv.text = dateFmt.format(now)

        val prayers = PrayerCalc.calculate(currentLat, currentLon)
        lastPrayerCalcLat = currentLat
        lastPrayerCalcLon = currentLon

        carCockpitView.updateGpsStatus(isGpsProviderActive())
        carCockpitView.updateClockAndPrayer(clockStr, prayers)
        carCockpitView.refreshTripAndServices()

        // Check if prayer time has arrived to trigger Adhan alert & audio
        adhanPlayerManager.checkAndTriggerIfPrayerTimeArrived(this, palette, clockStr, prayers)

        val hrs = prayers.nextPrayerRemainingMinutes / 60
        val mins = prayers.nextPrayerRemainingMinutes % 60
        val countdownStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
        prayerCountdownTv.text = "${prayers.nextPrayerName} ${prayers.nextPrayerTime} • in $countdownStr"

        val timesList = listOf(prayers.subuh, prayers.dzuhur, prayers.ashar, prayers.maghrib, prayers.isya)
        val namesList = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

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
        val quote = QuoteRepository.getCurrentQuote()
        quoteArabicTv.text = quote.arabic
        quoteArabicTv.setTextColor(palette.textPrimary)
        quoteTranslationTv.text = "\"${quote.translation}\" (${quote.reference})"
        quoteTranslationTv.setTextColor(palette.textSecondary)
        quoteHintTv.text = "Auto-updated via AlQuran Cloud • Tap for next verse"
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
            launcherStatusBtn.text = "Default Home"
            launcherStatusBtn.setTextColor(palette.selectFg)
            launcherStatusBtn.background = GlassTheme.selectDrawable(this, palette, 10f)
        } else {
            launcherStatusBtn.text = "Set as Home"
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
                Toast.makeText(this, "Open Android Settings > Default Apps > Home App > select Haraya Dock", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cycleThemeMode() {
        themeMode = when (themeMode) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.AUTO
            ThemeMode.AUTO -> ThemeMode.LIGHT
        }
        getSharedPreferences("haraya_prefs", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", themeMode.name).apply()
        themeSidebarItem.setLabel(themeModeLabel())
        refreshAll()
    }

    private fun themeModeLabel(): String = when (themeMode) {
        ThemeMode.AUTO -> "Auto"
        ThemeMode.DARK -> "Dark"
        ThemeMode.LIGHT -> "Light"
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
                putExtra(Intent.EXTRA_TEXT, "My live driving location: $coordLink")
            }
            startActivity(Intent.createChooser(sendIntent, "Share Live Location"))
        }
    }

    private fun openPhoneDialer() {
        val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in whatsappPackages) {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                return
            }
        }
        Toast.makeText(this, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(this, "$label is not installed on this device", Toast.LENGTH_SHORT).show()
    }

    private fun isGpsProviderActive(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return runCatching {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
    }

    private fun handleGpsPillClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissionIfNeeded()
            return
        }
        if (!isGpsProviderActive()) {
            runCatching {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }.onFailure {
                Toast.makeText(this, "Enable Location / GPS in Android quick settings", Toast.LENGTH_SHORT).show()
            }
        } else {
            startLocationUpdatesIfPermitted()
            Toast.makeText(this, "GPS Active (${String.format(Locale.US, "%.4f, %.4f", currentLat, currentLon)})", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCockpitSettingsDialog() {
        val notifOk = NotificationCollectorService.isNotificationAccessGranted(this)
        val launcherOk = isDefaultLauncher()
        val gpsOk = isGpsProviderActive()
        val overlayPermOk = FloatingDockService.canDrawOverlays(this)
        val floatingActive = overlayPermOk && floatingDockEnabled
        val adhanEnabled = adhanPlayerManager.isAdhanAlertEnabled()
        val obdConnected = obdConnectionManager.getCurrentTelemetry().connected

        HarayaDialog.showCustomModal(
            context = this,
            palette = palette,
            title = "Haraya Dock Settings",
            subtitle = "Configure OBD-II telemetry, vehicle identity, Adhan alerts, & embedded dock",
            contentBuilder = { container, dlg ->
                val rows = listOf(
                    HarayaDialog.buildOptionRow(
                        context = this,
                        palette = palette,
                        title = "OBD-II Bluetooth & Speedometer Source",
                        subtitle = "${obdConnectionManager.getSpeedSourceLabel()} • Adapter: ${obdConnectionManager.getSavedDeviceName()}",
                        badgeText = if (obdConnected) "OBD Active ✓" else "Configure",
                        badgePositive = obdConnected
                    ) {
                        dlg.dismiss()
                        openObdConfigurationModal()
                    },
                    HarayaDialog.buildOptionRow(
                        context = this,
                        palette = palette,
                        title = "Vehicle Identity & Custom Car Image",
                        subtitle = "${tripServiceManager.getVehicleName()} • ${tripServiceManager.getVehicleSubtitle()}" +
                            if (tripServiceManager.hasCustomCarImage()) " • Custom Photo" else "",
                        badgeText = "Edit Car",
                        badgePositive = tripServiceManager.hasCustomCarImage()
                    ) {
                        dlg.dismiss()
                        showEditVehicleDialog()
                    },
                    HarayaDialog.buildOptionRow(
                        context = this,
                        palette = palette,
                        title = "Prayer Schedule & Adhan Audio Alert",
                        subtitle = if (adhanEnabled) {
                            "Plays rotational Adhan from Download/hisab when prayer time arrives"
                        } else {
                            "Adhan audio alert is muted (Tap to enable or test)"
                        },
                        badgeText = if (adhanEnabled) "Enabled ✓" else "Muted",
                        badgePositive = adhanEnabled
                    ) {
                        dlg.dismiss()
                        showAdhanSettingsSubModal()
                    },
                    HarayaDialog.buildOptionRow(
                        context = this,
                        palette = palette,
                        title = "Floating Dock Overlay (AssistiveTouch Mode)",
                        subtitle = when {
                            !overlayPermOk -> "Grant 'Display over other apps' so the dock stays accessible in WhatsApp/YouTube"
                            floatingDockEnabled -> "Active when opening external apps (Collapsible into draggable circle)"
                            else -> "Floating Dock is disabled (Tap to enable)"
                        },
                        badgeText = when {
                            !overlayPermOk -> "Grant"
                            floatingDockEnabled -> "Active ✓"
                            else -> "Disabled"
                        },
                        badgePositive = floatingActive
                    ) {
                        dlg.dismiss()
                        if (!overlayPermOk) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                runCatching {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                    )
                                }
                            }
                        } else {
                            floatingDockEnabled = !floatingDockEnabled
                            getSharedPreferences("haraya_prefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("floating_dock_enabled", floatingDockEnabled).apply()
                            val msg = if (floatingDockEnabled) {
                                "Floating Dock enabled when opening external apps"
                            } else {
                                "Floating Dock disabled"
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    HarayaDialog.buildOptionRow(
                        context = this,
                        palette = palette,
                        title = "Service Schedule Security PIN",
                        subtitle = "PIN protection required before editing or resetting maintenance items",
                        badgeText = "Change PIN",
                        badgePositive = false
                    ) {
                        dlg.dismiss()
                        showChangePinDialog()
                    },
                    HarayaDialog.buildOptionRow(
                        context = this,
                        palette = palette,
                        title = "Media & Song Title Notification Access",
                        subtitle = if (notifOk) "Connected to YouTube Music / Spotify" else "Grant permission to show live song titles",
                        badgeText = if (notifOk) "Active ✓" else "Enable",
                        badgePositive = notifOk
                    ) {
                        dlg.dismiss()
                        runCatching {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    },
                    HarayaDialog.buildOptionRow(
                        context = this,
                        palette = palette,
                        title = "Location & GPS Services",
                        subtitle = if (gpsOk) "GPS signal active for Speedometer, Prayer Times, & Weather" else "Location is off • Tap to enable GPS",
                        badgeText = if (gpsOk) "GPS Active ✓" else "Turn On",
                        badgePositive = gpsOk
                    ) {
                        dlg.dismiss()
                        runCatching {
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    },
                    HarayaDialog.buildOptionRow(
                        context = this,
                        palette = palette,
                        title = "Default Home Launcher",
                        subtitle = if (launcherOk) "Haraya Dock is set as your default launcher" else "Set Haraya Dock as the primary head-unit launcher",
                        badgeText = if (launcherOk) "Active ✓" else "Set Home",
                        badgePositive = launcherOk
                    ) {
                        dlg.dismiss()
                        requestSetDefaultLauncher()
                    }
                )
                for ((idx, r) in rows.withIndex()) {
                    container.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (idx < rows.lastIndex) bottomMargin = dp(6f)
                    })
                }
            },
            actions = listOf(
                HarayaDialog.ActionButton("Close", isPrimary = true) { it.dismiss() }
            )
        )
    }

    private fun showAdhanSettingsSubModal() {
        val enabled = adhanPlayerManager.isAdhanAlertEnabled()
        HarayaDialog.showCustomModal(
            context = this,
            palette = palette,
            title = "Prayer Schedule & Adhan Audio",
            subtitle = "Auto-updates every minute & whenever vehicle GPS moves > 1 km",
            contentBuilder = { container, dlg ->
                val toggleRow = HarayaDialog.buildOptionRow(
                    context = this,
                    palette = palette,
                    title = "Automatic Adhan Popup & Audio",
                    subtitle = "Rotates tracks from Download/hisab (Fajr tracks exclusively for Fajr)",
                    badgeText = if (enabled) "Enabled ✓" else "Muted",
                    badgePositive = enabled
                ) {
                    adhanPlayerManager.setAdhanAlertEnabled(!enabled)
                    dlg.dismiss()
                    showAdhanSettingsSubModal()
                }
                val testRegRow = HarayaDialog.buildOptionRow(
                    context = this,
                    palette = palette,
                    title = "Preview Regular Adhan (Dhuhr / Asr / Maghrib / Isha)",
                    subtitle = "Plays a random rotational non-Fajr Adhan with Stop & Close popup",
                    badgeText = "Test Now",
                    badgePositive = false
                ) {
                    dlg.dismiss()
                    adhanPlayerManager.triggerAdhanWithPopup(this, palette, "Dhuhr", "12:00", isFajr = false)
                }
                val testFajrRow = HarayaDialog.buildOptionRow(
                    context = this,
                    palette = palette,
                    title = "Preview Fajr Adhan (Subuh)",
                    subtitle = "Plays a random rotational Fajr Adhan with Stop & Close popup",
                    badgeText = "Test Fajr",
                    badgePositive = false
                ) {
                    dlg.dismiss()
                    adhanPlayerManager.triggerAdhanWithPopup(this, palette, "Fajr", "04:30", isFajr = true)
                }
                val travelEnabled = adhanPlayerManager.isStartupTravelPrayerEnabled()
                val travelToggleRow = HarayaDialog.buildOptionRow(
                    context = this,
                    palette = palette,
                    title = "Startup Travel Prayer Audio (20s Delay)",
                    subtitle = "Automatically plays travel-pray.m4a 20s after head unit starts",
                    badgeText = if (travelEnabled) "Enabled ✓" else "Disabled",
                    badgePositive = travelEnabled
                ) {
                    adhanPlayerManager.setStartupTravelPrayerEnabled(!travelEnabled)
                    dlg.dismiss()
                    showAdhanSettingsSubModal()
                }
                val testTravelRow = HarayaDialog.buildOptionRow(
                    context = this,
                    palette = palette,
                    title = "Preview Startup Travel Prayer Audio",
                    subtitle = "Play travel-pray.m4a immediately",
                    badgeText = "Play Prayer",
                    badgePositive = false
                ) {
                    dlg.dismiss()
                    adhanPlayerManager.playTravelPrayerNow()
                }
                container.addView(toggleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })
                container.addView(testRegRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })
                container.addView(testFajrRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })
                container.addView(travelToggleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })
                container.addView(testTravelRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            actions = listOf(
                HarayaDialog.ActionButton("Close", isPrimary = true) { it.dismiss() }
            )
        )
    }

    private fun showChangePinDialog() {
        var oldPinRef: EditText? = null
        var newPinRef: EditText? = null

        HarayaDialog.showCustomModal(
            context = this,
            palette = palette,
            title = "Change Service Security PIN",
            subtitle = "Protects maintenance schedules from accidental resets",
            contentBuilder = { container, _ ->
                val oldEt = HarayaDialog.buildStyledInput(
                    context = this,
                    palette = palette,
                    hintText = "Current PIN",
                    inputTypeFlags = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                    maxLength = 8,
                    centerText = true
                )
                val newEt = HarayaDialog.buildStyledInput(
                    context = this,
                    palette = palette,
                    hintText = "New PIN (4 - 8 digits)",
                    inputTypeFlags = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                    maxLength = 8,
                    centerText = true
                )
                oldPinRef = oldEt
                newPinRef = newEt
                container.addView(oldEt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                container.addView(newEt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8f)
                })
            },
            actions = listOf(
                HarayaDialog.ActionButton("Cancel") { it.dismiss() },
                HarayaDialog.ActionButton("Save New PIN", isPrimary = true) { dlg ->
                    val oldPin = oldPinRef?.text?.toString().orEmpty()
                    val newPin = newPinRef?.text?.toString().orEmpty().trim()
                    if (!tripServiceManager.verifyServicePin(oldPin)) {
                        Toast.makeText(this, "Current PIN is incorrect!", Toast.LENGTH_LONG).show()
                    } else if (newPin.length < 4) {
                        Toast.makeText(this, "New PIN must be at least 4 digits!", Toast.LENGTH_LONG).show()
                    } else {
                        tripServiceManager.setServicePin(newPin)
                        dlg.dismiss()
                        Toast.makeText(this, "Service security PIN updated", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        )
    }

    private fun showEditVehicleDialog() {
        var nameRef: EditText? = null
        var subRef: EditText? = null

        HarayaDialog.showCustomModal(
            context = this,
            palette = palette,
            title = "Vehicle Identity & Car Photo",
            subtitle = "Customize vehicle name, license plate, and center car illustration",
            contentBuilder = { container, dlg ->
                val nameEt = HarayaDialog.buildStyledInput(
                    context = this,
                    palette = palette,
                    hintText = "Vehicle Name (e.g. Suzuki Baleno)",
                    initialText = tripServiceManager.getVehicleName(),
                    inputTypeFlags = InputType.TYPE_CLASS_TEXT,
                    maxLength = 40
                )
                val subEt = HarayaDialog.buildStyledInput(
                    context = this,
                    palette = palette,
                    hintText = "Type & Plate (e.g. Hatchback • B 1353 EYX)",
                    initialText = tripServiceManager.getVehicleSubtitle(),
                    inputTypeFlags = InputType.TYPE_CLASS_TEXT,
                    maxLength = 50
                )
                nameRef = nameEt
                subRef = subEt
                container.addView(nameEt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                container.addView(subEt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8f)
                    bottomMargin = dp(8f)
                })

                val pickImgRow = HarayaDialog.buildOptionRow(
                    context = this,
                    palette = palette,
                    title = "Change Car Image from Gallery",
                    subtitle = if (tripServiceManager.hasCustomCarImage()) {
                        "Custom car image active (PNG with transparent background recommended)"
                    } else {
                        "Using default Suzuki Baleno illustration (Tap to select PNG/JPG)"
                    },
                    badgeText = "Choose Photo",
                    badgePositive = tripServiceManager.hasCustomCarImage()
                ) {
                    // Save any typed name/plate before opening gallery picker
                    tripServiceManager.setVehicleIdentity(
                        nameRef?.text?.toString().orEmpty(),
                        subRef?.text?.toString().orEmpty()
                    )
                    carCockpitView.refreshVehicleIdentity()
                    dlg.dismiss()
                    launchCarImagePicker()
                }
                container.addView(pickImgRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

                if (tripServiceManager.hasCustomCarImage()) {
                    val resetImgRow = HarayaDialog.buildOptionRow(
                        context = this,
                        palette = palette,
                        title = "Restore Default Car Illustration",
                        subtitle = "Revert to built-in Suzuki Baleno image",
                        badgeText = "Reset Image",
                        badgePositive = false
                    ) {
                        tripServiceManager.resetCustomCarImage()
                        carCockpitView.refreshVehicleIdentity()
                        dlg.dismiss()
                        Toast.makeText(this, "Restored default car illustration", Toast.LENGTH_SHORT).show()
                    }
                    container.addView(resetImgRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(6f)
                    })
                }
            },
            actions = listOf(
                HarayaDialog.ActionButton("Cancel") { it.dismiss() },
                HarayaDialog.ActionButton("Save", isPrimary = true) { dlg ->
                    tripServiceManager.setVehicleIdentity(
                        nameRef?.text?.toString().orEmpty(),
                        subRef?.text?.toString().orEmpty()
                    )
                    carCockpitView.refreshVehicleIdentity()
                    dlg.dismiss()
                    Toast.makeText(this, "Vehicle identity updated", Toast.LENGTH_SHORT).show()
                }
            )
        )
    }

    private fun launchCarImagePicker() {
        runCatching {
            val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(pickIntent, "Select Car Image"), REQUEST_CODE_PICK_CAR_IMAGE)
        }.onFailure {
            Toast.makeText(this, "Unable to open image gallery on this device", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_CAR_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val ok = tripServiceManager.saveCustomCarImageFromUri(uri)
            if (ok) {
                carCockpitView.refreshVehicleIdentity()
                Toast.makeText(this, "Custom car image applied!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to load selected image", Toast.LENGTH_SHORT).show()
            }
        }
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
                    REQUEST_CODE_LOCATION
                )
                return
            }
        }
        startLocationUpdatesIfPermitted()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdatesIfPermitted()
        } else if (requestCode == REQUEST_CODE_BLUETOOTH && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openObdConfigurationModal()
        }
    }

    private fun startLocationUpdatesIfPermitted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            locTitleTv.text = "Location Permission Needed"
            locSubTv.text = "Tap to allow GPS & Weather"
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
                locSubTv.text = "Acquiring GPS/Network signal..."
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

        val gpsSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt() else 0
        obdConnectionManager.updateGpsSpeed(gpsSpeedKmh)

        tripServiceManager.onLocationUpdate(location)
        carCockpitView.refreshTripAndServices()

        // Immediately recalculate Prayer Schedule if the car has moved >= 0.01 deg (~1.1 km)
        if (abs(currentLat - lastPrayerCalcLat) >= 0.01 || abs(currentLon - lastPrayerCalcLon) >= 0.01) {
            refreshClockAndSchedule()
        }

        val providerLabel = if (location.provider == LocationManager.GPS_PROVIDER) "Accurate GPS" else "Network"
        val coordSub = String.format(Locale.US, "%.4f, %.4f • %s", currentLat, currentLon, providerLabel)
        locSubTv.text = coordSub
        mapCanvas.updateLocation(currentLat, currentLon, currentStreetLabel)
        carCockpitView.updateLocationInfo(currentLat, currentLon, locTitleTv.text.toString(), currentStreetLabel, coordSub)

        weatherFetcher.fetchIfNeeded(currentLat, currentLon) { weather ->
            runOnUiThread {
                carCockpitView.updateWeather(weather)
            }
        }

        reverseGeocodeAsync(currentLat, currentLon)
    }

    private fun reverseGeocodeAsync(lat: Double, lon: Double) {
        if (abs(lat - lastGeocodedLat) < 0.0008 && abs(lon - lastGeocodedLon) < 0.0008) {
            return
        }
        lastGeocodedLat = lat
        lastGeocodedLon = lon

        Thread {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(this, Locale.US)
                    @Suppress("DEPRECATION")
                    val list = geocoder.getFromLocation(lat, lon, 1)
                    if (!list.isNullOrEmpty()) {
                        val addr = list[0]
                        val city = addr.subAdminArea ?: addr.locality ?: addr.adminArea ?: "Location Detected"
                        val district = addr.locality ?: addr.subLocality ?: ""
                        val title = if (district.isNotEmpty() && district != city) "$district, $city" else city

                        val road = addr.thoroughfare ?: addr.subLocality ?: addr.featureName ?: ""
                        val streetSummary = when {
                            road.isNotBlank() && district.isNotBlank() && road != district -> "$road • $district"
                            road.isNotBlank() -> "$road • $city"
                            else -> title
                        }

                        runOnUiThread {
                            currentStreetLabel = streetSummary
                            locTitleTv.text = title
                            mapCanvas.updateLocation(lat, lon, currentStreetLabel)
                            carCockpitView.updateLocationInfo(lat, lon, title, currentStreetLabel, locSubTv.text.toString())
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {
        carCockpitView.updateGpsStatus(isGpsProviderActive())
    }
    override fun onProviderDisabled(provider: String) {
        carCockpitView.updateGpsStatus(isGpsProviderActive())
    }

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
