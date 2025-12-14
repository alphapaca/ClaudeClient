package com.github.alphapaca.claudeclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.alphapaca.claudeclient.data.db.ClaudeClientDatabase
import com.github.alphapaca.claudeclient.presentation.App
import org.koin.dsl.module

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClaudeClientTheme {
                App(module {
                    single { createDataStore(this@MainActivity) }
                    single { createDatabaseDriver(this@MainActivity) }
                    single { ClaudeClientDatabase(get()) }
                })
            }
        }
    }
}