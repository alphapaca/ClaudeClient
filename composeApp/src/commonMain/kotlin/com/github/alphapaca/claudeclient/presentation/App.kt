package com.github.alphapaca.claudeclient.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.github.alphapaca.claudeclient.di.appModule
import com.github.alphapaca.claudeclient.domain.usecase.AutoConnectMCPServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.ObserveReminderNotificationsUseCase
import com.github.alphapaca.claudeclient.presentation.chat.ChatScreen
import com.github.alphapaca.claudeclient.presentation.codesession.CodeSessionScreenWrapper
import com.github.alphapaca.claudeclient.presentation.navigation.ChatKey
import com.github.alphapaca.claudeclient.presentation.navigation.CodeSessionKey
import com.github.alphapaca.claudeclient.presentation.navigation.SettingsKey
import com.github.alphapaca.claudeclient.presentation.settings.SettingsScreen
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.module.Module

@Composable
fun App(platformModule: Module) {
    KoinApplication(application = {
        modules(platformModule, appModule)
    }) {
        val backStack = remember { mutableStateListOf<Any>(ChatKey) }

        // Auto-connect to MCP server if configured
        val autoConnectMCPServerUseCase = koinInject<AutoConnectMCPServerUseCase>()
        LaunchedEffect(Unit) {
            autoConnectMCPServerUseCase()
        }

        // Observe reminder notifications and show system notifications
        val observeReminderNotificationsUseCase = koinInject<ObserveReminderNotificationsUseCase>()
        LaunchedEffect(Unit) {
            co.touchlab.kermit.Logger.i("App") { "Starting to observe reminder notifications..." }
            observeReminderNotificationsUseCase().collect { notification ->
                co.touchlab.kermit.Logger.i("App") { "Received reminder notification: ${notification.reminderId} - ${notification.message}" }
                // Show the actual reminder message in the notification
                val displayMessage = notification.message ?: "Reminder #${notification.reminderId}"
                observeReminderNotificationsUseCase.showNotification(displayMessage)
            }
        }

        Scaffold(modifier = Modifier.Companion.imePadding().fillMaxSize()) { innerPadding ->
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<ChatKey> {
                        ChatScreen(
                            modifier = Modifier.Companion.padding(innerPadding),
                            onSettingsClick = { backStack.add(SettingsKey) },
                            onCodeSessionClick = { backStack.add(CodeSessionKey) },
                        )
                    }
                    entry<SettingsKey> {
                        SettingsScreen(
                            modifier = Modifier.Companion.padding(innerPadding),
                            onBackClick = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<CodeSessionKey> {
                        CodeSessionScreenWrapper(
                            modifier = Modifier.Companion.padding(innerPadding),
                            onBackClick = { backStack.removeLastOrNull() },
                        )
                    }
                }
            )
        }
    }
}
