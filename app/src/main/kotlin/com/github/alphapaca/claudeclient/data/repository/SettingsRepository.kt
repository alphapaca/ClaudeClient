package com.github.alphapaca.claudeclient.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first


class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun getSystemPrompt(): String {
        return dataStore.data.first().toPreferences()[systemPromptKey].orEmpty()
    }

    suspend fun setSystemPrompt(newPrompt: String) {
        dataStore.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[systemPromptKey] = newPrompt
            }
        }
    }

    private companion object {
        val systemPromptKey = stringPreferencesKey("system_prompt")
    }
}