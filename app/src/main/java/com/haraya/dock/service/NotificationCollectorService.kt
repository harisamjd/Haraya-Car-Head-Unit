package com.haraya.dock.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCollectorService : NotificationListenerService() {

    companion object {
        @Volatile
        var currentMediaTitle: String? = null
        @Volatile
        var currentMediaArtist: String? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notif = sbn?.notification ?: return
        val extras = notif.extras ?: return
        val category = notif.category
        val pkg = sbn.packageName ?: ""

        if (category == Notification.CATEGORY_TRANSPORT ||
            pkg.contains("music") ||
            pkg.contains("spotify") ||
            pkg.contains("youtube")
        ) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (!title.isNullOrBlank()) {
                currentMediaTitle = title
                currentMediaArtist = text ?: "Memutar Media"
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    }
}
