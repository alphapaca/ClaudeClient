package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager
import com.github.alphapaca.claudeclient.data.mcp.ReminderNotification
import com.github.alphapaca.claudeclient.data.notification.NotificationService
import kotlinx.coroutines.flow.SharedFlow

/**
 * Use case that provides access to reminder notifications from MCP servers.
 * Consumers should collect this flow and handle the notifications (e.g., show desktop notification).
 */
class ObserveReminderNotificationsUseCase(
    private val mcpClientManager: MCPClientManager,
    private val notificationService: NotificationService,
) {
    operator fun invoke(): SharedFlow<ReminderNotification> = mcpClientManager.reminderNotifications

    /**
     * Shows a system notification for the given reminder.
     */
    fun showNotification(message: String) {
        notificationService.showNotification(
            title = "Reminder",
            message = message,
        )
    }
}
