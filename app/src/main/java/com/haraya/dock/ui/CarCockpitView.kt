package com.haraya.dock.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.haraya.dock.R
import com.haraya.dock.calc.PrayerCalc
import com.haraya.dock.data.*
import com.haraya.dock.service.NotificationCollectorService
import com.haraya.dock.theme.ThemeMode
import com.haraya.dock.ui.glass.GlassCardView
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import java.util.Locale

class CarCockpitView(
    context: Context,
    private val tripServiceManager: TripAndServiceManager,
    private val adhanPlayerManager: AdhanPlayerManager,
    private val onSwitchThemeMode: (ThemeMode) -> Unit,
    private val onSwitchToClassicLayout: () -> Unit,
    private val onSelectHomeStage: () -> Unit,
    private val onSelectMapStage: () -> Unit,
    private val onOpenExternalMaps: () -> Unit,
    private val onShareLiveLocation: () -> Unit,
    private val onGpsPillClicked: () -> Unit,
    private val onOpenMusic: () -> Unit,
    private val onOpenYouTube: () -> Unit,
    private val onOpenPhone: () -> Unit,
    private val onOpenObdScreen: () -> Unit,
    private val onOpenObdSettingsModal: () -> Unit,
    private val onOpenSettingsDialog: () -> Unit,
    private val onOpenAppsScreen: () -> Unit
) : LinearLayout(context) {

    enum class CockpitStage {
        HOME,
        MAP,
        OBD,
        APPS
    }

    private var palette: GlassPalette = GlassTheme.Dark
    private var currentThemeMode: ThemeMode = ThemeMode.AUTO
    private var activeStage: CockpitStage = CockpitStage.HOME
    private var quoteIdx: Int = 0
    private var seatbeltBannerDismissed: Boolean = false
    private var isGpsActive: Boolean = false
    private var optimisticPlayingToggle: Boolean? = null

    // Stage Host Frame (between Top Bar and Bottom Dock)
    private val stageHostFrame: FrameLayout
    private val mainColumnsRow: LinearLayout

    // Embedded Full-Screen Interactive Map Stage
    private val embeddedMapCard: GlassCardView
    private val embeddedMapCanvas: MapCanvasView
    private val embeddedMapHeaderBar: LinearLayout
    private val embeddedMapTitleTv: TextView
    private val embeddedMapSubTv: TextView
    private val embeddedMapShareBtn: TextView
    private val embeddedMapExternalBtn: TextView
    private val embeddedMapZoomInBtn: FrameLayout
    private val embeddedMapZoomInIcon: VectorIconView
    private val embeddedMapZoomOutBtn: FrameLayout
    private val embeddedMapZoomOutIcon: VectorIconView
    private val embeddedMapRecenterBtn: FrameLayout
    private val embeddedMapRecenterIcon: VectorIconView
    private val embeddedMapBackHomeBtn: TextView

    // Embedded OBD & Apps Drawer references
    private var embeddedObdView: View? = null
    private var embeddedAppsView: View? = null

    // Top Bar
    private val topBarLayout: LinearLayout
    private val themeCycleBtn: FrameLayout
    private val themeCycleIcon: VectorIconView
    private val layoutSwitchBtn: TextView
    private val topBrandTitleTv: TextView
    private val topBrandSubTv: TextView
    private val gpsPillTv: TextView
    private val settingsTopBtn: FrameLayout
    private val settingsTopIcon: VectorIconView

    // Left Column: (1) Live Map + (2) Prayer Times + (3) Daily Verse & Prayer
    private val mapCard: GlassCardView
    private val mapHeaderTv: TextView
    private val mapShareBtn: TextView
    private val mapViewport: FrameLayout
    private val mapCanvas: MapCanvasView
    private val mapFooterTv: TextView

    private val prayerCard: GlassCardView
    private val prayerHeaderTitleTv: TextView
    private val prayerNextTv: TextView
    private val prayerSlotBoxes = mutableListOf<LinearLayout>()
    private val prayerSlotNames = mutableListOf<TextView>()
    private val prayerSlotTimes = mutableListOf<TextView>()

    private val quoteCard: GlassCardView
    private val quoteHeaderTitleTv: TextView
    private val quoteCountTv: TextView
    private val quoteArabicTv: TextView
    private val quoteBodyTv: TextView
    private val quoteRefTv: TextView

    // Center Column: Alert Banner + Arc Speedometer (with Mini RPM & Gear) + Car Image + Media Control
    private val alertBannerBox: LinearLayout
    private val alertBannerTitleTv: TextView
    private val alertBannerSubTv: TextView
    private val alertBannerCloseTv: TextView

    private val speedArcView: CenterSpeedArcView
    private val carImageView: ImageView
    private val carNameTv: TextView
    private val carSubTv: TextView

    private val mediaCard: GlassCardView
    private val mediaTitleTv: TextView
    private val mediaSubTv: TextView
    private val mediaYtBtn: FrameLayout
    private val mediaPrevBtn: FrameLayout
    private val mediaPlayBtn: FrameLayout
    private val mediaNextBtn: FrameLayout
    private val mediaYtIcon: VectorIconView
    private val mediaPrevIcon: VectorIconView
    private val mediaPlayIcon: VectorIconView
    private val mediaNextIcon: VectorIconView

    // Right Column: (1) Trip Summary & History + (2) Service Schedule (PIN Locked) + (3) Weather
    private val tripHistoryCard: GlassCardView
    private val tripHistoryHeaderTv: TextView
    private val tripHistoryBadgeTv: TextView
    private val tripDistValTv: TextView
    private val tripDurValTv: TextView
    private val tripAvgValTv: TextView
    private val tripDistSubTv: TextView
    private val tripDurSubTv: TextView
    private val tripAvgSubTv: TextView
    private val tripFootnoteTv: TextView

    private val serviceCard: GlassCardView
    private val serviceHeaderTv: TextView
    private val servicePinBadgeBox: LinearLayout
    private val servicePinLockIcon: VectorIconView
    private val servicePinBadgeTv: TextView
    private val serviceItemBoxes = mutableListOf<LinearLayout>()
    private val serviceItemIcons = mutableListOf<VectorIconView>()
    private val serviceItemTitles = mutableListOf<TextView>()
    private val serviceItemStatuses = mutableListOf<TextView>()

    private val weatherCard: GlassCardView
    private val weatherHeaderTv: TextView
    private val weatherSourceTv: TextView
    private val weatherIcon: VectorIconView
    private val weatherTempTv: TextView
    private val weatherDescTv: TextView

    // Bottom Dock Bar (6 Tabs: HOME, MAP, MEDIA, PHONE, OBD & ENGINE, APPS)
    private val bottomDockCard: GlassCardView
    private val dockTabBoxes = mutableListOf<LinearLayout>()
    private val dockTabIcons = mutableListOf<VectorIconView>()
    private val dockTabLabels = mutableListOf<TextView>()

    init {
        orientation = VERTICAL
        val pad = dp(12f)
        setPadding(pad, dp(7f), pad, dp(7f))

        NotificationCollectorService.onMediaChangedCallback = {
            post { refreshMediaState() }
        }

        // ==========================================
        // 1. TOP STATUS & MODE BAR
        // ==========================================
        topBarLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Single Theme Mode Cycle Button: Sun (Light) -> Moon (Dark) -> A (Auto)
        themeCycleIcon = VectorIconView(context, VectorIconView.IconType.AUTO_A)
        themeCycleBtn = FrameLayout(context).apply {
            addView(themeCycleIcon, FrameLayout.LayoutParams(dp(15f), dp(15f), Gravity.CENTER))
            setOnClickListener {
                val nextMode = when (currentThemeMode) {
                    ThemeMode.LIGHT -> ThemeMode.DARK
                    ThemeMode.DARK -> ThemeMode.AUTO
                    ThemeMode.AUTO -> ThemeMode.LIGHT
                }
                onSwitchThemeMode(nextMode)
            }
        }

        layoutSwitchBtn = TextView(context).apply {
            text = "Classic"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
            setOnClickListener { onSwitchToClassicLayout() }
        }

        val leftTopGroup = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        leftTopGroup.addView(themeCycleBtn, LayoutParams(dp(30f), dp(26f)))
        leftTopGroup.addView(layoutSwitchBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(6f)
        })

        val brandCenterCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
        }
        topBrandTitleTv = TextView(context).apply {
            text = "HARAYA DOCK"
            textSize = 12f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.16f
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        topBrandSubTv = TextView(context).apply {
            text = "Vehicle Smart Cockpit"
            textSize = 8.5f
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        brandCenterCol.addView(topBrandTitleTv)
        brandCenterCol.addView(topBrandSubTv)

        val rightTopGroup = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        gpsPillTv = TextView(context).apply {
            text = "● GPS Active"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
            setOnClickListener { onGpsPillClicked() }
        }

        // Settings Icon-Only Button
        settingsTopIcon = VectorIconView(context, VectorIconView.IconType.GEAR_COG)
        settingsTopBtn = FrameLayout(context).apply {
            addView(settingsTopIcon, FrameLayout.LayoutParams(dp(15f), dp(15f), Gravity.CENTER))
            setOnClickListener { onOpenSettingsDialog() }
        }

        rightTopGroup.addView(gpsPillTv)
        rightTopGroup.addView(settingsTopBtn, LayoutParams(dp(30f), dp(26f)).apply {
            leftMargin = dp(6f)
        })

        topBarLayout.addView(leftTopGroup, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f))
        topBarLayout.addView(brandCenterCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f))
        topBarLayout.addView(rightTopGroup, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f))

        addView(topBarLayout, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(5f)
        })

        // ==========================================
        // 2. MAIN 3-COLUMN COCKPIT AREA & EMBEDDED STAGE HOST
        // ==========================================
        stageHostFrame = FrameLayout(context)

        mainColumnsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }

        val cardPadH = dp(12f)
        val cardPadV = dp(8f)

        // --- LEFT COLUMN: (1) Live Map + (2) Prayer Times + (3) Daily Verse & Prayer ---
        val leftCol = LinearLayout(context).apply {
            orientation = VERTICAL
        }

        // 2A. Live Map & Location Card
        mapCard = GlassCardView(context)
        mapCard.setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        val mapCol = LinearLayout(context).apply { orientation = VERTICAL }
        val mapHeaderRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        mapHeaderTv = TextView(context).apply {
            text = "MAP & LOCATION"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.04f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mapShareBtn = TextView(context).apply {
            text = "Share Live"
            textSize = 8.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(8f), dp(3f), dp(8f), dp(3f))
            setOnClickListener { onShareLiveLocation() }
        }
        mapHeaderRow.addView(mapHeaderTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6f)
        })
        mapHeaderRow.addView(mapShareBtn)
        mapCol.addView(mapHeaderRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(5f)
        })

        mapViewport = FrameLayout(context)
        GlassTheme.clipRound(context, mapViewport, 10f)
        mapCanvas = MapCanvasView(context).apply {
            setOnClickListener { onSelectMapStage() }
        }
        mapViewport.addView(mapCanvas, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mapCol.addView(mapViewport, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(4f)
        })

        mapFooterTv = TextView(context).apply {
            text = "Live OpenStreetMap • Tap map to expand"
            textSize = 8.5f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mapCol.addView(mapFooterTv)
        mapCard.addView(mapCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 2B. 5 Daily Prayer Times Card (Auto-updates on time & GPS travel)
        prayerCard = GlassCardView(context)
        prayerCard.setPadding(cardPadH, dp(7f), cardPadH, dp(7f))
        prayerCard.setOnClickListener {
            if (adhanPlayerManager.isPlayingAdhan) {
                adhanPlayerManager.stopAdhanPlayback()
                Toast.makeText(context, "Adhan playback stopped", Toast.LENGTH_SHORT).show()
            } else {
                showAdhanPreviewDialog()
            }
        }
        val prayerCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val prayerHeadRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prayerHeaderTitleTv = TextView(context).apply {
            text = "PRAYER TIMES"
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.04f
            includeFontPadding = false
        }
        prayerNextTv = TextView(context).apply {
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            gravity = Gravity.END
        }
        prayerHeadRow.addView(prayerHeaderTitleTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        prayerHeadRow.addView(prayerNextTv)
        prayerCol.addView(prayerHeadRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4f)
        })

        val prayerStrip = LinearLayout(context).apply { orientation = HORIZONTAL }
        val pNames = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        for (i in pNames.indices) {
            val slot = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2f), dp(4f), dp(2f), dp(4f))
            }
            val nTv = TextView(context).apply {
                text = pNames[i]
                textSize = 8f
                includeFontPadding = false
                gravity = Gravity.CENTER
            }
            val tTv = TextView(context).apply {
                text = "--:--"
                textSize = 10f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                fontFeatureSettings = "tnum"
                includeFontPadding = false
                gravity = Gravity.CENTER
            }
            slot.addView(nTv)
            slot.addView(tTv, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(1f)
            })
            prayerSlotBoxes.add(slot)
            prayerSlotNames.add(nTv)
            prayerSlotTimes.add(tTv)
            prayerStrip.addView(slot, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i < pNames.lastIndex) rightMargin = dp(4f)
            })
        }
        prayerCol.addView(prayerStrip, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        prayerCard.addView(prayerCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 2C. Daily Verse & Prayer Card (Auto-updates via AlQuran Cloud API: Arabic + Indonesian)
        QuoteRepository.loadCachedOrDefault(context)
        quoteCard = GlassCardView(context)
        quoteCard.setPadding(cardPadH, dp(6f), cardPadH, dp(6f))
        quoteCard.setOnClickListener {
            QuoteRepository.fetchNextFromAlQuranApiAsync(context) {
                refreshQuoteText()
            }
        }
        val quoteCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val quoteHeadRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        quoteHeaderTitleTv = TextView(context).apply {
            text = "DAILY VERSE & PRAYER"
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.04f
            includeFontPadding = false
        }
        quoteCountTv = TextView(context).apply {
            text = "Auto • Tap"
            textSize = 8f
            includeFontPadding = false
            gravity = Gravity.END
        }
        quoteHeadRow.addView(quoteHeaderTitleTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        quoteHeadRow.addView(quoteCountTv)
        quoteCol.addView(quoteHeadRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(2f)
        })

        quoteArabicTv = TextView(context).apply {
            textSize = 11.5f
            typeface = Typeface.create("serif", Typeface.BOLD)
            includeFontPadding = false
            gravity = Gravity.END
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        quoteBodyTv = TextView(context).apply {
            textSize = 8.5f
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        quoteRefTv = TextView(context).apply {
            textSize = 8f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
        }
        quoteCol.addView(quoteArabicTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(2f)
        })
        quoteCol.addView(quoteBodyTv)
        quoteCol.addView(quoteRefTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2f)
        })
        quoteCard.addView(quoteCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        leftCol.addView(mapCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.41f).apply { bottomMargin = dp(6f) })
        leftCol.addView(prayerCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.27f).apply { bottomMargin = dp(6f) })
        leftCol.addView(quoteCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.32f))

        mainColumnsRow.addView(leftCol, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.29f).apply {
            rightMargin = dp(8f)
        })

        // --- CENTER COLUMN (Alert Banner + Speedometer Arc with Mini RPM/Gear + Vehicle Image + Media Control) ---
        val centerCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        alertBannerBox = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            visibility = View.GONE
        }
        val alertTextCol = LinearLayout(context).apply { orientation = VERTICAL }
        alertBannerTitleTv = TextView(context).apply {
            text = "Fasten Your Seatbelt"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
        }
        alertBannerSubTv = TextView(context).apply {
            text = "Please fasten your seatbelt for a safe journey"
            textSize = 8.5f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        alertTextCol.addView(alertBannerTitleTv)
        alertTextCol.addView(alertBannerSubTv)

        alertBannerCloseTv = TextView(context).apply {
            text = "Dismiss"
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(8f), dp(3f), dp(8f), dp(3f))
            setOnClickListener {
                seatbeltBannerDismissed = true
                refreshAlertBanner()
            }
        }
        alertBannerBox.addView(alertTextCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        alertBannerBox.addView(alertBannerCloseTv)
        centerCol.addView(alertBannerBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4f)
        })

        // Center Instrument Stage: Speedometer Arc + Mini RPM/Gear at top, Vehicle Image at bottom
        val centerStageFrame = FrameLayout(context)
        speedArcView = CenterSpeedArcView(context).apply {
            setOnClickListener { onOpenObdSettingsModal() }
        }
        centerStageFrame.addView(
            speedArcView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP)
        )

        carImageView = ImageView(context).apply {
            setImageResource(R.drawable.car_baleno)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        centerStageFrame.addView(
            carImageView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(100f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                leftMargin = dp(28f)
                rightMargin = dp(28f)
                bottomMargin = dp(2f)
            }
        )

        val carBadgeCol = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        carNameTv = TextView(context).apply {
            text = tripServiceManager.getVehicleName()
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
        }
        carSubTv = TextView(context).apply {
            text = tripServiceManager.getVehicleSubtitle()
            textSize = 8.5f
            includeFontPadding = false
        }
        carBadgeCol.addView(carNameTv)
        carBadgeCol.addView(carSubTv)
        centerStageFrame.addView(
            carBadgeCol,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(6f)
                bottomMargin = dp(2f)
            }
        )

        centerCol.addView(centerStageFrame, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(6f)
        })

        // Media Control Card Below Vehicle
        mediaCard = GlassCardView(context)
        mediaCard.setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        val mediaRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mediaTextCol = LinearLayout(context).apply {
            orientation = VERTICAL
            setOnClickListener {
                if (!NotificationCollectorService.isNotificationAccessGranted(context)) {
                    promptNotificationListenerAccess()
                } else {
                    onOpenMusic()
                }
            }
        }
        mediaTitleTv = TextView(context).apply {
            text = "Media Control"
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mediaSubTv = TextView(context).apply {
            text = "Tap to open YouTube Music"
            textSize = 8.5f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mediaTextCol.addView(mediaTitleTv)
        mediaTextCol.addView(mediaSubTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(1f)
        })
        mediaRow.addView(mediaTextCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(8f)
        })

        fun buildMediaCircleBtn(iconType: VectorIconView.IconType, boxDp: Float, iconDp: Float, onClick: () -> Unit): Pair<FrameLayout, VectorIconView> {
            val box = FrameLayout(context).apply {
                setOnClickListener { onClick() }
            }
            val ic = VectorIconView(context, iconType)
            box.addView(ic, FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER))
            mediaRow.addView(box, LayoutParams(dp(boxDp), dp(boxDp)).apply {
                leftMargin = dp(6f)
            })
            return box to ic
        }

        val ytPair = buildMediaCircleBtn(VectorIconView.IconType.YOUTUBE, 32f, 15f) { onOpenYouTube() }
        val prevPair = buildMediaCircleBtn(VectorIconView.IconType.PREV_TRACK, 32f, 14f) {
            sendMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        val playPair = buildMediaCircleBtn(VectorIconView.IconType.PLAY_PAUSE, 36f, 16f) {
            val currentlyPlaying = NotificationCollectorService.isCurrentlyPlaying(context)
            optimisticPlayingToggle = !currentlyPlaying
            refreshMediaState()
            sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        val nextPair = buildMediaCircleBtn(VectorIconView.IconType.NEXT_TRACK, 32f, 14f) {
            sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT)
        }

        mediaYtBtn = ytPair.first; mediaYtIcon = ytPair.second
        mediaPrevBtn = prevPair.first; mediaPrevIcon = prevPair.second
        mediaPlayBtn = playPair.first; mediaPlayIcon = playPair.second
        mediaNextBtn = nextPair.first; mediaNextIcon = nextPair.second

        mediaCard.addView(mediaRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        centerCol.addView(mediaCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        mainColumnsRow.addView(centerCol, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.42f).apply {
            rightMargin = dp(8f)
        })

        // --- RIGHT COLUMN: (1) Trip History + (2) Service Schedule (PIN Locked) + (3) Weather ---
        val rightCol = LinearLayout(context).apply {
            orientation = VERTICAL
        }

        // 3A. Trip Summary & History Card (Tap for Full Trip History Popup)
        tripHistoryCard = GlassCardView(context)
        tripHistoryCard.setPadding(cardPadH, dp(7f), cardPadH, dp(7f))
        tripHistoryCard.setOnClickListener {
            showTripHistoryPopupDialog()
        }
        val tripCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tripHeaderRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tripHistoryHeaderTv = TextView(context).apply {
            text = "TRIP HISTORY"
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.04f
            includeFontPadding = false
        }
        tripHistoryBadgeTv = TextView(context).apply {
            text = "0/5 • Details"
            textSize = 8.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }
        tripHeaderRow.addView(tripHistoryHeaderTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tripHeaderRow.addView(tripHistoryBadgeTv)
        tripCol.addView(tripHeaderRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(3f)
        })

        val tripMetricsRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        fun buildTripMetric(defaultVal: String, unitLabel: String): Pair<TextView, TextView> {
            val box = LinearLayout(context).apply { orientation = VERTICAL }
            val vTv = TextView(context).apply {
                text = defaultVal
                textSize = 13.5f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                fontFeatureSettings = "tnum"
                includeFontPadding = false
            }
            val uTv = TextView(context).apply {
                text = unitLabel
                textSize = 8f
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            box.addView(vTv)
            box.addView(uTv)
            tripMetricsRow.addView(box, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return vTv to uTv
        }
        val m1 = buildTripMetric("0.0", "km")
        val m2 = buildTripMetric("00:00", "hr : min")
        val m3 = buildTripMetric("0", "km/h avg")
        tripDistValTv = m1.first; tripDistSubTv = m1.second
        tripDurValTv = m2.first; tripDurSubTv = m2.second
        tripAvgValTv = m3.first; tripAvgSubTv = m3.second
        tripCol.addView(tripMetricsRow)

        tripFootnoteTv = TextView(context).apply {
            text = "Tap to view saved Trip History"
            textSize = 8f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        tripCol.addView(tripFootnoteTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2f)
        })
        tripHistoryCard.addView(tripCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 3B. Service Schedule Card (2x2 Horizontal Grid, PIN hidden behind Lock badge)
        serviceCard = GlassCardView(context)
        serviceCard.setPadding(cardPadH, dp(7f), cardPadH, dp(7f))
        val svcCol = LinearLayout(context).apply { orientation = VERTICAL }
        val svcHeadRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        serviceHeaderTv = TextView(context).apply {
            text = "SERVICE SCHEDULE"
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.04f
            includeFontPadding = false
        }
        servicePinBadgeBox = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        servicePinLockIcon = VectorIconView(context, VectorIconView.IconType.LOCK)
        servicePinBadgeTv = TextView(context).apply {
            text = "PIN Locked"
            textSize = 8f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
        }
        servicePinBadgeBox.addView(servicePinLockIcon, LayoutParams(dp(10f), dp(10f)).apply {
            rightMargin = dp(3f)
        })
        servicePinBadgeBox.addView(servicePinBadgeTv)

        svcHeadRow.addView(serviceHeaderTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        svcHeadRow.addView(servicePinBadgeBox)
        svcCol.addView(svcHeadRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(5f)
        })

        val svcIcons = listOf(
            VectorIconView.IconType.OIL_DROP,
            VectorIconView.IconType.GEAR_COG,
            VectorIconView.IconType.SNOWFLAKE,
            VectorIconView.IconType.WRENCH
        )
        for (rowIdx in 0..1) {
            val rBox = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (colIdx in 0..1) {
                val idx = rowIdx * 2 + colIdx
                val itemBox = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
                    setOnClickListener { promptPinBeforeEditService(idx) }
                }
                val ic = VectorIconView(context, svcIcons[idx])
                val textStack = LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val titleTv = TextView(context).apply {
                    textSize = 9.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                val statTv = TextView(context).apply {
                    textSize = 9f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    fontFeatureSettings = "tnum"
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                textStack.addView(titleTv)
                textStack.addView(statTv, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(1f)
                })

                itemBox.addView(ic, LayoutParams(dp(16f), dp(16f)).apply {
                    rightMargin = dp(7f)
                })
                itemBox.addView(textStack, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                serviceItemBoxes.add(itemBox)
                serviceItemIcons.add(ic)
                serviceItemTitles.add(titleTv)
                serviceItemStatuses.add(statTv)

                rBox.addView(itemBox, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (colIdx == 0) rightMargin = dp(5f)
                })
            }
            svcCol.addView(rBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                if (rowIdx == 0) bottomMargin = dp(5f)
            })
        }
        serviceCard.addView(svcCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 3C. Weather Card (Open-Meteo)
        weatherCard = GlassCardView(context)
        weatherCard.setPadding(cardPadH, dp(6f), cardPadH, dp(6f))
        val wCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val wHeadRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        weatherHeaderTv = TextView(context).apply {
            text = "WEATHER"
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.04f
            includeFontPadding = false
        }
        weatherSourceTv = TextView(context).apply {
            text = "Open-Meteo"
            textSize = 8f
            includeFontPadding = false
            gravity = Gravity.END
        }
        wHeadRow.addView(weatherHeaderTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        wHeadRow.addView(weatherSourceTv)
        wCol.addView(wHeadRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(2f)
        })

        val wBodyRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        weatherIcon = VectorIconView(context, VectorIconView.IconType.CLOUD_SUN)
        wBodyRow.addView(weatherIcon, LayoutParams(dp(20f), dp(20f)).apply {
            rightMargin = dp(8f)
        })
        weatherTempTv = TextView(context).apply {
            text = "28°C"
            textSize = 16.5f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }
        weatherDescTv = TextView(context).apply {
            text = "Partly Cloudy"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        wBodyRow.addView(weatherTempTv, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(8f)
        })
        wBodyRow.addView(weatherDescTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        wCol.addView(wBodyRow)
        weatherCard.addView(wCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        rightCol.addView(tripHistoryCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.30f).apply { bottomMargin = dp(6f) })
        rightCol.addView(serviceCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.46f).apply { bottomMargin = dp(6f) })
        rightCol.addView(weatherCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.24f))

        mainColumnsRow.addView(rightCol, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.29f))

        stageHostFrame.addView(
            mainColumnsRow,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // ==========================================
        // 2D. EMBEDDED INTERACTIVE MAP STAGE (In-Cockpit)
        // ==========================================
        embeddedMapCard = GlassCardView(context).apply {
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            visibility = View.GONE
        }
        val embeddedMapFrame = FrameLayout(context)
        GlassTheme.clipRound(context, embeddedMapFrame, 12f)

        embeddedMapCanvas = MapCanvasView(context, interactivePanZoom = true)
        embeddedMapFrame.addView(
            embeddedMapCanvas,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // Top Floating Overlay Bar inside Embedded Map
        embeddedMapHeaderBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        }
        val embLocCol = LinearLayout(context).apply { orientation = VERTICAL }
        embeddedMapTitleTv = TextView(context).apply {
            text = "INTERACTIVE NAVIGATION MAP"
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        embeddedMapSubTv = TextView(context).apply {
            text = "Drag map with finger • Use corner buttons to zoom or recenter"
            textSize = 9.5f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        embLocCol.addView(embeddedMapTitleTv)
        embLocCol.addView(embeddedMapSubTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2f)
        })
        embeddedMapHeaderBar.addView(embLocCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(8f)
        })

        embeddedMapShareBtn = TextView(context).apply {
            text = "Share Live Location"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            setOnClickListener { onShareLiveLocation() }
        }
        embeddedMapExternalBtn = TextView(context).apply {
            text = "Open Google Maps"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            setOnClickListener { onOpenExternalMaps() }
        }
        embeddedMapBackHomeBtn = TextView(context).apply {
            text = "Back"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            setOnClickListener { onSelectHomeStage() }
        }
        embeddedMapHeaderBar.addView(embeddedMapShareBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(6f)
        })
        embeddedMapHeaderBar.addView(embeddedMapExternalBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(6f)
        })
        embeddedMapHeaderBar.addView(embeddedMapBackHomeBtn)

        embeddedMapFrame.addView(
            embeddedMapHeaderBar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
                leftMargin = dp(10f)
                rightMargin = dp(10f)
                topMargin = dp(10f)
            }
        )

        // Icon-Only Map Controls: Zoom In (Magnifying Glass +), Zoom Out (Magnifying Glass -), Recenter (Target Dot)
        val mapControlsCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.END
        }
        fun buildMapFloatingIconBtn(iconType: VectorIconView.IconType, onClick: () -> Unit): Pair<FrameLayout, VectorIconView> {
            val ic = VectorIconView(context, iconType)
            val box = FrameLayout(context).apply {
                addView(ic, FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER))
                setOnClickListener { onClick() }
            }
            return box to ic
        }
        val zoomInPair = buildMapFloatingIconBtn(VectorIconView.IconType.ZOOM_IN) {
            embeddedMapCanvas.zoomIn()
        }
        val zoomOutPair = buildMapFloatingIconBtn(VectorIconView.IconType.ZOOM_OUT) {
            embeddedMapCanvas.zoomOut()
        }
        val recenterPair = buildMapFloatingIconBtn(VectorIconView.IconType.RECENTER_DOT) {
            embeddedMapCanvas.recenterOnCar()
        }
        embeddedMapZoomInBtn = zoomInPair.first; embeddedMapZoomInIcon = zoomInPair.second
        embeddedMapZoomOutBtn = zoomOutPair.first; embeddedMapZoomOutIcon = zoomOutPair.second
        embeddedMapRecenterBtn = recenterPair.first; embeddedMapRecenterIcon = recenterPair.second

        mapControlsCol.addView(embeddedMapZoomInBtn, LayoutParams(dp(38f), dp(38f)).apply {
            bottomMargin = dp(6f)
        })
        mapControlsCol.addView(embeddedMapZoomOutBtn, LayoutParams(dp(38f), dp(38f)).apply {
            bottomMargin = dp(6f)
        })
        mapControlsCol.addView(embeddedMapRecenterBtn, LayoutParams(dp(38f), dp(38f)))

        embeddedMapFrame.addView(
            mapControlsCol,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = dp(10f)
                bottomMargin = dp(10f)
            }
        )

        embeddedMapCard.addView(embeddedMapFrame, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        stageHostFrame.addView(
            embeddedMapCard,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        addView(stageHostFrame, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(5f)
        })

        // ==========================================
        // 3. BOTTOM NAVIGATION DOCK BAR (Settings Tab Removed!)
        // ==========================================
        bottomDockCard = GlassCardView(context)
        bottomDockCard.setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
        val dockRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dockSpecs = listOf(
            Triple(VectorIconView.IconType.HOME, "HOME") { onSelectHomeStage() },
            Triple(VectorIconView.IconType.MAPS, "MAP") { onSelectMapStage() },
            Triple(VectorIconView.IconType.MUSIC, "MEDIA") { onOpenMusic() },
            Triple(VectorIconView.IconType.PHONE, "PHONE") { onOpenPhone() },
            Triple(VectorIconView.IconType.GAUGE, "OBD & ENGINE") { onOpenObdScreen() },
            Triple(VectorIconView.IconType.APPS_GRID, "APPS") { onOpenAppsScreen() }
        )

        for (i in dockSpecs.indices) {
            val spec = dockSpecs[i]
            val tab = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(8f), dp(5f), dp(8f), dp(5f))
                setOnClickListener { spec.third.invoke() }
            }
            val ic = VectorIconView(context, spec.first)
            val lbl = TextView(context).apply {
                text = spec.second
                textSize = 9.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                letterSpacing = 0.04f
                includeFontPadding = false
            }
            tab.addView(ic, LayoutParams(dp(14f), dp(14f)).apply { rightMargin = dp(5f) })
            tab.addView(lbl)

            dockTabBoxes.add(tab)
            dockTabIcons.add(ic)
            dockTabLabels.add(lbl)

            dockRow.addView(tab, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i < dockSpecs.lastIndex) rightMargin = dp(4f)
            })
        }
        bottomDockCard.addView(dockRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(bottomDockCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun sendMediaButton(keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        postDelayed({
            optimisticPlayingToggle = null
            NotificationCollectorService.pollNow(context)
            refreshMediaState()
        }, 450L)
    }

    fun updateSpeed(kmh: Int) {
        speedArcView.setSpeed(kmh)
    }

    fun updateObdTelemetry(telemetry: ObdConnectionManager.ObdTelemetry) {
        speedArcView.setObdTelemetry(
            speed = telemetry.speedKmh,
            rpm = telemetry.rpm,
            gear = telemetry.gearLabel,
            src = telemetry.sourceLabel
        )
    }

    fun updateGpsStatus(enabled: Boolean) {
        isGpsActive = enabled
        styleGpsPill()
    }

    private fun styleGpsPill() {
        if (isGpsActive) {
            gpsPillTv.text = "● GPS Active"
            gpsPillTv.background = GlassTheme.innerDrawable(context, palette, 8f)
            gpsPillTv.setTextColor(if (palette.isDark) 0xFF34D399.toInt() else 0xFF059669.toInt())
        } else {
            gpsPillTv.text = "○ GPS Off"
            val offBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8f).toFloat()
                setColor(if (palette.isDark) 0x38EF4444 else 0x26EF4444)
                setStroke(dp(1f), 0xFFEF4444.toInt())
            }
            gpsPillTv.background = offBg
            gpsPillTv.setTextColor(if (palette.isDark) 0xFFFCA5A5.toInt() else 0xFFB91C1C.toInt())
        }
    }

    fun updateClockAndPrayer(clockStr: String, prayers: PrayerCalc.PrayerTimes) {
        speedArcView.setClockText(clockStr)

        if (adhanPlayerManager.isPlayingAdhan) {
            prayerNextTv.text = "🔊 Stop Adhan"
            prayerNextTv.setTextColor(0xFFEF4444.toInt())
        } else {
            val hrs = prayers.nextPrayerRemainingMinutes / 60
            val mins = prayers.nextPrayerRemainingMinutes % 60
            val remStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
            prayerNextTv.text = "${prayers.nextPrayerName} ${prayers.nextPrayerTime} (-$remStr)"
            prayerNextTv.setTextColor(palette.accent)
        }

        val times = listOf(prayers.subuh, prayers.dzuhur, prayers.ashar, prayers.maghrib, prayers.isya)
        val names = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        for (i in 0 until 5) {
            prayerSlotTimes[i].text = times[i]
            val isNext = names[i] == prayers.nextPrayerName
            if (isNext) {
                prayerSlotBoxes[i].background = GlassTheme.selectDrawable(context, palette, 8f)
                prayerSlotNames[i].setTextColor(palette.selectFg)
                prayerSlotTimes[i].setTextColor(palette.selectFg)
            } else {
                prayerSlotBoxes[i].background = GlassTheme.innerDrawable(context, palette, 8f)
                prayerSlotNames[i].setTextColor(palette.textSecondary)
                prayerSlotTimes[i].setTextColor(palette.textPrimary)
            }
        }
        QuoteRepository.autoUpdateIfDue(context, intervalMinutes = 5) {
            refreshQuoteText()
        }
        refreshQuoteText()
        refreshMediaState()
    }

    fun setCockpitStage(stage: CockpitStage, obdView: View, appsView: View) {
        activeStage = stage
        embeddedObdView = obdView
        embeddedAppsView = appsView

        if (obdView.parent !== stageHostFrame) {
            (obdView.parent as? ViewGroup)?.removeView(obdView)
            stageHostFrame.addView(
                obdView,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        if (appsView.parent !== stageHostFrame) {
            (appsView.parent as? ViewGroup)?.removeView(appsView)
            stageHostFrame.addView(
                appsView,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }

        mainColumnsRow.visibility = if (stage == CockpitStage.HOME) View.VISIBLE else View.GONE
        embeddedMapCard.visibility = if (stage == CockpitStage.MAP) View.VISIBLE else View.GONE
        obdView.visibility = if (stage == CockpitStage.OBD) View.VISIBLE else View.GONE
        appsView.visibility = if (stage == CockpitStage.APPS) View.VISIBLE else View.GONE

        styleDockTabs()
    }

    private fun styleDockTabs() {
        for (i in dockTabBoxes.indices) {
            val active = when (i) {
                0 -> activeStage == CockpitStage.HOME
                1 -> activeStage == CockpitStage.MAP
                4 -> activeStage == CockpitStage.OBD
                5 -> activeStage == CockpitStage.APPS
                else -> false
            }
            if (active) {
                dockTabBoxes[i].background = GlassTheme.selectDrawable(context, palette, 10f)
                dockTabIcons[i].setTint(palette.selectFg)
                dockTabLabels[i].setTextColor(palette.selectFg)
            } else {
                dockTabBoxes[i].background = null
                GlassTheme.makePressable(context, dockTabBoxes[i], palette, 10f)
                dockTabIcons[i].setTint(palette.textSecondary)
                dockTabLabels[i].setTextColor(palette.textSecondary)
            }
        }
    }

    fun updateLocationInfo(lat: Double, lon: Double, placeTitle: String, streetDetail: String, coordSub: String) {
        mapHeaderTv.text = placeTitle.uppercase(Locale.getDefault())
        val cleanStreet = streetDetail.trim()
        val combinedFooter = if (cleanStreet.isNotEmpty() && !cleanStreet.equals("Locating Street...", ignoreCase = true)) {
            "$cleanStreet • $coordSub"
        } else {
            coordSub
        }
        mapFooterTv.text = combinedFooter
        mapCanvas.updateLocation(lat, lon, cleanStreet.ifBlank { placeTitle })

        embeddedMapTitleTv.text = placeTitle.uppercase(Locale.getDefault())
        embeddedMapSubTv.text = combinedFooter
        embeddedMapCanvas.updateLocation(lat, lon, cleanStreet.ifBlank { placeTitle })
    }

    fun updateWeather(weather: WeatherState) {
        weatherTempTv.text = "${weather.tempCelsius}°C"
        val cleanCondition = weather.conditionLabel.split("•").firstOrNull()?.trim().orEmpty()
        weatherDescTv.text = cleanCondition.ifEmpty { weather.conditionLabel }
    }

    fun refreshVehicleIdentity() {
        carNameTv.text = tripServiceManager.getVehicleName()
        carSubTv.text = tripServiceManager.getVehicleSubtitle()
        val customBmp = tripServiceManager.getCustomCarBitmap()
        if (customBmp != null) {
            carImageView.setImageBitmap(customBmp)
        } else {
            carImageView.setImageResource(R.drawable.car_baleno)
        }
    }

    fun refreshTripAndServices() {
        val cur = tripServiceManager.getCurrentTripState()
        val history = tripServiceManager.getTripHistory()
        tripHistoryBadgeTv.text = "${history.size}/5 • Details"

        if (cur.hasStarted) {
            tripDistValTv.text = String.format(Locale.US, "%.1f", cur.distanceKm)
            val h = cur.durationMinutes / 60
            val m = cur.durationMinutes % 60
            tripDurValTv.text = String.format(Locale.US, "%02d:%02d", h, m)
            tripAvgValTv.text = cur.avgSpeedKmh.toString()
            tripFootnoteTv.text = "Active trip • Tap card for Trip History"
        } else if (history.isNotEmpty()) {
            val latest = history.first()
            tripDistValTv.text = String.format(Locale.US, "%.1f", latest.distanceKm)
            val h = latest.durationMinutes / 60
            val m = latest.durationMinutes % 60
            tripDurValTv.text = String.format(Locale.US, "%02d:%02d", h, m)
            tripAvgValTv.text = latest.avgSpeedKmh.toString()
            tripFootnoteTv.text = "Last (${latest.dateLabel}) • Tap for all trips"
        } else {
            tripDistValTv.text = "0.0"
            tripDurValTv.text = "00:00"
            tripAvgValTv.text = "0"
            tripFootnoteTv.text = "Starts on GPS movement • Tap for history"
        }

        val services = tripServiceManager.getServiceItems()
        for (i in services.indices) {
            val s = services[i]
            serviceItemTitles[i].text = s.title
            serviceItemStatuses[i].text = s.statusText
            if (s.isDue) {
                val dueBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10f).toFloat()
                    setColor(if (palette.isDark) 0x40EF4444 else 0x2EEF4444)
                    setStroke(dp(1f), 0xFFEF4444.toInt())
                }
                serviceItemBoxes[i].background = dueBg
                serviceItemIcons[i].setTint(0xFFEF4444.toInt())
                serviceItemTitles[i].setTextColor(if (palette.isDark) 0xFFFCA5A5.toInt() else 0xFFB91C1C.toInt())
                serviceItemStatuses[i].setTextColor(0xFFEF4444.toInt())
            } else {
                serviceItemBoxes[i].background = GlassTheme.innerDrawable(context, palette, 10f)
                serviceItemIcons[i].setTint(palette.accent)
                serviceItemTitles[i].setTextColor(palette.textPrimary)
                serviceItemStatuses[i].setTextColor(if (palette.isDark) 0xFF34D399.toInt() else 0xFF059669.toInt())
            }
        }

        refreshAlertBanner()
    }

    private fun refreshAlertBanner() {
        val dueService = tripServiceManager.getServiceItems().firstOrNull { it.isDue }
        if (dueService != null) {
            alertBannerBox.visibility = View.VISIBLE
            alertBannerTitleTv.text = "Service Maintenance Due!"
            alertBannerSubTv.text = "${dueService.title} (${dueService.statusText}) • Tap service card to reset"
            val bannerBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10f).toFloat()
                setColor(if (palette.isDark) 0x40EF4444 else 0x28EF4444)
                setStroke(dp(1f), 0xFFEF4444.toInt())
            }
            alertBannerBox.background = bannerBg
            alertBannerTitleTv.setTextColor(if (palette.isDark) 0xFFFCA5A5.toInt() else 0xFFB91C1C.toInt())
            alertBannerSubTv.setTextColor(palette.textPrimary)
            alertBannerCloseTv.visibility = View.GONE
        } else {
            alertBannerBox.visibility = View.GONE
        }
    }

    private fun refreshQuoteText() {
        val q = QuoteRepository.getCurrentQuote()
        quoteCountTv.text = "Auto • Tap"
        quoteArabicTv.text = q.arabic
        quoteBodyTv.text = "\"${q.translation}\""
        quoteRefTv.text = q.reference
        quoteCountTv.setTextColor(palette.textMuted)
        quoteArabicTv.setTextColor(palette.textPrimary)
        quoteBodyTv.setTextColor(palette.textSecondary)
        quoteRefTv.setTextColor(palette.accent)
    }

    fun refreshMediaState() {
        val playing = optimisticPlayingToggle ?: NotificationCollectorService.isCurrentlyPlaying(context)
        mediaPlayIcon.setIcon(
            if (playing) VectorIconView.IconType.PAUSE else VectorIconView.IconType.PLAY_PAUSE
        )

        val title = NotificationCollectorService.currentMediaTitle
        val artist = NotificationCollectorService.currentMediaArtist
        val hasAccess = NotificationCollectorService.isNotificationAccessGranted(context)

        if (!title.isNullOrBlank()) {
            mediaTitleTv.text = title
            val statePrefix = if (playing) "▶ " else "⏸ "
            mediaSubTv.text = statePrefix + (artist ?: "Active Audio")
        } else if (!hasAccess) {
            mediaTitleTv.text = if (playing) "Audio Playing" else "Media Control"
            mediaSubTv.text = "Tap here to grant song title access"
        } else {
            mediaTitleTv.text = if (playing) "Audio Playing" else "Media Control"
            mediaSubTv.text = "Tap to open YouTube Music"
        }
    }

    // ========================================================
    // CUSTOM THEMED POPUPS (English UI)
    // ========================================================

    private fun showAdhanPreviewDialog() {
        HarayaDialog.showCustomModal(
            context = context,
            palette = palette,
            title = "Prayer Schedule & Adhan Audio",
            subtitle = "Auto-updates every minute & whenever GPS moves across locations",
            contentBuilder = { container, dlg ->
                val enabled = adhanPlayerManager.isAdhanAlertEnabled()
                val toggleRow = HarayaDialog.buildOptionRow(
                    context = context,
                    palette = palette,
                    title = "Automatic Adhan Alert at Prayer Time",
                    subtitle = "Rotates tracks from Download/hisab (Fajr tracks exclusively for Fajr)",
                    badgeText = if (enabled) "Enabled ✓" else "Muted",
                    badgePositive = enabled
                ) {
                    adhanPlayerManager.setAdhanAlertEnabled(!enabled)
                    dlg.dismiss()
                    showAdhanPreviewDialog()
                }
                val testRegRow = HarayaDialog.buildOptionRow(
                    context = context,
                    palette = palette,
                    title = "Test Regular Adhan Popup & Audio",
                    subtitle = "Rotates randomly through non-Fajr Adhan recordings",
                    badgeText = "Play Adhan",
                    badgePositive = false
                ) {
                    dlg.dismiss()
                    adhanPlayerManager.triggerAdhanWithPopup(context, palette, "Dhuhr", "12:00", isFajr = false)
                }
                val testFajrRow = HarayaDialog.buildOptionRow(
                    context = context,
                    palette = palette,
                    title = "Test Fajr Adhan Popup & Audio",
                    subtitle = "Rotates randomly through Fajr Adhan recordings",
                    badgeText = "Play Fajr",
                    badgePositive = false
                ) {
                    dlg.dismiss()
                    adhanPlayerManager.triggerAdhanWithPopup(context, palette, "Fajr", "04:30", isFajr = true)
                }
                val travelEnabled = adhanPlayerManager.isStartupTravelPrayerEnabled()
                val travelToggleRow = HarayaDialog.buildOptionRow(
                    context = context,
                    palette = palette,
                    title = "Startup Travel Prayer Audio (20s Delay)",
                    subtitle = "Plays travel-pray.m4a 20 seconds after head unit turns on",
                    badgeText = if (travelEnabled) "Enabled ✓" else "Disabled",
                    badgePositive = travelEnabled
                ) {
                    adhanPlayerManager.setStartupTravelPrayerEnabled(!travelEnabled)
                    dlg.dismiss()
                    showAdhanPreviewDialog()
                }
                val testTravelRow = HarayaDialog.buildOptionRow(
                    context = context,
                    palette = palette,
                    title = "Play Travel Prayer Audio Now",
                    subtitle = "Test audio/travel-pray.m4a immediately",
                    badgeText = "Play Prayer",
                    badgePositive = false
                ) {
                    dlg.dismiss()
                    adhanPlayerManager.playTravelPrayerNow()
                }
                container.addView(toggleRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })
                container.addView(testRegRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })
                container.addView(testFajrRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })
                container.addView(travelToggleRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })
                container.addView(testTravelRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            actions = listOf(
                HarayaDialog.ActionButton("Close", isPrimary = true) { it.dismiss() }
            )
        )
    }

    private fun promptNotificationListenerAccess() {
        HarayaDialog.showCustomModal(
            context = context,
            palette = palette,
            title = "Enable Song Title Info",
            subtitle = "Real-time track & artist sync from YouTube Music / Spotify",
            contentBuilder = { container, _ ->
                val infoTv = TextView(context).apply {
                    text = "To display the currently playing song title and artist on the Media Control card, please grant Notification Access in Android Settings."
                    textSize = 11.5f
                    setTextColor(palette.textPrimary)
                }
                container.addView(infoTv)
            },
            actions = listOf(
                HarayaDialog.ActionButton("Cancel") { it.dismiss() },
                HarayaDialog.ActionButton("Open Music Anyway") {
                    it.dismiss()
                    onOpenMusic()
                },
                HarayaDialog.ActionButton("Grant Permission", isPrimary = true) {
                    it.dismiss()
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                }
            )
        )
    }

    private fun promptPinBeforeEditService(serviceIndex: Int) {
        val item = tripServiceManager.getServiceItems().getOrNull(serviceIndex) ?: return
        var pinInputRef: EditText? = null

        HarayaDialog.showCustomModal(
            context = context,
            palette = palette,
            title = "Verify Service PIN • ${item.title}",
            subtitle = "Enter your security PIN to edit or reset this maintenance schedule",
            contentBuilder = { container, _ ->
                val input = HarayaDialog.buildStyledInput(
                    context = context,
                    palette = palette,
                    hintText = "Enter Security PIN",
                    inputTypeFlags = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                    maxLength = 8,
                    centerText = true
                )
                pinInputRef = input
                container.addView(input, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            actions = listOf(
                HarayaDialog.ActionButton("Cancel") { it.dismiss() },
                HarayaDialog.ActionButton("Verify & Continue", isPrimary = true) { dlg ->
                    val entered = pinInputRef?.text?.toString().orEmpty()
                    if (tripServiceManager.verifyServicePin(entered)) {
                        dlg.dismiss()
                        showEditServiceDialog(serviceIndex)
                    } else {
                        Toast.makeText(context, "Incorrect PIN! You can manage your PIN in Settings.", Toast.LENGTH_LONG).show()
                    }
                }
            )
        )
    }

    private fun showEditServiceDialog(index: Int) {
        val item = tripServiceManager.getServiceItems().getOrNull(index) ?: return
        var valInputRef: EditText? = null

        HarayaDialog.showCustomModal(
            context = context,
            palette = palette,
            title = "Edit Service Schedule • ${item.title}",
            subtitle = "Standard interval: ${item.defaultTarget} ${item.unitType} (enter 0 to simulate Due Now)",
            contentBuilder = { container, _ ->
                val input = HarayaDialog.buildStyledInput(
                    context = context,
                    palette = palette,
                    hintText = "Remaining ${item.unitType} (e.g. ${item.defaultTarget})",
                    initialText = item.remainingValue.toString(),
                    inputTypeFlags = InputType.TYPE_CLASS_NUMBER,
                    maxLength = 7,
                    centerText = true
                )
                valInputRef = input
                container.addView(input, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            actions = listOf(
                HarayaDialog.ActionButton("Cancel") { it.dismiss() },
                HarayaDialog.ActionButton("Reset (${item.defaultTarget} ${item.unitType})") { dlg ->
                    tripServiceManager.updateServiceValue(item.id, item.defaultTarget)
                    refreshTripAndServices()
                    dlg.dismiss()
                },
                HarayaDialog.ActionButton("Save", isPrimary = true) { dlg ->
                    val v = valInputRef?.text?.toString()?.toIntOrNull() ?: item.defaultTarget
                    tripServiceManager.updateServiceValue(item.id, v)
                    refreshTripAndServices()
                    dlg.dismiss()
                }
            )
        )
    }

    private fun showTripHistoryPopupDialog() {
        val cur = tripServiceManager.getCurrentTripState()
        val history = tripServiceManager.getTripHistory()

        val actionList = mutableListOf<HarayaDialog.ActionButton>()
        if (history.isNotEmpty()) {
            actionList.add(
                HarayaDialog.ActionButton("Clear History", isDestructive = true) { dlg ->
                    tripServiceManager.clearTripHistory()
                    refreshTripAndServices()
                    dlg.dismiss()
                    Toast.makeText(context, "Trip history cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }
        if (cur.hasStarted) {
            actionList.add(
                HarayaDialog.ActionButton("Save Active Trip") { dlg ->
                    tripServiceManager.saveCurrentTripToHistory()
                    refreshTripAndServices()
                    dlg.dismiss()
                    Toast.makeText(context, "Active trip saved to History", Toast.LENGTH_SHORT).show()
                }
            )
        }
        actionList.add(HarayaDialog.ActionButton("Close", isPrimary = true) { it.dismiss() })

        HarayaDialog.showCustomModal(
            context = context,
            palette = palette,
            title = "Trip Summary & History",
            subtitle = "Automatically saves trips after the vehicle is parked for 15 minutes",
            contentBuilder = { container, _ ->
                // 1. Current Trip Card
                val activeCard = LinearLayout(context).apply {
                    orientation = VERTICAL
                    background = GlassTheme.innerDrawable(context, palette, 10f)
                    setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
                }
                val activeHead = TextView(context).apply {
                    text = if (cur.hasStarted) "● CURRENT TRIP (ACTIVE)" else "CURRENT TRIP"
                    textSize = 9.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setTextColor(palette.accent)
                    includeFontPadding = false
                }
                val activeBody = TextView(context).apply {
                    val h = cur.durationMinutes / 60
                    val m = cur.durationMinutes % 60
                    text = if (cur.hasStarted) {
                        String.format(
                            Locale.US,
                            "Distance: %.1f km   •   Duration: %02d:%02d   •   Avg Speed: %d km/h",
                            cur.distanceKm, h, m, cur.avgSpeedKmh
                        )
                    } else {
                        "No active movement yet (automatically starts recording when GPS detects vehicle motion)."
                    }
                    textSize = 11f
                    setTextColor(palette.textPrimary)
                    includeFontPadding = false
                }
                activeCard.addView(activeHead)
                activeCard.addView(activeBody, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(3f)
                })
                container.addView(activeCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10f)
                })

                // 2. Saved Trips List (up to 5)
                val histTitle = TextView(context).apply {
                    text = "SAVED TRIP HISTORY (${history.size}/5)"
                    textSize = 9.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setTextColor(palette.textSecondary)
                    includeFontPadding = false
                }
                container.addView(histTitle, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                })

                if (history.isEmpty()) {
                    val emptyBox = TextView(context).apply {
                        text = "No saved trips yet."
                        textSize = 11f
                        setTextColor(palette.textMuted)
                        background = GlassTheme.innerDrawable(context, palette, 10f)
                        setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    }
                    container.addView(emptyBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                } else {
                    for ((idx, item) in history.withIndex()) {
                        val row = LinearLayout(context).apply {
                            orientation = HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = GlassTheme.innerDrawable(context, palette, 9f)
                            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
                        }
                        val dateTv = TextView(context).apply {
                            text = "${idx + 1}. ${item.dateLabel}"
                            textSize = 11f
                            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                            setTextColor(palette.textPrimary)
                            fontFeatureSettings = "tnum"
                        }
                        val statsTv = TextView(context).apply {
                            text = String.format(
                                Locale.US,
                                "%.1f km  •  %d min  •  %d km/h",
                                item.distanceKm, item.durationMinutes, item.avgSpeedKmh
                            )
                            textSize = 10.5f
                            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                            setTextColor(palette.accent)
                            fontFeatureSettings = "tnum"
                            gravity = Gravity.END
                        }
                        row.addView(dateTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        row.addView(statsTv)
                        container.addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            if (idx < history.lastIndex) bottomMargin = dp(5f)
                        })
                    }
                }
            },
            actions = actionList
        )
    }

    fun applyTheme(newPalette: GlassPalette, currentMode: ThemeMode, gpsEnabled: Boolean) {
        palette = newPalette
        currentThemeMode = currentMode
        isGpsActive = gpsEnabled

        // 1-Button Theme Mode Cycler (Sun -> Moon -> A)
        val modeIconType = when (currentMode) {
            ThemeMode.LIGHT -> VectorIconView.IconType.SUN
            ThemeMode.DARK -> VectorIconView.IconType.MOON
            ThemeMode.AUTO -> VectorIconView.IconType.AUTO_A
        }
        themeCycleIcon.setIcon(modeIconType)
        themeCycleIcon.setTint(palette.selectFg)
        themeCycleBtn.background = GlassTheme.selectDrawable(context, palette, 8f)
        GlassTheme.makePressable(context, themeCycleBtn, palette, 8f)

        layoutSwitchBtn.background = GlassTheme.innerDrawable(context, palette, 8f)
        layoutSwitchBtn.setTextColor(palette.textPrimary)
        GlassTheme.makePressable(context, layoutSwitchBtn, palette, 8f)

        topBrandTitleTv.setTextColor(palette.textPrimary)
        topBrandSubTv.setTextColor(palette.textMuted)

        styleGpsPill()

        settingsTopBtn.background = GlassTheme.innerDrawable(context, palette, 8f)
        settingsTopIcon.setTint(palette.textPrimary)
        GlassTheme.makePressable(context, settingsTopBtn, palette, 8f)

        // Left Column
        mapCard.applyTheme(palette)
        mapHeaderTv.setTextColor(palette.textSecondary)
        mapShareBtn.background = GlassTheme.accentButtonDrawable(context, palette, 8f)
        mapShareBtn.setTextColor(if (palette.isDark) 0xFF0B0E11.toInt() else 0xFFFFFFFF.toInt())
        mapCanvas.applyTheme(palette)
        mapFooterTv.setTextColor(palette.textSecondary)

        prayerCard.applyTheme(palette)
        prayerHeaderTitleTv.setTextColor(palette.textSecondary)
        prayerNextTv.setTextColor(if (adhanPlayerManager.isPlayingAdhan) 0xFFEF4444.toInt() else palette.accent)

        quoteCard.applyTheme(palette)
        quoteHeaderTitleTv.setTextColor(palette.textSecondary)
        refreshQuoteText()

        // Center Column
        speedArcView.applyTheme(palette)
        refreshVehicleIdentity()
        carNameTv.setTextColor(palette.textPrimary)
        carSubTv.setTextColor(palette.textSecondary)

        mediaCard.applyTheme(palette)
        mediaTitleTv.setTextColor(palette.textPrimary)
        mediaSubTv.setTextColor(palette.textSecondary)

        mediaYtBtn.background = GlassTheme.innerDrawable(context, palette, 16f)
        mediaPrevBtn.background = GlassTheme.innerDrawable(context, palette, 16f)
        mediaPlayBtn.background = GlassTheme.accentButtonDrawable(context, palette, 18f)
        mediaNextBtn.background = GlassTheme.innerDrawable(context, palette, 16f)

        mediaYtIcon.setTint(palette.textPrimary)
        mediaPrevIcon.setTint(palette.textPrimary)
        mediaPlayIcon.setTint(if (palette.isDark) 0xFF0B0E11.toInt() else 0xFFFFFFFF.toInt())
        mediaNextIcon.setTint(palette.textPrimary)

        // Right Column
        tripHistoryCard.applyTheme(palette)
        tripHistoryHeaderTv.setTextColor(palette.textSecondary)
        tripHistoryBadgeTv.setTextColor(palette.accent)
        tripDistValTv.setTextColor(palette.textPrimary)
        tripDurValTv.setTextColor(palette.textPrimary)
        tripAvgValTv.setTextColor(palette.textPrimary)
        tripDistSubTv.setTextColor(palette.textSecondary)
        tripDurSubTv.setTextColor(palette.textSecondary)
        tripAvgSubTv.setTextColor(palette.textSecondary)
        tripFootnoteTv.setTextColor(palette.textMuted)

        serviceCard.applyTheme(palette)
        serviceHeaderTv.setTextColor(palette.textSecondary)
        servicePinLockIcon.setTint(palette.textMuted)
        servicePinBadgeTv.setTextColor(palette.textMuted)

        weatherCard.applyTheme(palette)
        weatherHeaderTv.setTextColor(palette.textSecondary)
        weatherSourceTv.setTextColor(palette.textMuted)
        weatherIcon.setTint(palette.accent)
        weatherTempTv.setTextColor(palette.textPrimary)
        weatherDescTv.setTextColor(palette.textPrimary)

        // Embedded Interactive Map Stage
        embeddedMapCard.applyTheme(palette)
        embeddedMapCanvas.applyTheme(palette)
        embeddedMapHeaderBar.background = GlassTheme.overDrawable(context, palette, 12f)
        embeddedMapTitleTv.setTextColor(palette.textPrimary)
        embeddedMapSubTv.setTextColor(palette.textSecondary)
        embeddedMapShareBtn.background = GlassTheme.accentButtonDrawable(context, palette, 8f)
        embeddedMapShareBtn.setTextColor(if (palette.isDark) 0xFF0B0E11.toInt() else 0xFFFFFFFF.toInt())
        GlassTheme.makePressable(context, embeddedMapShareBtn, palette, 8f)

        embeddedMapExternalBtn.background = GlassTheme.innerDrawable(context, palette, 8f)
        embeddedMapExternalBtn.setTextColor(palette.textPrimary)
        GlassTheme.makePressable(context, embeddedMapExternalBtn, palette, 8f)

        embeddedMapBackHomeBtn.background = GlassTheme.innerDrawable(context, palette, 8f)
        embeddedMapBackHomeBtn.setTextColor(palette.textSecondary)
        GlassTheme.makePressable(context, embeddedMapBackHomeBtn, palette, 8f)

        for (pair in listOf(
            embeddedMapZoomInBtn to embeddedMapZoomInIcon,
            embeddedMapZoomOutBtn to embeddedMapZoomOutIcon,
            embeddedMapRecenterBtn to embeddedMapRecenterIcon
        )) {
            pair.first.background = GlassTheme.overDrawable(context, palette, 10f)
            pair.second.setTint(palette.textPrimary)
            GlassTheme.makePressable(context, pair.first, palette, 10f)
        }

        // Bottom Dock
        bottomDockCard.applyTheme(palette)
        styleDockTabs()

        refreshTripAndServices()
        refreshMediaState()
    }

    private fun dp(value: Float): Int = GlassTheme.dp(context, value)
}
