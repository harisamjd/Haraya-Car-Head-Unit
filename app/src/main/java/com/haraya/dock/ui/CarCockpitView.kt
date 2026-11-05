package com.haraya.dock.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.text.InputFilter
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
        setPadding(pad, dp(8f), pad, dp(8f))

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
            textSize = 12.5f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
        }
        topBrandSubTv = TextView(context).apply {
            text = "Vehicle Smart Cockpit"
            textSize = 8.5f
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
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            setOnClickListener { onGpsPillClicked() }
        }
        settingsTopBtn = TextView(context).apply {
            text = "Setelan"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
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
            bottomMargin = dp(6f)
        })

        // ==========================================
        // 2. MAIN 3-COLUMN COCKPIT AREA
        // ==========================================
        val mainColumnsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }

        val cardPadH = dp(14f)
        val cardPadV = dp(10f)

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
            letterSpacing = 0.05f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mapShareBtn = TextView(context).apply {
            text = "Bagikan Live"
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8f), dp(3f), dp(8f), dp(3f))
            setOnClickListener { onShareLiveLocation() }
        }
        mapHeaderRow.addView(mapHeaderTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6f)
        })
        mapHeaderRow.addView(mapShareBtn)
        mapCol.addView(mapHeaderRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6f)
        })

        mapViewport = FrameLayout(context)
        GlassTheme.clipRound(context, mapViewport, 12f)
        mapCanvas = MapCanvasView(context).apply {
            setOnClickListener { onOpenMaps() }
        }
        mapViewport.addView(mapCanvas, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mapCol.addView(mapViewport, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(5f)
        })

        mapFooterTv = TextView(context).apply {
            text = "Peta Live OpenStreetMap • Ketuk peta untuk navigasi"
            textSize = 8.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mapCol.addView(mapFooterTv)
        mapCard.addView(mapCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 2B. Kartu Jadwal Sholat 5 Waktu (Bersih & Rapi)
        prayerCard = GlassCardView(context)
        prayerCard.setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
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
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.05f
        }
        prayerNextTv = TextView(context).apply {
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            gravity = Gravity.END
        }
        prayerHeadRow.addView(prayerHeaderTitleTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        prayerHeadRow.addView(prayerNextTv)
        prayerCol.addView(prayerHeadRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6f)
        })

        val prayerStrip = LinearLayout(context).apply { orientation = HORIZONTAL }
        val pNames = listOf("Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")
        for (i in pNames.indices) {
            val slot = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2f), dp(5f), dp(2f), dp(5f))
            }
            val nTv = TextView(context).apply {
                text = pNames[i]
                textSize = 8.5f
                gravity = Gravity.CENTER
            }
            val tTv = TextView(context).apply {
                text = "--:--"
                textSize = 10.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                fontFeatureSettings = "tnum"
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
        quoteCard.setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
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
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.05f
        }
        quoteCountTv = TextView(context).apply {
            textSize = 8.5f
            gravity = Gravity.END
        }
        quoteHeadRow.addView(quoteHeaderTitleTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        quoteHeadRow.addView(quoteCountTv)
        quoteCol.addView(quoteHeadRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(3f)
        })

        quoteBodyTv = TextView(context).apply {
            textSize = 9.5f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        quoteRefTv = TextView(context).apply {
            textSize = 8.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
        }
        quoteCol.addView(quoteBodyTv)
        quoteCol.addView(quoteRefTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2f)
        })
        quoteCard.addView(quoteCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        leftCol.addView(mapCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.46f).apply { bottomMargin = dp(6f) })
        leftCol.addView(prayerCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.28f).apply { bottomMargin = dp(6f) })
        leftCol.addView(quoteCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.26f))

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
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        alertBannerSubTv = TextView(context).apply {
            text = "Pastikan sabuk pengaman terpasang demi keselamatan perjalanan"
            textSize = 8.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        alertTextCol.addView(alertBannerTitleTv)
        alertTextCol.addView(alertBannerSubTv)

        alertBannerCloseTv = TextView(context).apply {
            text = "Tutup"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
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

        // Area Instrumen Tengah: Busur Speedometer di atas, Mobil di separuh bawah (tidak saling menutupi)
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
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        carSubTv = TextView(context).apply {
            text = tripServiceManager.getVehicleSubtitle()
            textSize = 9f
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
        mediaCard.setPadding(dp(14f), dp(9f), dp(14f), dp(9f))
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
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mediaSubTv = TextView(context).apply {
            text = "Ketuk untuk membuka YouTube Music"
            textSize = 9f
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
        val playPair = buildMediaCircleBtn(VectorIconView.IconType.PLAY_PAUSE, 38f, 17f) {
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
        tripHistoryCard.setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
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
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.05f
        }
        tripHistoryBadgeTv = TextView(context).apply {
            text = "0/5 • Detail"
            textSize = 8.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        tripHeaderRow.addView(tripHistoryHeaderTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tripHeaderRow.addView(tripHistoryBadgeTv)
        tripCol.addView(tripHeaderRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4f)
        })

        val tripMetricsRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        fun buildTripMetric(unitLabel: String): Pair<TextView, TextView> {
            val box = LinearLayout(context).apply { orientation = VERTICAL }
            val vTv = TextView(context).apply {
                text = "—"
                textSize = 14.5f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                fontFeatureSettings = "tnum"
                includeFontPadding = false
            }
            val uTv = TextView(context).apply {
                text = unitLabel
                textSize = 8.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            box.addView(vTv)
            box.addView(uTv)
            tripMetricsRow.addView(box, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return vTv to uTv
        }
        val m1 = buildTripMetric("km")
        val m2 = buildTripMetric("jam : mnt")
        val m3 = buildTripMetric("km/j rata-rata")
        tripDistValTv = m1.first; tripDistSubTv = m1.second
        tripDurValTv = m2.first; tripDurSubTv = m2.second
        tripAvgValTv = m3.first; tripAvgSubTv = m3.second
        tripCol.addView(tripMetricsRow)

        tripFootnoteTv = TextView(context).apply {
            text = "Ketuk untuk melihat daftar Riwayat Perjalanan"
            textSize = 8.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        tripCol.addView(tripFootnoteTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(3f)
        })
        tripHistoryCard.addView(tripCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 3B. Kartu Jadwal Servis (Grid 2x2 dengan proteksi PIN)
        serviceCard = GlassCardView(context)
        serviceCard.setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        val svcCol = LinearLayout(context).apply { orientation = VERTICAL }
        val svcHeadRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        serviceHeaderTv = TextView(context).apply {
            text = "JADWAL SERVIS"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.05f
        }
        servicePinBadgeTv = TextView(context).apply {
            text = "🔒 PIN"
            textSize = 8.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        svcHeadRow.addView(serviceHeaderTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        svcHeadRow.addView(servicePinBadgeTv)
        svcCol.addView(svcHeadRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6f)
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
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(6f), dp(5f), dp(6f), dp(5f))
                    setOnClickListener { promptPinBeforeEditService(idx) }
                }
                val ic = VectorIconView(context, svcIcons[idx])
                val titleTv = TextView(context).apply {
                    textSize = 10f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    gravity = Gravity.CENTER
                    maxLines = 1
                }
                val statTv = TextView(context).apply {
                    textSize = 8.5f
                    fontFeatureSettings = "tnum"
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                itemBox.addView(ic, LayoutParams(dp(15f), dp(15f)))
                itemBox.addView(titleTv, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(2f)
                })
                itemBox.addView(statTv)

                serviceItemBoxes.add(itemBox)
                serviceItemIcons.add(ic)
                serviceItemTitles.add(titleTv)
                serviceItemStatuses.add(statTv)

                rBox.addView(itemBox, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (colIdx == 0) rightMargin = dp(6f)
                })
            }
            svcCol.addView(rBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                if (rowIdx == 0) bottomMargin = dp(6f)
            })
        }
        serviceCard.addView(svcCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 3C. Kartu Cuaca Open-Meteo (Tanpa tulisan LIVE)
        weatherCard = GlassCardView(context)
        weatherCard.setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        val wCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        weatherHeaderTv = TextView(context).apply {
            text = "CUACA"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.05f
        }
        wCol.addView(weatherHeaderTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(3f)
        })

        val wBodyRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        weatherIcon = VectorIconView(context, VectorIconView.IconType.CLOUD_SUN)
        wBodyRow.addView(weatherIcon, LayoutParams(dp(24f), dp(24f)).apply {
            rightMargin = dp(10f)
        })
        val wTextCol = LinearLayout(context).apply { orientation = VERTICAL }
        weatherTempTv = TextView(context).apply {
            text = "28°C"
            textSize = 17f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }
        weatherDescTv = TextView(context).apply {
            text = "Cerah Berawan • Open-Meteo"
            textSize = 8.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        wTextCol.addView(weatherTempTv)
        wTextCol.addView(weatherDescTv)
        wBodyRow.addView(wTextCol)
        wCol.addView(wBodyRow)
        weatherCard.addView(wCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        rightCol.addView(tripHistoryCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.30f).apply { bottomMargin = dp(6f) })
        rightCol.addView(serviceCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.48f).apply { bottomMargin = dp(6f) })
        rightCol.addView(weatherCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.22f))

        mainColumnsRow.addView(rightCol, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.29f))

        addView(mainColumnsRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(6f)
        })

        // ==========================================
        // 3. BOTTOM NAVIGATION DOCK BAR
        // ==========================================
        bottomDockCard = GlassCardView(context)
        bottomDockCard.setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
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
                setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
                setOnClickListener { spec.third.invoke() }
            }
            val ic = VectorIconView(context, spec.first)
            val lbl = TextView(context).apply {
                text = spec.second
                textSize = 10f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                letterSpacing = 0.05f
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
            gpsPillTv.setTextColor(if (palette.isDark) 0xFF4ADE80.toInt() else 0xFF15803D.toInt())
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
        mapFooterTv.text = coordSub
        mapCanvas.updateLocation(lat, lon, streetDetail.ifBlank { placeTitle })
    }

    fun updateWeather(weather: WeatherState) {
        weatherTempTv.text = "${weather.tempCelsius}°C"
        weatherDescTv.text = weather.conditionLabel
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
            tripDistValTv.text = "—"
            tripDurValTv.text = "—"
            tripAvgValTv.text = "—"
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
                    setColor(if (palette.isDark) 0x44EF4444 else 0x33EF4444)
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
                serviceItemStatuses[i].setTextColor(if (palette.isDark) 0xFF4ADE80.toInt() else 0xFF15803D.toInt())
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
                cornerRadius = dp(12f).toFloat()
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
                cornerRadius = dp(12f).toFloat()
                setColor(if (palette.isDark) 0x38F59E0B else 0x26F59E0B)
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

    private fun promptNotificationListenerAccess() {
        val themeStyle = if (palette.isDark) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        AlertDialog.Builder(context, themeStyle)
            .setTitle("Aktifkan Info Judul Lagu")
            .setMessage("Agar Haraya Dock dapat menampilkan judul lagu & nama penyanyi dari YouTube Music / Spotify secara real-time, aktifkan izin Akses Notifikasi untuk Haraya Dock.")
            .setPositiveButton("Buka Pengaturan Izin") { _, _ ->
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
            .setNeutralButton("Tetap Buka Musik") { _, _ ->
                onOpenMusic()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun promptPinBeforeEditService(serviceIndex: Int) {
        val item = tripServiceManager.getServiceItems().getOrNull(serviceIndex) ?: return
        val themeStyle = if (palette.isDark) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }

        val pinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Masukkan PIN (Default: 0000)"
            filters = arrayOf(InputFilter.LengthFilter(8))
            gravity = Gravity.CENTER
            textSize = 16f
        }
        val box = FrameLayout(context).apply {
            val p = dp(20f)
            setPadding(p, dp(10f), p, 0)
            addView(pinInput)
        }

        AlertDialog.Builder(context, themeStyle)
            .setTitle("Verifikasi PIN Servis (${item.title})")
            .setMessage("Masukkan PIN keamanan untuk mengatur atau mereset pengingat jadwal servis:")
            .setView(box)
            .setPositiveButton("Lanjut") { _, _ ->
                val entered = pinInput.text?.toString().orEmpty()
                if (tripServiceManager.verifyServicePin(entered)) {
                    showEditServiceDialog(serviceIndex)
                } else {
                    Toast.makeText(context, "PIN salah! PIN default adalah 0000 (bisa diubah di menu Setelan).", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditServiceDialog(index: Int) {
        val item = tripServiceManager.getServiceItems().getOrNull(index) ?: return
        val themeStyle = if (palette.isDark) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }

        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(item.remainingValue.toString())
            hint = "Masukkan sisa ${item.unitType} (mis. ${item.defaultTarget})"
        }
        val box = FrameLayout(context).apply {
            val p = dp(18f)
            setPadding(p, dp(8f), p, 0)
            addView(input)
        }

        AlertDialog.Builder(context, themeStyle)
            .setTitle("Atur Jadwal Servis: ${item.title}")
            .setMessage("Masukkan sisa ${item.unitType} menuju servis berikutnya (isi 0 untuk simulasi Jatuh Tempo):")
            .setView(box)
            .setPositiveButton("Simpan") { _, _ ->
                val v = input.text?.toString()?.toIntOrNull() ?: item.defaultTarget
                tripServiceManager.updateServiceValue(item.id, v)
                refreshTripAndServices()
            }
            .setNeutralButton("Reset Standar (${item.defaultTarget} ${item.unitType})") { _, _ ->
                tripServiceManager.updateServiceValue(item.id, item.defaultTarget)
                refreshTripAndServices()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showTripHistoryPopupDialog() {
        val themeStyle = if (palette.isDark) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }

        val cur = tripServiceManager.getCurrentTripState()
        val history = tripServiceManager.getTripHistory()

        val sb = StringBuilder()
        sb.append("● PERJALANAN SAAT INI (TERBARU)\n")
        if (cur.hasStarted) {
            val h = cur.durationMinutes / 60
            val m = cur.durationMinutes % 60
            sb.append(
                String.format(
                    Locale("id", "ID"),
                    "   Jarak: %.1f km  |  Waktu: %02d:%02d  |  Rata-rata: %d km/jam\n\n",
                    cur.distanceKm, h, m, cur.avgSpeedKmh
                )
            )
        } else {
            sb.append("   Belum ada pergerakan aktif (otomatis mulai saat mobil berjalan).\n\n")
        }

        sb.append("● DAFTAR RIWAYAT TERSIMPAN (${history.size}/5)\n")
        sb.append("   (Otomatis tersimpan setelah parkir 15 menit)\n")
        if (history.isEmpty()) {
            sb.append("   — Belum ada riwayat perjalanan tersimpan —")
        } else {
            for ((idx, item) in history.withIndex()) {
                sb.append(
                    String.format(
                        Locale("id", "ID"),
                        "   %d. [%s]  %.1f km  •  %d mnt  •  %d km/jam\n",
                        idx + 1, item.dateLabel, item.distanceKm, item.durationMinutes, item.avgSpeedKmh
                    )
                )
            }
        }

        val builder = AlertDialog.Builder(context, themeStyle)
            .setTitle("Ringkasan & Riwayat Perjalanan")
            .setMessage(sb.toString().trimEnd())
            .setPositiveButton("Tutup", null)

        if (cur.hasStarted) {
            builder.setNeutralButton("Simpan Perjalanan Saat Ini") { _, _ ->
                tripServiceManager.saveCurrentTripToHistory()
                refreshTripAndServices()
                Toast.makeText(context, "Perjalanan berhasil disimpan ke Riwayat", Toast.LENGTH_SHORT).show()
            }
        }
        if (history.isNotEmpty()) {
            builder.setNegativeButton("Hapus Riwayat") { _, _ ->
                tripServiceManager.clearTripHistory()
                refreshTripAndServices()
                Toast.makeText(context, "Riwayat perjalanan dibersihkan", Toast.LENGTH_SHORT).show()
            }
        }

        builder.show()
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
        mapShareBtn.setTextColor(if (palette.isDark) 0xFF0D1222.toInt() else 0xFFFFFFFF.toInt())
        mapCanvas.applyTheme(palette)
        mapFooterTv.setTextColor(palette.textMuted)

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
        mediaPlayBtn.background = GlassTheme.accentButtonDrawable(context, palette, 19f)
        mediaNextBtn.background = GlassTheme.innerDrawable(context, palette, 16f)

        mediaYtIcon.setTint(palette.textPrimary)
        mediaPrevIcon.setTint(palette.textPrimary)
        mediaPlayIcon.setTint(if (palette.isDark) 0xFF0D1222.toInt() else 0xFFFFFFFF.toInt())
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
        weatherIcon.setTint(palette.accent)
        weatherTempTv.setTextColor(palette.textPrimary)
        weatherDescTv.setTextColor(palette.textSecondary)

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
