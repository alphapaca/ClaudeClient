package com.github.alphapaca.claudeclient

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.alphapaca.claudeclient.data.db.ClaudeClientDatabase
import com.github.alphapaca.claudeclient.di.codeSessionModule
import com.github.alphapaca.claudeclient.di.systemAnalysisModule
import com.github.alphapaca.claudeclient.presentation.App
import org.koin.dsl.module

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ClaudeClient",
    ) {
        ClaudeClientTheme {
            val platformModule = module {
                single { createDataStore() }
                single { createDatabaseDriver() }
                single { ClaudeClientDatabase(get()) }
                includes(codeSessionModule)
                includes(systemAnalysisModule)
            }
            App(platformModule)
        }
    }
}