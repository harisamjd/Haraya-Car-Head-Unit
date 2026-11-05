package com.haraya.dock.data

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.haraya.dock.R
import com.haraya.dock.calc.PrayerCalc
import com.haraya.dock.ui.HarayaDialog
import com.haraya.dock.ui.glass.GlassPalette
import com.haraya.dock.ui.glass.GlassTheme
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages:
 * 1. Adhan audio playback from Download/hisab & bundled assets/hisab with rotational random selection:
 *    - Tracks containing "Fajr" (case-insensitive) are used exclusively for Fajr prayer.
 *    - Tracks without "Fajr" are rotated randomly for Dhuhr, Asr, Maghrib, and Isha.
 *    - Popup has "Stop" (stops audio + closes popup) and "Close" (closes popup only while audio continues).
 * 2. Startup Travel Prayer audio (audio/travel-pray.m4a) played automatically 20 seconds after
 *    the vehicle / head unit turns on so Bluetooth/amplifier audio is fully connected first.
 */
class AdhanPlayerManager(private val context: Context) {

    companion object {
        private const val TAG = "AdhanPlayerManager"
        private const val STARTUP_PRAYER_DELAY_MS = 20_000L

        @Volatile
        private var startupPrayerPlayedInProcess: Boolean = false
    }

    private val prefs = context.getSharedPreferences("haraya_adhan_prefs", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var travelPrayerPlayer: MediaPlayer? = null
    private var activeDialog: Dialog? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    var isPlayingAdhan: Boolean = false
        private set

    var currentTrackDisplayName: String = ""
        private set

    var currentPrayerLabel: String = ""
        private set

    private val fajrRotationBag = mutableListOf<String>()
    private val regularRotationBag = mutableListOf<String>()

    var onPlaybackStateChanged: (() -> Unit)? = null

    private val delayedTravelPrayerRunnable = Runnable {
        playTravelPrayerNow(showToast = true)
    }

    private data class AdhanCandidate(
        val fileName: String,
        val assetPath: String?,
        val externalFile: File?
    ) {
        val cleanMuezzinName: String
            get() = fileName
                .removeSuffix(".mp3")
                .removeSuffix(".MP3")
                .replace("_", " ")
                .trim()
    }

    /**
     * Schedules the startup travel prayer (travel-pray.m4a) to play 20 seconds after the
     * head unit / app process starts (once per boot/session) so audio routing is ready.
     */
    fun scheduleStartupTravelPrayerIfNeeded() {
        if (startupPrayerPlayedInProcess) return
        if (!isStartupTravelPrayerEnabled()) return
        startupPrayerPlayedInProcess = true
        mainHandler.removeCallbacks(delayedTravelPrayerRunnable)
        mainHandler.postDelayed(delayedTravelPrayerRunnable, STARTUP_PRAYER_DELAY_MS)
    }

    fun isStartupTravelPrayerEnabled(): Boolean = prefs.getBoolean("startup_travel_prayer_enabled", true)

    fun setStartupTravelPrayerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("startup_travel_prayer_enabled", enabled).apply()
        if (!enabled) {
            mainHandler.removeCallbacks(delayedTravelPrayerRunnable)
            stopTravelPrayerPlayback()
        }
    }

    fun playTravelPrayerNow(showToast: Boolean = true) {
        if (isPlayingAdhan) return // Do not interrupt an active Adhan
        stopTravelPrayerPlayback()

        try {
            requestAudioFocus()
            ensureAudibleMusicStreamVolume()

            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )

            var prepared = false
            // 1. Try bundled raw resource R.raw.travel_pray first
            runCatching {
                val afd = context.resources.openRawResourceFd(R.raw.travel_pray)
                if (afd != null) {
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    mp.prepare()
                    afd.close()
                    prepared = true
                }
            }

            // 2. Fallback to assets/audio/travel-pray.m4a
            if (!prepared) {
                val afd = context.assets.openFd("audio/travel-pray.m4a")
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                mp.prepare()
                afd.close()
            }

            mp.setVolume(1.0f, 1.0f)
            mp.setOnCompletionListener { player ->
                runCatching { player.release() }
                if (travelPrayerPlayer === player) {
                    travelPrayerPlayer = null
                }
                abandonAudioFocus()
            }
            mp.start()
            travelPrayerPlayer = mp

            if (showToast) {
                Toast.makeText(context, "🔊 Playing Travel Prayer (Doa Perjalanan)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play travel-pray.m4a", e)
        }
    }

