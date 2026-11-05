package com.haraya.dock.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCollectorService : NotificationListenerService() {

    companion object {
        @Volatile
        var currentMediaTitle: String? = null
        @Volatile
        var currentMediaArtist: String? = null
        @Volatile
        var isSessionPlaying: Boolean? = null
        @Volatile
        var onMediaChangedCallback: (() -> Unit)? = null

        @Volatile
        private var instance: NotificationCollectorService? = null

        fun isNotificationAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val cn = ComponentName(context, NotificationCollectorService::class.java).flattenToString()
            return flat.contains(cn) || flat.contains(context.packageName)
        }

        fun isCurrentlyPlaying(context: Context): Boolean {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val musicActive = am?.isMusicActive == true
            return isSessionPlaying ?: musicActive
        }

        fun pollNow(context: Context) {
            instance?.scanSessionsAndNotifications() ?: run {
                try {
                    if (isNotificationAccessGranted(context)) {
                        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                        val cn = ComponentName(context, NotificationCollectorService::class.java)
                        val controllers = msm?.getActiveSessions(cn).orEmpty()
                        updateFromControllers(controllers)
                    }
                } catch (_: Exception) {
                }
            }
        }

        private fun updateFromControllers(controllers: List<MediaController>) {
            if (controllers.isEmpty()) return
            val active = controllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: controllers.firstOrNull() ?: return

            val meta = active.metadata
            val state = active.playbackState?.state
            if (state != null) {
                isSessionPlaying = (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING)
            }
            if (meta != null) {
                val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM)
                if (!title.isNullOrBlank()) {
                    currentMediaTitle = title
                    currentMediaArtist = artist?.takeIf { it.isNotBlank() } ?: "Memutar Musik"
                }
            }
            onMediaChangedCallback?.invoke()
        }
    }

    private val registeredControllers = mutableListOf<MediaController>()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (state != null) {
                isSessionPlaying = (state.state == PlaybackState.STATE_PLAYING || state.state == PlaybackState.STATE_BUFFERING)
            }
            scanSessionsAndNotifications()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            scanSessionsAndNotifications()
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        bindControllers(controllers.orEmpty())
        updateFromControllers(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val cn = ComponentName(this, NotificationCollectorService::class.java)
            msm?.addOnActiveSessionsChangedListener(sessionsChangedListener, cn)
            val controllers = msm?.getActiveSessions(cn).orEmpty()
            bindControllers(controllers)
        } catch (_: Exception) {
        }
        scanSessionsAndNotifications()
    }

    override fun onListenerDisconnected() {
        instance = null
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            msm?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {
        }
        clearControllers()
        super.onListenerDisconnected()
    }

    private fun bindControllers(controllers: List<MediaController>) {
        clearControllers()
        for (c in controllers) {
            try {
                c.registerCallback(controllerCallback)
                registeredControllers.add(c)
            } catch (_: Exception) {
            }
        }
    }

    private fun clearControllers() {
        for (c in registeredControllers) {
            try {
                c.unregisterCallback(controllerCallback)
            } catch (_: Exception) {
            }
        }
        registeredControllers.clear()
    }

    fun scanSessionsAndNotifications() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val cn = ComponentName(this, NotificationCollectorService::class.java)
            val controllers = msm?.getActiveSessions(cn).orEmpty()
            if (controllers.isNotEmpty()) {
                updateFromControllers(controllers)
            }
        } catch (_: Exception) {
        }

        try {
            val activeNotifs = activeNotifications ?: return
            for (sbn in activeNotifs) {
                extractMediaFromSbn(sbn)
            }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) {
            extractMediaFromSbn(sbn)
        }
        scanSessionsAndNotifications()
    }

    private fun extractMediaFromSbn(sbn: StatusBarNotification) {
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return
        val category = notif.category
        val pkg = sbn.packageName?.lowercase() ?: ""

        // Cek token MediaSession jika ada di dalam notifikasi
        @Suppress("DEPRECATION")
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }

        if (token != null) {
            try {
                val controller = MediaController(this, token)
                updateFromControllers(listOf(controller))
            } catch (_: Exception) {
            }
        }

        if (token != null ||
            category == Notification.CATEGORY_TRANSPORT ||
            pkg.contains("music") ||
            pkg.contains("spotify") ||
            pkg.contains("youtube") ||
            pkg.contains("revanced") ||
            pkg.contains("player")
        ) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            if (!title.isNullOrBlank()) {
                currentMediaTitle = title
                if (!text.isNullOrBlank()) {
                    currentMediaArtist = text
                }
                onMediaChangedCallback?.invoke()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        scanSessionsAndNotifications()
    }
}
