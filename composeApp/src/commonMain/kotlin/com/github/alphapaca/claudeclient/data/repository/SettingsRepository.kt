package com.github.alphapaca.claudeclient.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun getModel(): LLMModel {
        val apiName = dataStore.data.first().toPreferences()[modelKey] ?: LLMModel.DEFAULT.apiName
        return LLMModel.fromApiName(apiName)
    }

    suspend fun setModel(model: LLMModel) {
        dataStore.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[modelKey] = model.apiName
            }
        }
    }
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

    fun getTemperatureFlow(): Flow<Double?> {
        return dataStore.data.map { it[temperatureKey] }
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
        val modelKey = stringPreferencesKey("model")
    }
}