    fun stopTravelPrayerPlayback() {
        runCatching {
            travelPrayerPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        travelPrayerPlayer = null
    }

    private fun discoverTracks(forFajr: Boolean): List<AdhanCandidate> {
        val mapByLowerName = linkedMapOf<String, AdhanCandidate>()

        // 1. Bundled assets/hisab tracks (always fast & Scoped-Storage-safe)
        runCatching {
            val assetFiles = context.assets.list("hisab") ?: emptyArray()
            for (name in assetFiles) {
                if (name.endsWith(".mp3", ignoreCase = true)) {
                    val hasFajr = name.contains("fajr", ignoreCase = true)
                    if (hasFajr == forFajr) {
                        val key = name.lowercase(Locale.US)
                        mapByLowerName[key] = AdhanCandidate(
                            fileName = name,
                            assetPath = "hisab/$name",
                            externalFile = null
                        )
                    }
                }
            }
        }

        // 2. Also include any extra tracks added by the user into /sdcard/Download/hisab
        runCatching {
            val dlDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "hisab")
            if (dlDir.exists() && dlDir.isDirectory) {
                dlDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".mp3", ignoreCase = true)) {
                        val hasFajr = file.name.contains("fajr", ignoreCase = true)
                        if (hasFajr == forFajr) {
                            val key = file.name.lowercase(Locale.US)
                            val existing = mapByLowerName[key]
                            if (existing != null) {
                                mapByLowerName[key] = existing.copy(externalFile = file)
                            } else {
                                mapByLowerName[key] = AdhanCandidate(
                                    fileName = file.name,
                                    assetPath = null,
                                    externalFile = file
                                )
                            }
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
        stopTravelPrayerPlayback()
        stopAdhanPlayback(notify = false)

        try {
            requestAudioFocus()
            ensureAudibleMusicStreamVolume()

            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )

            var prepared = false

            // 1. Primary: Load from bundled assets/hisab — IMPORTANT: call mp.prepare() BEFORE afd.close()!
            if (candidate.assetPath != null) {
                try {
                    val afd = context.assets.openFd(candidate.assetPath)
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    mp.prepare()
                    afd.close()
                    prepared = true
                } catch (e: Exception) {
                    Log.w(TAG, "Asset openFd prepare failed for ${candidate.assetPath}, trying fallback", e)
                    mp.reset()
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                }
            }

            // 2. Fallback: Load from /sdcard/Download/hisab via FileInputStream FD (process-safe)
            if (!prepared && candidate.externalFile != null && candidate.externalFile.exists()) {
                FileInputStream(candidate.externalFile).use { fis ->
                    mp.setDataSource(fis.fd)
                    mp.prepare()
                    prepared = true
                }
            }

            if (!prepared) {
                mp.release()
                return
            }

            mp.setVolume(1.0f, 1.0f)
            mp.setOnCompletionListener {
                isPlayingAdhan = false
                currentTrackDisplayName = ""
                currentPrayerLabel = ""
                abandonAudioFocus()
                runCatching { activeDialog?.dismiss() }
                activeDialog = null
                onPlaybackStateChanged?.invoke()
            }
            mp.start()

            mediaPlayer = mp
            isPlayingAdhan = true
            currentTrackDisplayName = candidate.cleanMuezzinName
            currentPrayerLabel = prayerName
            onPlaybackStateChanged?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Adhan playback for ${candidate.fileName}", e)
        }
    }

    private fun requestAudioFocus() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        }
    }

    private fun abandonAudioFocus() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
    }

    private fun ensureAudibleMusicStreamVolume() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curVol <= 1) {
                val targetVol = (maxVol * 0.65f).toInt().coerceAtLeast(3)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            }
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
        abandonAudioFocus()
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
                    dlg.dismiss()
                    activeDialog = null
                },
                HarayaDialog.ActionButton("Stop", isDestructive = true) { dlg ->
                    dlg.dismiss()
                    stopAdhanPlayback()
                }
            )
        )
    }
}
