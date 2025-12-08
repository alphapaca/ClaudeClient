package com.github.alphapaca.claudeclient.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.github.alphapaca.claudeclient.presentation.chat.ChatScreen
import com.github.alphapaca.claudeclient.presentation.navigation.ChatKey
import com.github.alphapaca.claudeclient.presentation.navigation.SettingsKey
import com.github.alphapaca.claudeclient.presentation.settings.SettingsScreen
import com.github.alphapaca.claudeclient.presentation.theme.ClaudeClientTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClaudeClientTheme {
                val backStack = remember { mutableStateListOf<Any>(ChatKey) }

                Scaffold(modifier = Modifier.imePadding().fillMaxSize()) { innerPadding ->
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
                            entry<ChatKey> {
                                ChatScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onSettingsClick = { backStack.add(SettingsKey) }
                                )
                            }
                            entry<SettingsKey> {
                                SettingsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onBackClick = { backStack.removeLastOrNull() }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}