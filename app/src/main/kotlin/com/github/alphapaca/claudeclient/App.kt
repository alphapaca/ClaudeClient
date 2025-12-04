package com.github.alphapaca.claudeclient

import android.app.Application
import com.github.alphapaca.claudeclient.di.chatModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(chatModule)
        }
    }
}