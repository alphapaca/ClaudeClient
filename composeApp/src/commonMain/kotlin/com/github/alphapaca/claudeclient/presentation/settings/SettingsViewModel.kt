package com.github.alphapaca.claudeclient.presentation.settings

import ClaudeClient.composeApp.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.mcp.MCPServerConfig
import com.github.alphapaca.claudeclient.data.mcp.MCPTool
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.usecase.ConnectMCPServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.DisconnectMCPServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMCPConnectionStateUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMCPToolsUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMaxTokensUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMcpServerCommandUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetModelUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetTemperatureUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetMaxTokensUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetMcpServerCommandUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetModelUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetTemperatureUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val getSystemPromptUseCase: GetSystemPromptUseCase,
    private val setSystemPromptUseCase: SetSystemPromptUseCase,
    private val getTemperatureUseCase: GetTemperatureUseCase,
    private val setTemperatureUseCase: SetTemperatureUseCase,
    private val getModelUseCase: GetModelUseCase,
    private val setModelUseCase: SetModelUseCase,
    private val getMaxTokensUseCase: GetMaxTokensUseCase,
    private val setMaxTokensUseCase: SetMaxTokensUseCase,
    private val getMcpServerCommandUseCase: GetMcpServerCommandUseCase,
    private val setMcpServerCommandUseCase: SetMcpServerCommandUseCase,
    private val connectMCPServerUseCase: ConnectMCPServerUseCase,
    private val disconnectMCPServerUseCase: DisconnectMCPServerUseCase,
    private val getMCPConnectionStateUseCase: GetMCPConnectionStateUseCase,
    private val getMCPToolsUseCase: GetMCPToolsUseCase,
) : ViewModel() {

    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt

    private val _temperature = MutableStateFlow("")
    val temperature: StateFlow<String> = _temperature

    private val _maxTokens = MutableStateFlow("")
    val maxTokens: StateFlow<String> = _maxTokens

    private val _selectedModel = MutableStateFlow(LLMModel.DEFAULT)
    val selectedModel: StateFlow<LLMModel> = _selectedModel

    private val _mcpServerCommand = MutableStateFlow("")
    val mcpServerCommand: StateFlow<String> = _mcpServerCommand

    val isMcpConnected: StateFlow<Boolean> = getMCPConnectionStateUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val mcpTools: StateFlow<List<MCPTool>> = getMCPToolsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _mcpConnectionError = MutableStateFlow<String?>(null)
    val mcpConnectionError: StateFlow<String?> = _mcpConnectionError

    private val _isMcpConnecting = MutableStateFlow(false)
    val isMcpConnecting: StateFlow<Boolean> = _isMcpConnecting

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _systemPrompt.value = getSystemPromptUseCase()
            _temperature.value = getTemperatureUseCase()?.toString().orEmpty()
            _maxTokens.value = getMaxTokensUseCase().toString()
            _selectedModel.value = getModelUseCase()
            _mcpServerCommand.value = getMcpServerCommandUseCase().ifBlank { DEFAULT_MCP_COMMAND }
        }
    }

    fun onSystemPromptChange(newPrompt: String) {
        _systemPrompt.value = newPrompt
    }

    fun onTemperatureChange(newTemperature: String) {
        _temperature.value = newTemperature
    }

    fun onMaxTokensChange(newMaxTokens: String) {
        _maxTokens.value = newMaxTokens
    }

    fun onModelChange(model: LLMModel) {
        _selectedModel.value = model
    }

    fun onMcpServerCommandChange(command: String) {
        _mcpServerCommand.value = command
    }

    fun connectMcpServer() {
        val command = _mcpServerCommand.value
        if (command.isBlank()) {
            _mcpConnectionError.value = "Command is empty"
            return
        }

        viewModelScope.launch {
            _isMcpConnecting.value = true
            _mcpConnectionError.value = null

            val parts = command.split(" ").filter { it.isNotBlank() }
            if (parts.isEmpty()) {
                _mcpConnectionError.value = "Invalid command"
                _isMcpConnecting.value = false
                return@launch
            }

            val config = MCPServerConfig(
                name = MCP_SERVER_NAME,
                command = parts.first(),
                args = parts.drop(1),
            )

            connectMCPServerUseCase(config)
                .onSuccess {
                    Logger.i(TAG) { "Connected to MCP server" }
                    _mcpConnectionError.value = null
                }
                .onFailure { error ->
                    Logger.e(TAG, error) { "Failed to connect to MCP server" }
                    _mcpConnectionError.value = error.message ?: "Failed to connect"
                }

            _isMcpConnecting.value = false
        }
    }

    fun disconnectMcpServer() {
        Logger.d(TAG) { "disconnectMcpServer called" }
        viewModelScope.launch {
            Logger.d(TAG) { "Disconnecting from MCP server: $MCP_SERVER_NAME" }
            disconnectMCPServerUseCase(MCP_SERVER_NAME)
            Logger.d(TAG) { "Disconnected from MCP server" }
            _mcpConnectionError.value = null
        }
    }

    fun clearMcpError() {
        _mcpConnectionError.value = null
    }

    fun saveSettings() {
        viewModelScope.launch {
            setSystemPromptUseCase(_systemPrompt.value)
            setTemperatureUseCase(_temperature.value.toDoubleOrNull()?.coerceIn(0.0, 1.0))
            setMaxTokensUseCase(_maxTokens.value.toIntOrNull()?.coerceIn(1, 8192) ?: 1024)
            setModelUseCase(_selectedModel.value)
            setMcpServerCommandUseCase(_mcpServerCommand.value)
            _temperature.value = getTemperatureUseCase()?.toString().orEmpty()
            _maxTokens.value = getMaxTokensUseCase().toString()
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
        const val MCP_SERVER_NAME = "mcp-server"
        private val DEFAULT_MCP_COMMAND = "java -jar ${BuildConfig.HN_MCP_JAR_PATH}"
    }
}
