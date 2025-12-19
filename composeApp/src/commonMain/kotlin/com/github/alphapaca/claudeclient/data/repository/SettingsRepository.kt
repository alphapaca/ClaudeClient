package com.github.alphapaca.claudeclient.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.alphapaca.claudeclient.data.mcp.MCPServerConfig
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json


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

    suspend fun getMaxTokens(): Int {
        return dataStore.data.first().toPreferences()[maxTokensKey] ?: DEFAULT_MAX_TOKENS
    }

    suspend fun setMaxTokens(maxTokens: Int) {
        dataStore.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[maxTokensKey] = maxTokens
            }
        }
    }

    suspend fun getMcpServerCommand(): String {
        return dataStore.data.first().toPreferences()[mcpServerCommandKey].orEmpty()
    }

    suspend fun setMcpServerCommand(command: String) {
        dataStore.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[mcpServerCommandKey] = command
            }
        }
    }

    suspend fun getMcpServers(): List<MCPServerConfig> {
        val json = dataStore.data.first().toPreferences()[mcpServersKey].orEmpty()
        return if (json.isBlank()) emptyList()
        else runCatching { Json.decodeFromString<List<MCPServerConfig>>(json) }.getOrElse { emptyList() }
    }

    fun getMcpServersFlow(): Flow<List<MCPServerConfig>> {
        return dataStore.data.map { preferences ->
            val json = preferences[mcpServersKey].orEmpty()
            if (json.isBlank()) emptyList()
            else runCatching { Json.decodeFromString<List<MCPServerConfig>>(json) }.getOrElse { emptyList() }
        }
    }

    suspend fun setMcpServers(servers: List<MCPServerConfig>) {
        dataStore.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[mcpServersKey] = Json.encodeToString(servers)
            }
        }
    }

    suspend fun addMcpServer(server: MCPServerConfig) {
        val current = getMcpServers().toMutableList()
        current.removeAll { it.name == server.name }
        current.add(server)
        setMcpServers(current)
    }

    suspend fun removeMcpServer(serverName: String) {
        val current = getMcpServers().filter { it.name != serverName }
        setMcpServers(current)
    }

    suspend fun isMcpServersInitialized(): Boolean {
        return dataStore.data.first().toPreferences()[mcpServersInitializedKey] == true
    }

    suspend fun setMcpServersInitialized() {
        dataStore.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[mcpServersInitializedKey] = true
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_TOKENS = 1024
        val systemPromptKey = stringPreferencesKey("system_prompt")
        val temperatureKey = doublePreferencesKey("temperature")
        val modelKey = stringPreferencesKey("model")
        val maxTokensKey = intPreferencesKey("max_tokens")
        val mcpServerCommandKey = stringPreferencesKey("mcp_server_command")
        val mcpServersKey = stringPreferencesKey("mcp_servers")
        val mcpServersInitializedKey = booleanPreferencesKey("mcp_servers_initialized")
    }
}