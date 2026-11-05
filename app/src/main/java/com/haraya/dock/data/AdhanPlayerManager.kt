package com.haraya.dock.data

import android.app.Dialog
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Environment
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import com.haraya.dock.calc.PrayerCalc
import com.haraya.dock.ui.HarayaDialog
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages Adhan audio playback from Download/hisab (and bundled assets/hisab fallback)
 * with rotational random selection:
 * - Tracks containing "Fajr" (case-insensitive) are used exclusively for Fajr prayer.
 * - Tracks without "Fajr" are rotated randomly for Dhuhr, Asr, Maghrib, and Isha.
 * Provides a themed popup with:
 * - "Stop": Stops the Adhan audio and dismisses the notification/popup.
 * - "Close": Closes only the popup while Adhan audio continues playing to completion.
 */
class AdhanPlayerManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("haraya_adhan_prefs", Context.MODE_PRIVATE)
    private var mediaPlayer: MediaPlayer? = null
    private var activeDialog: Dialog? = null

    var isPlayingAdhan: Boolean = false
        private set

    var currentTrackDisplayName: String = ""
        private set

    var currentPrayerLabel: String = ""
        private set

    private val fajrRotationBag = mutableListOf<String>()
    private val regularRotationBag = mutableListOf<String>()

    var onPlaybackStateChanged: (() -> Unit)? = null

    private data class AdhanCandidate(
        val fileName: String,
        val externalFile: File?,
        val assetPath: String?
    ) {
        val cleanMuezzinName: String
            get() = fileName
                .removeSuffix(".mp3")
                .removeSuffix(".MP3")
                .replace("_", " ")
                .trim()
    }

    private fun discoverTracks(forFajr: Boolean): List<AdhanCandidate> {
        val mapByLowerName = linkedMapOf<String, AdhanCandidate>()

        // 1. Check /sdcard/Download/hisab first if readable
        runCatching {
            val dlDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "hisab")
            if (dlDir.exists() && dlDir.isDirectory) {
                dlDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".mp3", ignoreCase = true)) {
                        val hasFajr = file.name.contains("fajr", ignoreCase = true)
                        if (hasFajr == forFajr) {
                            mapByLowerName[file.name.lowercase(Locale.US)] = AdhanCandidate(
                                fileName = file.name,
                                externalFile = file,
                                assetPath = null
                            )
                        }
                    }
                }
            }
        }

        // 2. Also include bundled assets/hisab tracks (pulled from Download/hisab)
        runCatching {
            val assetFiles = context.assets.list("hisab") ?: emptyArray()
            for (name in assetFiles) {
                if (name.endsWith(".mp3", ignoreCase = true)) {
                    val hasFajr = name.contains("fajr", ignoreCase = true)
                    if (hasFajr == forFajr) {
                        val key = name.lowercase(Locale.US)
                        if (!mapByLowerName.containsKey(key)) {
                            mapByLowerName[key] = AdhanCandidate(
                                fileName = name,
                                externalFile = null,
                                assetPath = "hisab/$name"
                            )
                        }
                    }
                }
            }
        }

        return mapByLowerName.values.toList()
    }

    private fun pickNextRotationalTrack(forFajr: Boolean): AdhanCandidate? {
        val candidates = discoverTracks(forFajr)
        if (candidates.isEmpty()) return null

        val bag = if (forFajr) fajrRotationBag else regularRotationBag
        val validNames = candidates.map { it.fileName }.toSet()
        bag.retainAll(validNames)

        if (bag.isEmpty()) {
            val shuffled = candidates.map { it.fileName }.shuffled()
            val lastPlayed = prefs.getString(if (forFajr) "last_fajr_track" else "last_reg_track", null)
            bag.addAll(shuffled)
            if (bag.size > 1 && bag.first() == lastPlayed) {
                val first = bag.removeAt(0)
                bag.add(first)
            }
        }

        val chosenName = bag.removeAt(0)
        prefs.edit().putString(if (forFajr) "last_fajr_track" else "last_reg_track", chosenName).apply()
        return candidates.firstOrNull { it.fileName == chosenName } ?: candidates.random()
    }

    fun checkAndTriggerIfPrayerTimeArrived(
        activityContext: Context,
        palette: GlassPalette,
        currentHHmm: String,
        prayers: PrayerCalc.PrayerTimes
    ) {
        if (!prefs.getBoolean("adhan_enabled", true)) return

        val matchedPrayer: Pair<String, Boolean>? = when (currentHHmm) {
            prayers.subuh -> "Fajr" to true
            prayers.dzuhur -> "Dhuhr" to false
            prayers.ashar -> "Asr" to false
            prayers.maghrib -> "Maghrib" to false
            prayers.isya -> "Isha" to false
            else -> null
        }

        if (matchedPrayer != null) {
            val dayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val triggerKey = "${dayStr}_${matchedPrayer.first}_$currentHHmm"
            val lastTriggered = prefs.getString("last_triggered_prayer_key", "") ?: ""
            if (triggerKey != lastTriggered) {
                prefs.edit().putString("last_triggered_prayer_key", triggerKey).apply()
                triggerAdhanWithPopup(
                    activityContext = activityContext,
                    palette = palette,
                    prayerName = matchedPrayer.first,
                    prayerTime = currentHHmm,
                    isFajr = matchedPrayer.second
                )
            }
        }
    }

    fun triggerAdhanWithPopup(
        activityContext: Context,
        palette: GlassPalette,
        prayerName: String,
        prayerTime: String,
        isFajr: Boolean
    ) {
        val candidate = pickNextRotationalTrack(isFajr) ?: return
        startAudioPlayback(candidate, prayerName)
        showAdhanAlertDialog(activityContext, palette, prayerName, prayerTime, candidate.cleanMuezzinName)
    }

    private fun startAudioPlayback(candidate: AdhanCandidate, prayerName: String) {
        stopAdhanPlayback(notify = false)
        runCatching {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val extFile = candidate.externalFile
            var loaded = false
            if (extFile != null && extFile.exists() && extFile.canRead()) {
                runCatching {
                    mp.setDataSource(extFile.absolutePath)
                    loaded = true
                }
            }
            if (!loaded) {
                val assetPath = candidate.assetPath ?: "hisab/${candidate.fileName}"
                context.assets.openFd(assetPath).use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
            }
            mp.setOnCompletionListener {
                isPlayingAdhan = false
                currentTrackDisplayName = ""
                currentPrayerLabel = ""
                runCatching { activeDialog?.dismiss() }
                activeDialog = null
                onPlaybackStateChanged?.invoke()
            }
            mp.prepare()
            mp.start()
            mediaPlayer = mp
            isPlayingAdhan = true
            currentTrackDisplayName = candidate.cleanMuezzinName
            currentPrayerLabel = prayerName
            onPlaybackStateChanged?.invoke()
        }
    }

    fun stopAdhanPlayback(notify: Boolean = true) {
        runCatching {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        mediaPlayer = null
        isPlayingAdhan = false
        currentTrackDisplayName = ""
        currentPrayerLabel = ""
        runCatching { activeDialog?.dismiss() }
        activeDialog = null
        if (notify) {
            onPlaybackStateChanged?.invoke()
        }
    }

    fun isAdhanAlertEnabled(): Boolean = prefs.getBoolean("adhan_enabled", true)

    fun setAdhanAlertEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("adhan_enabled", enabled).apply()
        if (!enabled && isPlayingAdhan) {
            stopAdhanPlayback()
        }
    }

    fun showAdhanAlertDialog(
        activityContext: Context,
        palette: GlassPalette,
        prayerName: String,
        prayerTime: String,
        muezzinTitle: String
    ) {
        runCatching { activeDialog?.dismiss() }

        val dp = { v: Float -> GlassTheme.dp(activityContext, v) }
        activeDialog = HarayaDialog.showCustomModal(
            context = activityContext,
            palette = palette,
            title = "Adhan Alert • $prayerName ($prayerTime)",
            subtitle = "It is time for $prayerName prayer",
            contentBuilder = { container, _ ->
                val card = LinearLayout(activityContext).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GlassTheme.innerDrawable(activityContext, palette, 10f)
                    setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
                }
                val badgeTv = TextView(activityContext).apply {
                    text = if (prayerName.equals("Fajr", ignoreCase = true)) {
                        "● FAJR ADHAN SPECIAL TRACK"
                    } else {
                        "● ROTATIONAL ADHAN TRACK"
                    }
                    textSize = 9.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setTextColor(palette.accent)
                    includeFontPadding = false
                }
                val trackTv = TextView(activityContext).apply {
                    text = "🔊 $muezzinTitle"
                    textSize = 12.5f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    setTextColor(palette.textPrimary)
                    includeFontPadding = false
                }
                val hintTv = TextView(activityContext).apply {
                    text = "Tap 'Stop' to silence Adhan audio and dismiss this alert, or tap 'Close' to hide this popup while Adhan continues playing."
                    textSize = 10f
                    setTextColor(palette.textSecondary)
                    includeFontPadding = false
                }
                card.addView(badgeTv)
                card.addView(trackTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4f)
                    bottomMargin = dp(6f)
                })
                card.addView(hintTv)
                container.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            actions = listOf(
                HarayaDialog.ActionButton("Close", isPrimary = false) { dlg ->
                    // Close only dismisses the popup; Adhan audio continues playing!
                    dlg.dismiss()
                    activeDialog = null
                },
                HarayaDialog.ActionButton("Stop", isDestructive = true) { dlg ->
                    // Stop silences the Adhan audio AND dismisses the popup!
                    dlg.dismiss()
                    stopAdhanPlayback()
                }
            )
        )
    }
}

