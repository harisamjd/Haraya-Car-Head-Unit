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
    private val onSwitchThemeMode: (ThemeMode) -> Unit,
    private val onSwitchToClassicLayout: () -> Unit,
    private val onOpenMaps: () -> Unit,
    private val onShareLiveLocation: () -> Unit,
    private val onGpsPillClicked: () -> Unit,
    private val onOpenMusic: () -> Unit,
    private val onOpenYouTube: () -> Unit,
    private val onOpenPhone: () -> Unit,
    private val onOpenObdScreen: () -> Unit,
    private val onOpenSettingsDialog: () -> Unit,
    private val onOpenAppsScreen: () -> Unit
) : LinearLayout(context) {

    private var palette: GlassPalette = GlassTheme.Dark
    private var quoteIdx: Int = 0
    private var seatbeltBannerDismissed: Boolean = false
    private var isGpsActive: Boolean = false
    private var optimisticPlayingToggle: Boolean? = null

    // Top Bar
    private val topBarLayout: LinearLayout
    private val modeTerangBtn: TextView
    private val modeGelapBtn: TextView
    private val modeAutoBtn: TextView
    private val layoutSwitchBtn: TextView
    private val topBrandTitleTv: TextView
    private val topBrandSubTv: TextView
    private val gpsPillTv: TextView
    private val settingsTopBtn: TextView

    // Left Column: (1) Peta Live + (2) Jadwal Sholat + (3) Kutipan Ayat/Doa Harian
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
    private val quoteBodyTv: TextView
    private val quoteRefTv: TextView

    // Center Column: Alert Banner + Arc Speedometer + Car Image + Media Control
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

    // Right Column: (1) Ringkasan & Riwayat Perjalanan + (2) Jadwal Servis (PIN) + (3) Cuaca
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

    // Bottom Dock Bar
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

        val themeSegmentBox = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        modeTerangBtn = buildModePill("Terang") { onSwitchThemeMode(ThemeMode.LIGHT) }
        modeGelapBtn = buildModePill("Gelap") { onSwitchThemeMode(ThemeMode.DARK) }
        modeAutoBtn = buildModePill("Auto") { onSwitchThemeMode(ThemeMode.AUTO) }
        themeSegmentBox.addView(modeTerangBtn)
        themeSegmentBox.addView(modeGelapBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(4f) })
        themeSegmentBox.addView(modeAutoBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(4f) })

        layoutSwitchBtn = TextView(context).apply {
            text = "Mode Klasik"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            setOnClickListener { onSwitchToClassicLayout() }
        }

        val leftTopGroup = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        leftTopGroup.addView(themeSegmentBox)
        leftTopGroup.addView(layoutSwitchBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(8f)
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
            text = "● GPS Aktif"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            setOnClickListener { onGpsPillClicked() }
        }
        settingsTopBtn = TextView(context).apply {
            text = "Setelan"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            setOnClickListener { onOpenSettingsDialog() }
        }
        rightTopGroup.addView(gpsPillTv)
        rightTopGroup.addView(settingsTopBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(6f)
        })

        topBarLayout.addView(leftTopGroup, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f))
        topBarLayout.addView(brandCenterCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f))
        topBarLayout.addView(rightTopGroup, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f))

        addView(topBarLayout, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(5f)
        })

        // ==========================================
        // 2. MAIN 3-COLUMN COCKPIT AREA
        // ==========================================
        val mainColumnsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }

        val cardPadH = dp(12f)
        val cardPadV = dp(8f)

        // --- KOLOM KIRI: (1) Peta Live + (2) Jadwal Sholat + (3) Kutipan Ayat/Doa ---
        val leftCol = LinearLayout(context).apply {
            orientation = VERTICAL
        }

        // 2A. Kartu Peta & Share Lokasi
        mapCard = GlassCardView(context)
        mapCard.setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        val mapCol = LinearLayout(context).apply { orientation = VERTICAL }
        val mapHeaderRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        mapHeaderTv = TextView(context).apply {
            text = "PETA & LOKASI"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.04f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mapShareBtn = TextView(context).apply {
            text = "Bagikan Live"
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
            setOnClickListener { onOpenMaps() }
        }
        mapViewport.addView(mapCanvas, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mapCol.addView(mapViewport, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(4f)
        })

        mapFooterTv = TextView(context).apply {
            text = "Peta Live OpenStreetMap • Ketuk peta untuk navigasi"
            textSize = 8.5f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mapCol.addView(mapFooterTv)
        mapCard.addView(mapCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 2B. Kartu Jadwal Sholat 5 Waktu (Bersih & Rapi)
        prayerCard = GlassCardView(context)
        prayerCard.setPadding(cardPadH, dp(7f), cardPadH, dp(7f))
        val prayerCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val prayerHeadRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prayerHeaderTitleTv = TextView(context).apply {
            text = "JADWAL SHOLAT"
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
        val pNames = listOf("Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")
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

        // 2C. Kartu Kutipan Ayat / Doa Muslim (Di bawah Jadwal Sholat)
        quoteCard = GlassCardView(context)
        quoteCard.setPadding(cardPadH, dp(7f), cardPadH, dp(7f))
        quoteCard.setOnClickListener {
            quoteIdx = (quoteIdx + 1) % QuoteRepository.count()
            refreshQuoteText()
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
            text = "KUTIPAN & DOA HARIAN"
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.04f
            includeFontPadding = false
        }
        quoteCountTv = TextView(context).apply {
            textSize = 8f
            includeFontPadding = false
            gravity = Gravity.END
        }
        quoteHeadRow.addView(quoteHeaderTitleTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        quoteHeadRow.addView(quoteCountTv)
        quoteCol.addView(quoteHeadRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(2f)
        })

        quoteBodyTv = TextView(context).apply {
            textSize = 9f
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        quoteRefTv = TextView(context).apply {
            textSize = 8.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
        }
        quoteCol.addView(quoteBodyTv)
        quoteCol.addView(quoteRefTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2f)
        })
        quoteCard.addView(quoteCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        leftCol.addView(mapCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.45f).apply { bottomMargin = dp(6f) })
        leftCol.addView(prayerCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.28f).apply { bottomMargin = dp(6f) })
        leftCol.addView(quoteCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.27f))

        mainColumnsRow.addView(leftCol, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.29f).apply {
            rightMargin = dp(8f)
        })

        // --- KOLOM TENGAH (Banner + Busur Speedometer + Suzuki Baleno + Kontrol Media) ---
        val centerCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        alertBannerBox = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
        }
        val alertTextCol = LinearLayout(context).apply { orientation = VERTICAL }
        alertBannerTitleTv = TextView(context).apply {
            text = "Kenakan Sabuk Pengaman"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
        }
        alertBannerSubTv = TextView(context).apply {
            text = "Pastikan sabuk pengaman terpasang demi keselamatan perjalanan"
            textSize = 8.5f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        alertTextCol.addView(alertBannerTitleTv)
        alertTextCol.addView(alertBannerSubTv)

        alertBannerCloseTv = TextView(context).apply {
            text = "Tutup"
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

        // Area Instrumen Tengah: Busur Speedometer di atas, Mobil di separuh bawah
        val centerStageFrame = FrameLayout(context)
        speedArcView = CenterSpeedArcView(context)
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
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(105f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
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

        // Kartu Kontrol Media di Bawah Mobil
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
            text = "Kontrol Media"
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mediaSubTv = TextView(context).apply {
            text = "Ketuk untuk membuka YouTube Music"
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

        // --- KOLOM KANAN: (1) Ringkasan & Riwayat Perjalanan + (2) Jadwal Servis (PIN) + (3) Cuaca ---
        val rightCol = LinearLayout(context).apply {
            orientation = VERTICAL
        }

        // 3A. Kartu Ringkasan & Riwayat Perjalanan (Ketuk untuk Popup Riwayat Lengkap)
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
            text = "RIWAYAT PERJALANAN"
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.04f
            includeFontPadding = false
        }
        tripHistoryBadgeTv = TextView(context).apply {
            text = "0/5 • Detail"
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
        val m1 = buildTripMetric("0,0", "km")
        val m2 = buildTripMetric("00:00", "jam : mnt")
        val m3 = buildTripMetric("0", "km/j rata-rata")
        tripDistValTv = m1.first; tripDistSubTv = m1.second
        tripDurValTv = m2.first; tripDurSubTv = m2.second
        tripAvgValTv = m3.first; tripAvgSubTv = m3.second
        tripCol.addView(tripMetricsRow)

        tripFootnoteTv = TextView(context).apply {
            text = "Ketuk untuk melihat daftar Riwayat Perjalanan"
            textSize = 8f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        tripCol.addView(tripFootnoteTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2f)
        })
        tripHistoryCard.addView(tripCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 3B. Kartu Jadwal Servis (Grid 2x2 Horizontal per Item agar 100% Terbaca tanpa terpotong)
        serviceCard = GlassCardView(context)
        serviceCard.setPadding(cardPadH, dp(7f), cardPadH, dp(7f))
        val svcCol = LinearLayout(context).apply { orientation = VERTICAL }
        val svcHeadRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        serviceHeaderTv = TextView(context).apply {
            text = "JADWAL SERVIS"
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.04f
            includeFontPadding = false
        }
        servicePinBadgeTv = TextView(context).apply {
            text = "PIN 0000"
            textSize = 8f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
        }
        svcHeadRow.addView(serviceHeaderTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        svcHeadRow.addView(servicePinBadgeTv)
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
                // Layout Horizontal: Ikon di kiri, Judul + Sisa KM/Hari di kanan (bebas kepotong!)
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

        // 3C. Kartu Cuaca Open-Meteo (Horizontal sejajar agar kondisi cuaca 100% terbaca utuh)
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
            text = "CUACA"
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
            text = "Cerah Berawan"
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

        addView(mainColumnsRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(5f)
        })

        // ==========================================
        // 3. BOTTOM NAVIGATION DOCK BAR
        // ==========================================
        bottomDockCard = GlassCardView(context)
        bottomDockCard.setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
        val dockRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dockSpecs = listOf(
            Triple(VectorIconView.IconType.HOME, "BERANDA") { },
            Triple(VectorIconView.IconType.MUSIC, "MEDIA") { onOpenMusic() },
            Triple(VectorIconView.IconType.PHONE, "TELEPON") { onOpenPhone() },
            Triple(VectorIconView.IconType.GAUGE, "OBD & MESIN") { onOpenObdScreen() },
            Triple(VectorIconView.IconType.GEAR_COG, "SETELAN") { onOpenSettingsDialog() },
            Triple(VectorIconView.IconType.APPS_GRID, "APLIKASI") { onOpenAppsScreen() }
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

    private fun buildModePill(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(9f), dp(4f), dp(9f), dp(4f))
            setOnClickListener { onClick() }
        }
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

    fun updateGpsStatus(enabled: Boolean) {
        isGpsActive = enabled
        styleGpsPill()
    }

    private fun styleGpsPill() {
        if (isGpsActive) {
            gpsPillTv.text = "● GPS Aktif"
            gpsPillTv.background = GlassTheme.innerDrawable(context, palette, 8f)
            gpsPillTv.setTextColor(if (palette.isDark) 0xFF34D399.toInt() else 0xFF059669.toInt())
        } else {
            gpsPillTv.text = "○ GPS Nonaktif"
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

        val hrs = prayers.nextPrayerRemainingMinutes / 60
        val mins = prayers.nextPrayerRemainingMinutes % 60
        val remStr = if (hrs > 0) "${hrs}j ${mins}m" else "${mins}m"
        prayerNextTv.text = "${prayers.nextPrayerName} ${prayers.nextPrayerTime} (-$remStr)"

        val times = listOf(prayers.subuh, prayers.dzuhur, prayers.ashar, prayers.maghrib, prayers.isya)
        val names = listOf("Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")
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
        refreshQuoteText()
        refreshMediaState()
    }

    fun updateLocationInfo(lat: Double, lon: Double, placeTitle: String, streetDetail: String, coordSub: String) {
        mapHeaderTv.text = placeTitle.uppercase(Locale.getDefault())
        val cleanStreet = streetDetail.trim()
        mapFooterTv.text = if (cleanStreet.isNotEmpty() && !cleanStreet.equals("Mencari Jalan...", ignoreCase = true)) {
            "$cleanStreet • $coordSub"
        } else {
            coordSub
        }
        mapCanvas.updateLocation(lat, lon, cleanStreet.ifBlank { placeTitle })
    }

    fun updateWeather(weather: WeatherState) {
        weatherTempTv.text = "${weather.tempCelsius}°C"
        val cleanCondition = weather.conditionLabel.split("•").firstOrNull()?.trim().orEmpty()
        weatherDescTv.text = cleanCondition.ifEmpty { weather.conditionLabel }
    }

    fun refreshVehicleIdentity() {
        carNameTv.text = tripServiceManager.getVehicleName()
        carSubTv.text = tripServiceManager.getVehicleSubtitle()
    }

    fun refreshTripAndServices() {
        val cur = tripServiceManager.getCurrentTripState()
        val history = tripServiceManager.getTripHistory()
        tripHistoryBadgeTv.text = "${history.size}/5 • Detail"

        if (cur.hasStarted) {
            tripDistValTv.text = String.format(Locale("id", "ID"), "%.1f", cur.distanceKm)
            val h = cur.durationMinutes / 60
            val m = cur.durationMinutes % 60
            tripDurValTv.text = String.format(Locale.US, "%02d:%02d", h, m)
            tripAvgValTv.text = cur.avgSpeedKmh.toString()
            tripFootnoteTv.text = "Perjalanan aktif • Ketuk kartu untuk Riwayat"
        } else if (history.isNotEmpty()) {
            val latest = history.first()
            tripDistValTv.text = String.format(Locale("id", "ID"), "%.1f", latest.distanceKm)
            val h = latest.durationMinutes / 60
            val m = latest.durationMinutes % 60
            tripDurValTv.text = String.format(Locale.US, "%02d:%02d", h, m)
            tripAvgValTv.text = latest.avgSpeedKmh.toString()
            tripFootnoteTv.text = "Terakhir (${latest.dateLabel}) • Ketuk untuk semua riwayat"
        } else {
            tripDistValTv.text = "0,0"
            tripDurValTv.text = "00:00"
            tripAvgValTv.text = "0"
            tripFootnoteTv.text = "Dimulai saat GPS gerak • Ketuk untuk lihat riwayat"
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
            alertBannerTitleTv.text = "Jadwal Servis Jatuh Tempo!"
            alertBannerSubTv.text = "${dueService.title} (${dueService.statusText}) • Ketuk kartu servis untuk reset"
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
        } else if (!seatbeltBannerDismissed) {
            alertBannerBox.visibility = View.VISIBLE
            alertBannerTitleTv.text = "Kenakan Sabuk Pengaman"
            alertBannerSubTv.text = "Jangan lupa pasang seatbelt demi keselamatan perjalanan Anda"
            val bannerBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10f).toFloat()
                setColor(if (palette.isDark) 0x33F59E0B else 0x22F59E0B)
                setStroke(dp(1f), 0xFFF59E0B.toInt())
            }
            alertBannerBox.background = bannerBg
            alertBannerTitleTv.setTextColor(if (palette.isDark) 0xFFFCD34D.toInt() else 0xFFB45309.toInt())
            alertBannerSubTv.setTextColor(palette.textPrimary)
            alertBannerCloseTv.visibility = View.VISIBLE
            alertBannerCloseTv.setTextColor(palette.textSecondary)
        } else {
            alertBannerBox.visibility = View.GONE
        }
    }

    private fun refreshQuoteText() {
        val q = QuoteRepository.getQuote(quoteIdx)
        quoteCountTv.text = "${quoteIdx + 1}/${QuoteRepository.count()} • Ketuk"
        quoteBodyTv.text = "\"${q.translation}\""
        quoteRefTv.text = q.reference
        quoteCountTv.setTextColor(palette.textMuted)
        quoteBodyTv.setTextColor(palette.textPrimary)
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
            mediaSubTv.text = statePrefix + (artist ?: "Media Aktif")
        } else if (!hasAccess) {
            mediaTitleTv.text = if (playing) "Musik Sedang Berputar" else "Kontrol Media"
            mediaSubTv.text = "Ketuk di sini untuk izinkan akses info judul lagu"
        } else {
            mediaTitleTv.text = if (playing) "Musik Sedang Berputar" else "Kontrol Media"
            mediaSubTv.text = "Ketuk untuk membuka YouTube Music"
        }
    }

    // ========================================================
    // CUSTOM THEMED POPUPS (Menggantikan AlertDialog Bawaan)
    // ========================================================

    private fun promptNotificationListenerAccess() {
        HarayaDialog.showCustomModal(
            context = context,
            palette = palette,
            title = "Aktifkan Info Judul Lagu",
            subtitle = "Sinkronisasi judul & artis secara real-time dari YouTube Music / Spotify",
            contentBuilder = { container, _ ->
                val infoTv = TextView(context).apply {
                    text = "Agar Haraya Dock dapat menampilkan judul lagu dan penyanyi yang sedang diputar di kartu Kontrol Media, berikan izin Akses Notifikasi pada pengaturan sistem Android."
                    textSize = 11.5f
                    setTextColor(palette.textPrimary)
                }
                container.addView(infoTv)
            },
            actions = listOf(
                HarayaDialog.ActionButton("Batal") { it.dismiss() },
                HarayaDialog.ActionButton("Tetap Buka Musik") {
                    it.dismiss()
                    onOpenMusic()
                },
                HarayaDialog.ActionButton("Buka Pengaturan Izin", isPrimary = true) {
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
            title = "Verifikasi PIN Servis • ${item.title}",
            subtitle = "Masukkan PIN keamanan untuk mengatur atau mereset jadwal servis",
            contentBuilder = { container, _ ->
                val input = HarayaDialog.buildStyledInput(
                    context = context,
                    palette = palette,
                    hintText = "Masukkan PIN (Default: 0000)",
                    inputTypeFlags = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                    maxLength = 8,
                    centerText = true
                )
                pinInputRef = input
                container.addView(input, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            actions = listOf(
                HarayaDialog.ActionButton("Batal") { it.dismiss() },
                HarayaDialog.ActionButton("Verifikasi & Lanjut", isPrimary = true) { dlg ->
                    val entered = pinInputRef?.text?.toString().orEmpty()
                    if (tripServiceManager.verifyServicePin(entered)) {
                        dlg.dismiss()
                        showEditServiceDialog(serviceIndex)
                    } else {
                        Toast.makeText(context, "PIN tidak sesuai! PIN standar adalah 0000 (bisa diubah di menu Setelan).", Toast.LENGTH_LONG).show()
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
            title = "Atur Jadwal Servis • ${item.title}",
            subtitle = "Target standar: ${item.defaultTarget} ${item.unitType} (isi 0 untuk simulasi Jatuh Tempo)",
            contentBuilder = { container, _ ->
                val input = HarayaDialog.buildStyledInput(
                    context = context,
                    palette = palette,
                    hintText = "Sisa ${item.unitType} (mis. ${item.defaultTarget})",
                    initialText = item.remainingValue.toString(),
                    inputTypeFlags = InputType.TYPE_CLASS_NUMBER,
                    maxLength = 7,
                    centerText = true
                )
                valInputRef = input
                container.addView(input, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            actions = listOf(
                HarayaDialog.ActionButton("Batal") { it.dismiss() },
                HarayaDialog.ActionButton("Reset (${item.defaultTarget} ${item.unitType})") { dlg ->
                    tripServiceManager.updateServiceValue(item.id, item.defaultTarget)
                    refreshTripAndServices()
                    dlg.dismiss()
                },
                HarayaDialog.ActionButton("Simpan", isPrimary = true) { dlg ->
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
                HarayaDialog.ActionButton("Hapus Riwayat", isDestructive = true) { dlg ->
                    tripServiceManager.clearTripHistory()
                    refreshTripAndServices()
                    dlg.dismiss()
                    Toast.makeText(context, "Riwayat perjalanan dibersihkan", Toast.LENGTH_SHORT).show()
                }
            )
        }
        if (cur.hasStarted) {
            actionList.add(
                HarayaDialog.ActionButton("Simpan Perjalanan Aktif") { dlg ->
                    tripServiceManager.saveCurrentTripToHistory()
                    refreshTripAndServices()
                    dlg.dismiss()
                    Toast.makeText(context, "Perjalanan disimpan ke Riwayat", Toast.LENGTH_SHORT).show()
                }
            )
        }
        actionList.add(HarayaDialog.ActionButton("Tutup", isPrimary = true) { it.dismiss() })

        HarayaDialog.showCustomModal(
            context = context,
            palette = palette,
            title = "Ringkasan & Riwayat Perjalanan",
            subtitle = "Otomatis menyimpan perjalanan setelah mobil parkir 15 menit",
            contentBuilder = { container, _ ->
                // 1. Kartu Perjalanan Saat Ini
                val activeCard = LinearLayout(context).apply {
                    orientation = VERTICAL
                    background = GlassTheme.innerDrawable(context, palette, 10f)
                    setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
                }
                val activeHead = TextView(context).apply {
                    text = if (cur.hasStarted) "● PERJALANAN SAAT INI (AKTIF)" else "PERJALANAN SAAT INI"
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
                            Locale("id", "ID"),
                            "Jarak Tempuh: %.1f km   •   Durasi: %02d:%02d   •   Rata-rata: %d km/jam",
                            cur.distanceKm, h, m, cur.avgSpeedKmh
                        )
                    } else {
                        "Belum ada pergerakan aktif (otomatis menghitung saat GPS mendeteksi kendaraan berjalan)."
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

                // 2. Daftar 5 Riwayat Tersimpan
                val histTitle = TextView(context).apply {
                    text = "DAFTAR RIWAYAT TERSIMPAN (${history.size}/5)"
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
                        text = "Belum ada riwayat perjalanan tersimpan."
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
                                Locale("id", "ID"),
                                "%.1f km  •  %d mnt  •  %d km/jam",
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
        isGpsActive = gpsEnabled

        fun styleModeBtn(btn: TextView, active: Boolean) {
            if (active) {
                btn.background = GlassTheme.selectDrawable(context, palette, 8f)
                btn.setTextColor(palette.selectFg)
            } else {
                btn.background = GlassTheme.innerDrawable(context, palette, 8f)
                btn.setTextColor(palette.textSecondary)
            }
        }
        styleModeBtn(modeTerangBtn, currentMode == ThemeMode.LIGHT)
        styleModeBtn(modeGelapBtn, currentMode == ThemeMode.DARK)
        styleModeBtn(modeAutoBtn, currentMode == ThemeMode.AUTO)

        layoutSwitchBtn.background = GlassTheme.innerDrawable(context, palette, 8f)
        layoutSwitchBtn.setTextColor(palette.textPrimary)
        GlassTheme.makePressable(context, layoutSwitchBtn, palette, 8f)

        topBrandTitleTv.setTextColor(palette.textPrimary)
        topBrandSubTv.setTextColor(palette.textMuted)

        styleGpsPill()

        settingsTopBtn.background = GlassTheme.innerDrawable(context, palette, 8f)
        settingsTopBtn.setTextColor(palette.textPrimary)
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
        prayerNextTv.setTextColor(palette.accent)

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
        servicePinBadgeTv.setTextColor(palette.textMuted)

        weatherCard.applyTheme(palette)
        weatherHeaderTv.setTextColor(palette.textSecondary)
        weatherSourceTv.setTextColor(palette.textMuted)
        weatherIcon.setTint(palette.accent)
        weatherTempTv.setTextColor(palette.textPrimary)
        weatherDescTv.setTextColor(palette.textPrimary)

        // Bottom Dock
        bottomDockCard.applyTheme(palette)
        for (i in dockTabBoxes.indices) {
            val active = (i == 0)
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

        refreshTripAndServices()
        refreshMediaState()
    }

    private fun dp(value: Float): Int = GlassTheme.dp(context, value)
}
