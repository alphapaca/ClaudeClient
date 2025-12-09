package com.github.alphapaca.claudeclient.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
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

    suspend fun getTemperature(): Double? {
        return dataStore.data.first().toPreferences()[temperatureKey]
    }

    suspend fun setTemperature(temperature: Double?) {
        dataStore.updateData {
            it.toMutablePreferences().also { preferences ->
                if (temperature != null) {
                    preferences[temperatureKey] = temperature
                } else {
                    preferences.remove(temperatureKey)
                }
            }
        }
    }

    private companion object {
        val systemPromptKey = stringPreferencesKey("system_prompt")
        val temperatureKey = doublePreferencesKey("temperature")
    }
}