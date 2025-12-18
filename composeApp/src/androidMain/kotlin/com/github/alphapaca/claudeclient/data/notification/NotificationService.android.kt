package com.github.alphapaca.claudeclient.data.notification

import co.touchlab.kermit.Logger

actual fun createNotificationService(): NotificationService = AndroidNotificationService()

class AndroidNotificationService : NotificationService {
    override fun showNotification(title: String, message: String) {
        // TODO: Implement Android notification using NotificationManager
        Logger.i(TAG) { "Android notification (not implemented): $title - $message" }
    }

    companion object {
        private const val TAG = "NotificationService"
    }
}
