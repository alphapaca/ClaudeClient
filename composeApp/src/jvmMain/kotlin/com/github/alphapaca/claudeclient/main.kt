package com.github.alphapaca.claudeclient

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.alphapaca.claudeclient.presentation.App
import org.koin.dsl.module

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ClaudeClient",
    ) {
        ClaudeClientTheme {
            App(module {
                single { createDataStore() }
            })
        }
    }
}