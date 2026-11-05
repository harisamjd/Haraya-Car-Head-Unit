package com.haraya.dock.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.SystemClock
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
    private val onRequestLocationPermission: () -> Unit,
    private val onOpenMusic: () -> Unit,
    private val onOpenYouTube: () -> Unit,
    private val onOpenPhone: () -> Unit,
    private val onOpenObdScreen: () -> Unit,
    private val onOpenAppsScreen: () -> Unit,
    private val onRequestDefaultLauncher: () -> Unit
) : LinearLayout(context) {

    private var palette: GlassPalette = GlassTheme.Dark
    private var quoteIdx: Int = 0
    private var seatbeltBannerDismissed: Boolean = false

    // Top Bar
    private val topBarLayout: LinearLayout
    private val modeTerangBtn: TextView
    private val modeGelapBtn: TextView
    private val modeAutoBtn: TextView
    private val layoutSwitchBtn: TextView
    private val topBrandTitleTv: TextView
    private val topBrandSubTv: TextView
    private val gpsPillTv: TextView
    private val launcherPillTv: TextView

    // Left Column: Peta + Sholat + Perjalanan Saat Ini
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
    private val prayerQuoteTv: TextView

    private val currentTripCard: GlassCardView
    private val currentTripHeaderTv: TextView
    private val tripDistValTv: TextView
    private val tripDurValTv: TextView
    private val tripAvgValTv: TextView
    private val tripDistSubTv: TextView
    private val tripDurSubTv: TextView
    private val tripAvgSubTv: TextView
    private val tripFootnoteTv: TextView

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

    // Right Column: Riwayat Perjalanan + Jadwal Servis + Cuaca
    private val historyCard: GlassCardView
    private val historyHeaderTv: TextView
    private val historyCountTv: TextView
    private val historyEmptyTv: TextView
    private val historyListLayout: LinearLayout

    private val serviceCard: GlassCardView
    private val serviceHeaderTv: TextView
    private val serviceItemBoxes = mutableListOf<LinearLayout>()
    private val serviceItemIcons = mutableListOf<VectorIconView>()
    private val serviceItemTitles = mutableListOf<TextView>()
    private val serviceItemStatuses = mutableListOf<TextView>()

    private val weatherCard: GlassCardView
    private val weatherHeaderTv: TextView
    private val weatherBadgeTv: TextView
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
        val pad = dp(10f)
        setPadding(pad, dp(6f), pad, dp(8f))

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
            val p = dp(3f)
            setPadding(p, p, p, p)
        }
        modeTerangBtn = buildModePill("Terang") { onSwitchThemeMode(ThemeMode.LIGHT) }
        modeGelapBtn = buildModePill("Gelap") { onSwitchThemeMode(ThemeMode.DARK) }
        modeAutoBtn = buildModePill("Auto") { onSwitchThemeMode(ThemeMode.AUTO) }
        themeSegmentBox.addView(modeTerangBtn)
        themeSegmentBox.addView(modeGelapBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(3f) })
        themeSegmentBox.addView(modeAutoBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(3f) })

        layoutSwitchBtn = TextView(context).apply {
            text = "Mode Klasik"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            setOnClickListener { onSwitchToClassicLayout() }
        }

        val leftTopGroup = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        leftTopGroup.addView(themeSegmentBox)
        leftTopGroup.addView(layoutSwitchBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(6f)
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
            gravity = Gravity.CENTER_VERTICAL
        }
        gpsPillTv = TextView(context).apply {
            text = "GPS Aktif"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            setOnClickListener { onRequestLocationPermission() }
        }
        launcherPillTv = TextView(context).apply {
            text = "Setel Beranda"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            setOnClickListener { onRequestDefaultLauncher() }
        }
        rightTopGroup.addView(gpsPillTv)
        rightTopGroup.addView(launcherPillTv, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(6f)
        })

        topBarLayout.addView(leftTopGroup, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f))
        topBarLayout.addView(brandCenterCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f))
        rightTopGroup.gravity = Gravity.END or Gravity.CENTER_VERTICAL
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

        // --- KOLOM KIRI (Peta + Jadwal Sholat + Perjalanan Saat Ini) ---
        val leftCol = LinearLayout(context).apply {
            orientation = VERTICAL
        }

        // 2A. Kartu Peta & Share Lokasi
        mapCard = GlassCardView(context)
        mapCard.setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        val mapCol = LinearLayout(context).apply { orientation = VERTICAL }
        val mapHeaderRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        mapHeaderTv = TextView(context).apply {
            text = "PETA & LOKASI"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.06f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mapShareBtn = TextView(context).apply {
            text = "Bagikan Live"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(7f), dp(2f), dp(7f), dp(2f))
            setOnClickListener { onShareLiveLocation() }
        }
        mapHeaderRow.addView(mapHeaderTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        mapHeaderRow.addView(mapShareBtn)
        mapCol.addView(mapHeaderRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4f)
        })

        mapViewport = FrameLayout(context)
        GlassTheme.clipRound(context, mapViewport, 12f)
        mapCanvas = MapCanvasView(context).apply {
            setOnClickListener { onOpenMaps() }
        }
        mapViewport.addView(mapCanvas, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mapCol.addView(mapViewport, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(4f)
        })

        mapFooterTv = TextView(context).apply {
            text = "GPS Realtime • Ketuk peta untuk Google Maps"
            textSize = 8.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mapCol.addView(mapFooterTv)
        mapCard.addView(mapCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 2B. Kartu Jadwal Sholat (Di antara Maps & Ringkasan Perjalanan)
        prayerCard = GlassCardView(context)
        prayerCard.setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        val prayerCol = LinearLayout(context).apply { orientation = VERTICAL }
        val prayerHeadRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prayerHeaderTitleTv = TextView(context).apply {
            text = "JADWAL SHOLAT"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.06f
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
            bottomMargin = dp(4f)
        })

        val prayerStrip = LinearLayout(context).apply { orientation = HORIZONTAL }
        val pNames = listOf("Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")
        for (i in pNames.indices) {
            val slot = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2f), dp(3f), dp(2f), dp(3f))
            }
            val nTv = TextView(context).apply {
                text = pNames[i]
                textSize = 8.5f
                gravity = Gravity.CENTER
            }
            val tTv = TextView(context).apply {
                text = "--:--"
                textSize = 10f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                fontFeatureSettings = "tnum"
                gravity = Gravity.CENTER
            }
            slot.addView(nTv)
            slot.addView(tTv)
            prayerSlotBoxes.add(slot)
            prayerSlotNames.add(nTv)
            prayerSlotTimes.add(tTv)
            prayerStrip.addView(slot, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i < pNames.lastIndex) rightMargin = dp(3f)
            })
        }
        prayerCol.addView(prayerStrip, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4f)
        })

        prayerQuoteTv = TextView(context).apply {
            textSize = 9f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener {
                quoteIdx = (quoteIdx + 1) % QuoteRepository.count()
                refreshQuoteText()
            }
        }
        prayerCol.addView(prayerQuoteTv)
        prayerCard.addView(prayerCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 2C. Kartu Ringkasan Perjalanan Saat Ini
        currentTripCard = GlassCardView(context)
        currentTripCard.setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        currentTripCard.setOnClickListener {
            showCurrentTripActionDialog()
        }
        val tripCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        currentTripHeaderTv = TextView(context).apply {
            text = "RINGKASAN PERJALANAN"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.06f
        }
        tripCol.addView(currentTripHeaderTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(3f)
        })

        val tripMetricsRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        fun buildTripMetric(unitLabel: String): Pair<TextView, TextView> {
            val box = LinearLayout(context).apply { orientation = VERTICAL }
            val vTv = TextView(context).apply {
                text = "—"
                textSize = 15f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                fontFeatureSettings = "tnum"
            }
            val uTv = TextView(context).apply {
                text = unitLabel
                textSize = 8.5f
            }
            box.addView(vTv)
            box.addView(uTv)
            tripMetricsRow.addView(box, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return vTv to uTv
        }
        val m1 = buildTripMetric("km")
        val m2 = buildTripMetric("jam : mnt")
        val m3 = buildTripMetric("km/jam rata-rata")
        tripDistValTv = m1.first; tripDistSubTv = m1.second
        tripDurValTv = m2.first; tripDurSubTv = m2.second
        tripAvgValTv = m3.first; tripAvgSubTv = m3.second
        tripCol.addView(tripMetricsRow)

        tripFootnoteTv = TextView(context).apply {
            text = "Dimulai saat GPS mendeteksi gerak"
            textSize = 8.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        tripCol.addView(tripFootnoteTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2f)
        })
        currentTripCard.addView(tripCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        leftCol.addView(mapCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.44f).apply { bottomMargin = dp(6f) })
        leftCol.addView(prayerCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.31f).apply { bottomMargin = dp(6f) })
        leftCol.addView(currentTripCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.25f))

        mainColumnsRow.addView(leftCol, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.29f).apply {
            rightMargin = dp(8f)
        })

        // --- KOLOM TENGAH (Banner Pengingat + Busur Speedometer + Gambar Mobil Baleno + Kontrol Media) ---
        val centerCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Banner Peringatan (Sabuk Pengaman / Servis Jatuh Tempo)
        alertBannerBox = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
        }
        val alertTextCol = LinearLayout(context).apply { orientation = VERTICAL }
        alertBannerTitleTv = TextView(context).apply {
            text = "Kenakan Sabuk Pengaman"
            textSize = 10.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        alertBannerSubTv = TextView(context).apply {
            text = "Pastikan sabuk pengaman terpasang demi keselamatan perjalanan"
            textSize = 9f
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

        // Area Instrumen Tengah: Speedometer Arc + Gambar Mobil Suzuki Baleno
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
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(68f)
                leftMargin = dp(20f)
                rightMargin = dp(20f)
                bottomMargin = dp(4f)
            }
        )

        val carBadgeCol = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        carNameTv = TextView(context).apply {
            text = "Suzuki Baleno"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        carSubTv = TextView(context).apply {
            text = "Hatchback • B 1353 EYX"
            textSize = 9.5f
        }
        carBadgeCol.addView(carNameTv)
        carBadgeCol.addView(carSubTv)
        centerStageFrame.addView(
            carBadgeCol,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(6f)
                bottomMargin = dp(4f)
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
            setOnClickListener { onOpenMusic() }
        }
        mediaTitleTv = TextView(context).apply {
            text = "Kontrol Media"
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mediaSubTv = TextView(context).apply {
            text = "Ketuk untuk membuka YouTube Music"
            textSize = 9.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mediaTextCol.addView(mediaTitleTv)
        mediaTextCol.addView(mediaSubTv)
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

        val ytPair = buildMediaCircleBtn(VectorIconView.IconType.YOUTUBE, 32f, 16f) { onOpenYouTube() }
        val prevPair = buildMediaCircleBtn(VectorIconView.IconType.PREV_TRACK, 32f, 15f) { sendMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        val playPair = buildMediaCircleBtn(VectorIconView.IconType.PLAY_PAUSE, 38f, 18f) { sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        val nextPair = buildMediaCircleBtn(VectorIconView.IconType.NEXT_TRACK, 32f, 15f) { sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT) }

        mediaYtBtn = ytPair.first; mediaYtIcon = ytPair.second
        mediaPrevBtn = prevPair.first; mediaPrevIcon = prevPair.second
        mediaPlayBtn = playPair.first; mediaPlayIcon = playPair.second
        mediaNextBtn = nextPair.first; mediaNextIcon = nextPair.second

        mediaCard.addView(mediaRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        centerCol.addView(mediaCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        mainColumnsRow.addView(centerCol, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.42f).apply {
            rightMargin = dp(8f)
        })

        // --- KOLOM KANAN (Riwayat Perjalanan + Jadwal Servis + Cuaca) ---
        val rightCol = LinearLayout(context).apply {
            orientation = VERTICAL
        }

        // 3A. Kartu Riwayat Perjalanan
        historyCard = GlassCardView(context)
        historyCard.setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        historyCard.setOnClickListener { showCurrentTripActionDialog() }
        val histCol = LinearLayout(context).apply { orientation = VERTICAL }
        val histHeaderRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        historyHeaderTv = TextView(context).apply {
            text = "RIWAYAT PERJALANAN"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.06f
        }
        historyCountTv = TextView(context).apply {
            text = "0/5"
            textSize = 9.5f
            fontFeatureSettings = "tnum"
        }
        histHeaderRow.addView(historyHeaderTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        histHeaderRow.addView(historyCountTv)
        histCol.addView(histHeaderRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4f)
        })

        historyEmptyTv = TextView(context).apply {
            text = "Belum ada riwayat perjalanan\nTersimpan otomatis setelah parkir 15 mnt"
            textSize = 9.5f
            gravity = Gravity.CENTER
        }
        historyListLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
        }
        histCol.addView(historyEmptyTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        histCol.addView(historyListLayout, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        historyCard.addView(histCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 3B. Kartu Jadwal Servis (Grid 2x2: Oli Mesin, Oli Matic, Servis AC, Tuneup)
        serviceCard = GlassCardView(context)
        serviceCard.setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        val svcCol = LinearLayout(context).apply { orientation = VERTICAL }
        serviceHeaderTv = TextView(context).apply {
            text = "JADWAL SERVIS (KETUK UNTUK ATUR)"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.06f
        }
        svcCol.addView(serviceHeaderTv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4f)
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
                    setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                    setOnClickListener { showEditServiceDialog(idx) }
                }
                val ic = VectorIconView(context, svcIcons[idx])
                val titleTv = TextView(context).apply {
                    textSize = 10.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    gravity = Gravity.CENTER
                    maxLines = 1
                }
                val statTv = TextView(context).apply {
                    textSize = 9f
                    fontFeatureSettings = "tnum"
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                itemBox.addView(ic, LayoutParams(dp(16f), dp(16f)))
                itemBox.addView(titleTv, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(2f)
                })
                itemBox.addView(statTv)

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

        // 3C. Kartu Cuaca Open-Meteo
        weatherCard = GlassCardView(context)
        weatherCard.setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
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
            textSize = 9.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.06f
        }
        weatherBadgeTv = TextView(context).apply {
            text = "LIVE"
            textSize = 8.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(6f), dp(1f), dp(6f), dp(1f))
        }
        wHeadRow.addView(weatherHeaderTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        wHeadRow.addView(weatherBadgeTv)
        wCol.addView(wHeadRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(2f)
        })

        val wBodyRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        weatherIcon = VectorIconView(context, VectorIconView.IconType.CLOUD_SUN)
        wBodyRow.addView(weatherIcon, LayoutParams(dp(26f), dp(26f)).apply {
            rightMargin = dp(8f)
        })
        val wTextCol = LinearLayout(context).apply { orientation = VERTICAL }
        weatherTempTv = TextView(context).apply {
            text = "28°C"
            textSize = 18f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }
        weatherDescTv = TextView(context).apply {
            text = "Cerah Berawan • Open-Meteo"
            textSize = 9f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        wTextCol.addView(weatherTempTv)
        wTextCol.addView(weatherDescTv)
        wBodyRow.addView(wTextCol)
        wCol.addView(wBodyRow)
        weatherCard.addView(wCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        rightCol.addView(historyCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.29f).apply { bottomMargin = dp(6f) })
        rightCol.addView(serviceCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.48f).apply { bottomMargin = dp(6f) })
        rightCol.addView(weatherCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.23f))

        mainColumnsRow.addView(rightCol, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.29f))

        addView(mainColumnsRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(6f)
        })

        // ==========================================
        // 3. BOTTOM NAVIGATION DOCK BAR
        // ==========================================
        bottomDockCard = GlassCardView(context)
        bottomDockCard.setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
        val dockRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dockSpecs = listOf(
            Triple(VectorIconView.IconType.HOME, "BERANDA") { },
            Triple(VectorIconView.IconType.MUSIC, "MEDIA") { onOpenMusic() },
            Triple(VectorIconView.IconType.PHONE, "TELEPON") { onOpenPhone() },
            Triple(VectorIconView.IconType.GAUGE, "OBD & MESIN") { onOpenObdScreen() },
            Triple(VectorIconView.IconType.APPS_GRID, "APLIKASI") { onOpenAppsScreen() }
        )

        for (i in dockSpecs.indices) {
            val spec = dockSpecs[i]
            val tab = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                setOnClickListener { spec.third.invoke() }
            }
            val ic = VectorIconView(context, spec.first)
            val lbl = TextView(context).apply {
                text = spec.second
                textSize = 10.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                letterSpacing = 0.06f
            }
            tab.addView(ic, LayoutParams(dp(15f), dp(15f)).apply { rightMargin = dp(6f) })
            tab.addView(lbl)

            dockTabBoxes.add(tab)
            dockTabIcons.add(ic)
            dockTabLabels.add(lbl)

            dockRow.addView(tab, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i < dockSpecs.lastIndex) rightMargin = dp(6f)
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
            setPadding(dp(8f), dp(3f), dp(8f), dp(3f))
            setOnClickListener { onClick() }
        }
    }

    private fun sendMediaButton(keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        postDelayed({ refreshMediaText() }, 250L)
    }

    fun updateSpeed(kmh: Int) {
        speedArcView.setSpeed(kmh)
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
        refreshMediaText()
    }

    fun updateLocationInfo(placeTitle: String, coordSub: String) {
        mapHeaderTv.text = placeTitle.uppercase(Locale.getDefault())
        mapFooterTv.text = coordSub
    }

    fun updateWeather(weather: WeatherState) {
        weatherTempTv.text = "${weather.tempCelsius}°C"
        weatherDescTv.text = weather.conditionLabel
        weatherBadgeTv.text = if (weather.isLive) "LIVE" else "CACHE"
    }

    fun refreshTripAndServices() {
        val cur = tripServiceManager.getCurrentTripState()
        if (cur.hasStarted) {
            tripDistValTv.text = String.format(Locale("id", "ID"), "%.1f", cur.distanceKm)
            val h = cur.durationMinutes / 60
            val m = cur.durationMinutes % 60
            tripDurValTv.text = String.format(Locale.US, "%02d:%02d", h, m)
            tripAvgValTv.text = cur.avgSpeedKmh.toString()
            tripFootnoteTv.text = "Perjalanan aktif • Ketuk kartu untuk simpan/reset"
        } else {
            tripDistValTv.text = "—"
            tripDurValTv.text = "—"
            tripAvgValTv.text = "—"
            tripFootnoteTv.text = "Dimulai saat GPS mendeteksi gerak"
        }

        val history = tripServiceManager.getTripHistory()
        historyCountTv.text = "${history.size}/5"
        if (history.isEmpty()) {
            historyEmptyTv.visibility = View.VISIBLE
            historyListLayout.visibility = View.GONE
        } else {
            historyEmptyTv.visibility = View.GONE
            historyListLayout.visibility = View.VISIBLE
            historyListLayout.removeAllViews()
            for (item in history) {
                val r = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(1f), 0, dp(1f))
                }
                val dTv = TextView(context).apply {
                    text = item.dateLabel
                    textSize = 9f
                    fontFeatureSettings = "tnum"
                    setTextColor(palette.textSecondary)
                }
                val distTv = TextView(context).apply {
                    text = String.format(Locale("id", "ID"), "%.1f km", item.distanceKm)
                    textSize = 9.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    fontFeatureSettings = "tnum"
                    gravity = Gravity.END
                    setTextColor(palette.textPrimary)
                }
                val durTv = TextView(context).apply {
                    text = "${item.durationMinutes}m"
                    textSize = 9f
                    fontFeatureSettings = "tnum"
                    gravity = Gravity.END
                    setTextColor(palette.textSecondary)
                }
                val avgTv = TextView(context).apply {
                    text = "${item.avgSpeedKmh} km/j"
                    textSize = 9f
                    fontFeatureSettings = "tnum"
                    gravity = Gravity.END
                    setTextColor(palette.textSecondary)
                }
                r.addView(dTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f))
                r.addView(distTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
                r.addView(durTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f))
                r.addView(avgTv, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
                historyListLayout.addView(r)
            }
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
            GlassTheme.makePressable(context, serviceItemBoxes[i], palette, 10f)
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
        prayerQuoteTv.text = "\"${q.translation}\" (${q.reference})"
        prayerQuoteTv.setTextColor(palette.textSecondary)
    }

    private fun refreshMediaText() {
        val title = NotificationCollectorService.currentMediaTitle
        val artist = NotificationCollectorService.currentMediaArtist
        if (!title.isNullOrBlank()) {
            mediaTitleTv.text = title
            mediaSubTv.text = artist ?: "Memutar Media"
        } else {
            mediaTitleTv.text = "Kontrol Media"
            mediaSubTv.text = "Kontrol sistem • ketuk untuk buka YouTube Music"
        }
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
            val p = dp(16f)
            setPadding(p, dp(8f), p, 0)
            addView(input)
        }

        AlertDialog.Builder(context, themeStyle)
            .setTitle("Atur Pengingat: ${item.title}")
            .setMessage("Masukkan sisa ${item.unitType} menuju servis berikutnya (isi 0 untuk simulasi Jatuh Tempo):")
            .setView(box)
            .setPositiveButton("Simpan") { _, _ ->
                val v = input.text?.toString()?.toIntOrNull() ?: item.defaultTarget
                tripServiceManager.updateServiceValue(item.id, v)
                refreshTripAndServices()
            }
            .setNeutralButton("Reset Baru (${item.defaultTarget} ${item.unitType})") { _, _ ->
                tripServiceManager.updateServiceValue(item.id, item.defaultTarget)
                refreshTripAndServices()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showCurrentTripActionDialog() {
        val themeStyle = if (palette.isDark) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        val options = arrayOf(
            "Simpan Perjalanan Saat Ini ke Riwayat",
            "Bersihkan Semua Riwayat Perjalanan"
        )
        AlertDialog.Builder(context, themeStyle)
            .setTitle("Kelola Catatan Perjalanan")
            .setItems(options) { _, which ->
                if (which == 0) {
                    val ok = tripServiceManager.saveCurrentTripToHistory()
                    if (!ok) {
                        Toast.makeText(context, "Belum ada perjalanan aktif (minimal bergerak 100 meter)", Toast.LENGTH_SHORT).show()
                    }
                    refreshTripAndServices()
                } else {
                    tripServiceManager.clearTripHistory()
                    refreshTripAndServices()
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    fun applyTheme(newPalette: GlassPalette, currentMode: ThemeMode, isDefaultLauncher: Boolean) {
        palette = newPalette

        // Top Bar Pills
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

        gpsPillTv.background = GlassTheme.innerDrawable(context, palette, 8f)
        gpsPillTv.setTextColor(if (palette.isDark) 0xFF4ADE80.toInt() else 0xFF15803D.toInt())

        launcherPillTv.text = if (isDefaultLauncher) "Beranda Aktif" else "Setel Beranda"
        launcherPillTv.background = if (isDefaultLauncher) {
            GlassTheme.innerDrawable(context, palette, 8f)
        } else {
            GlassTheme.selectDrawable(context, palette, 8f)
        }
        launcherPillTv.setTextColor(if (isDefaultLauncher) palette.textSecondary else palette.selectFg)

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
        refreshQuoteText()

        currentTripCard.applyTheme(palette)
        currentTripHeaderTv.setTextColor(palette.textSecondary)
        tripDistValTv.setTextColor(palette.textPrimary)
        tripDurValTv.setTextColor(palette.textPrimary)
        tripAvgValTv.setTextColor(palette.textPrimary)
        tripDistSubTv.setTextColor(palette.textSecondary)
        tripDurSubTv.setTextColor(palette.textSecondary)
        tripAvgSubTv.setTextColor(palette.textSecondary)
        tripFootnoteTv.setTextColor(palette.textMuted)

        // Center Column
        speedArcView.applyTheme(palette)
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
        historyCard.applyTheme(palette)
        historyHeaderTv.setTextColor(palette.textSecondary)
        historyCountTv.setTextColor(palette.textMuted)
        historyEmptyTv.setTextColor(palette.textMuted)

        serviceCard.applyTheme(palette)
        serviceHeaderTv.setTextColor(palette.textSecondary)

        weatherCard.applyTheme(palette)
        weatherHeaderTv.setTextColor(palette.textSecondary)
        weatherBadgeTv.background = GlassTheme.selectDrawable(context, palette, 6f)
        weatherBadgeTv.setTextColor(palette.selectFg)
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
    }

    private fun dp(value: Float): Int = GlassTheme.dp(context, value)
}
