package com.haraya.dock.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCollectorService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Akan mengumpulkan petunjuk belokan Google Maps & status media player
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    }
}

