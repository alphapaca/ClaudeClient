package com.github.alphapaca.claudeclient.data.notification

import co.touchlab.kermit.Logger
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.border.EmptyBorder

actual fun createNotificationService(): NotificationService = JvmNotificationService()

class JvmNotificationService : NotificationService {

    override fun showNotification(title: String, message: String) {
        Logger.i(TAG) { "Showing notification: $title - $message" }

        // Play system beep to alert user
        try {
            Toolkit.getDefaultToolkit().beep()
        } catch (e: Exception) {
            Logger.w(TAG) { "Could not play beep: ${e.message}" }
        }

        // Show a toast-style popup window
        SwingUtilities.invokeLater {
            showToastNotification(title, message)
        }
    }

    private fun isDarkMode(): Boolean {
        // Check macOS dark mode
        try {
            val process = ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output.equals("Dark", ignoreCase = true)) {
                return true
            }
        } catch (e: Exception) {
            // Not macOS or command failed, fall through
        }

        // Fallback: check if the current L&F background is dark
        try {
            val bgColor = UIManager.getColor("Panel.background")
            if (bgColor != null) {
                // Calculate perceived brightness
                val brightness = (bgColor.red * 299 + bgColor.green * 587 + bgColor.blue * 114) / 1000
                return brightness < 128
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Default to dark mode
        return true
    }

    private fun showToastNotification(title: String, message: String) {
        try {
            val isDark = isDarkMode()

            val backgroundColor = if (isDark) Color(50, 50, 50) else Color(255, 255, 255)
            val titleColor = if (isDark) Color.WHITE else Color(30, 30, 30)
            val messageColor = if (isDark) Color(200, 200, 200) else Color(80, 80, 80)
            val borderColor = if (isDark) Color(80, 80, 80) else Color(200, 200, 200)

            val frame = JFrame().apply {
                isUndecorated = true
                isAlwaysOnTop = true
                background = backgroundColor

                val panel = JPanel(BorderLayout(10, 5)).apply {
                    background = backgroundColor
                    border = javax.swing.border.CompoundBorder(
                        javax.swing.border.LineBorder(borderColor, 1),
                        EmptyBorder(15, 20, 15, 20)
                    )

                    val titleLabel = JLabel(title).apply {
                        foreground = titleColor
                        font = Font("SansSerif", Font.BOLD, 14)
                        horizontalAlignment = SwingConstants.LEFT
                    }

                    val messageLabel = JLabel("<html><body style='width: 250px'>$message</body></html>").apply {
                        foreground = messageColor
                        font = Font("SansSerif", Font.PLAIN, 12)
                        horizontalAlignment = SwingConstants.LEFT
                    }

                    add(titleLabel, BorderLayout.NORTH)
                    add(messageLabel, BorderLayout.CENTER)
                }

                contentPane = panel
                pack()

                // Position in top-right corner of screen
                val screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .defaultScreenDevice.defaultConfiguration.bounds
                val x = screenBounds.width - width - 20
                val y = 50
                setLocation(x, y)
            }

            frame.isVisible = true
            Logger.i(TAG) { "Toast notification displayed (${if (isDark) "dark" else "light"} mode)" }

            // Auto-close after 5 seconds
            Timer(5000) {
                frame.isVisible = false
                frame.dispose()
                Logger.d(TAG) { "Toast notification dismissed" }
            }.apply {
                isRepeats = false
                start()
            }

        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to show toast notification" }
            // Ultimate fallback - simple dialog
            javax.swing.JOptionPane.showMessageDialog(
                null,
                message,
                title,
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    companion object {
        private const val TAG = "NotificationService"
    }
}
