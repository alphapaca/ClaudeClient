package com.github.alphapaca.claudeclient.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.github.alphapaca.claudeclient.di.appModule
import com.github.alphapaca.claudeclient.presentation.chat.ChatScreen
import com.github.alphapaca.claudeclient.presentation.checktools.CheckToolsScreen
import com.github.alphapaca.claudeclient.presentation.navigation.ChatKey
import com.github.alphapaca.claudeclient.presentation.navigation.CheckToolsKey
import com.github.alphapaca.claudeclient.presentation.navigation.SettingsKey
import com.github.alphapaca.claudeclient.presentation.settings.SettingsScreen
import org.koin.compose.KoinApplication
import org.koin.core.module.Module

@Composable
fun App(platformModule: Module) {
    KoinApplication(application = {
        modules(platformModule, appModule)
    }) {
        val backStack = remember { mutableStateListOf<Any>(ChatKey) }

        Scaffold(modifier = Modifier.Companion.imePadding().fillMaxSize()) { innerPadding ->
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<ChatKey> {
                        ChatScreen(
                            modifier = Modifier.Companion.padding(innerPadding),
                            onSettingsClick = { backStack.add(SettingsKey) }
                        )
                    }
                    entry<SettingsKey> {
                        SettingsScreen(
                            modifier = Modifier.Companion.padding(innerPadding),
                            onBackClick = { backStack.removeLastOrNull() },
                            onCheckToolsClick = { command -> backStack.add(CheckToolsKey(command)) }
                        )
                    }
                    entry<CheckToolsKey> { key ->
                        CheckToolsScreen(
                            command = key.command,
                            modifier = Modifier.Companion.padding(innerPadding),
                            onBackClick = { backStack.removeLastOrNull() }
                        )
                    }
                }
            )
        }
    }
}