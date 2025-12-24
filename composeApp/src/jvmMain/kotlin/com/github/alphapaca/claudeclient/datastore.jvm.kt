package com.github.alphapaca.claudeclient

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

fun createDataStore(): DataStore<Preferences> = createDataStore(
    producePath = {
        val dir = File(System.getProperty("user.home"), ".claudeclient")
        dir.mkdirs()
        File(dir, dataStoreFileName).absolutePath
    }
)