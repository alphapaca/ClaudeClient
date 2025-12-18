package com.github.alphapaca.claudeclient.data.notification

interface NotificationService {
    fun showNotification(title: String, message: String)
}

expect fun createNotificationService(): NotificationService
