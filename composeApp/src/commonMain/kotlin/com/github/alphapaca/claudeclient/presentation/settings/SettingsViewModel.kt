package com.github.alphapaca.claudeclient.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager
import com.github.alphapaca.claudeclient.data.mcp.MCPServerConfig
import com.github.alphapaca.claudeclient.data.mcp.MCPTool
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.usecase.AddMcpServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.ConnectMCPServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.DisconnectMCPServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetDeveloperProfileUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMCPToolsUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMaxTokensUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMcpServersUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetModelUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetOllamaBaseUrlUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetTemperatureUseCase
import com.github.alphapaca.claudeclient.domain.usecase.RemoveMcpServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetDeveloperProfileUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetMaxTokensUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetModelUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetOllamaBaseUrlUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetTemperatureUseCase
import com.github.alphapaca.claudeclient.domain.usecase.TestOllamaConnectionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val getOllamaBaseUrlUseCase: GetOllamaBaseUrlUseCase,
    private val setOllamaBaseUrlUseCase: SetOllamaBaseUrlUseCase,
    private val testOllamaConnectionUseCase: TestOllamaConnectionUseCase,
    private val getMcpServersUseCase: GetMcpServersUseCase,
    private val addMcpServerUseCase: AddMcpServerUseCase,
    private val removeMcpServerUseCase: RemoveMcpServerUseCase,
    private val connectMCPServerUseCase: ConnectMCPServerUseCase,
    private val disconnectMCPServerUseCase: DisconnectMCPServerUseCase,
    private val getMCPToolsUseCase: GetMCPToolsUseCase,
    private val mcpClientManager: MCPClientManager,
    private val getDeveloperProfileUseCase: GetDeveloperProfileUseCase,
    private val setDeveloperProfileUseCase: SetDeveloperProfileUseCase,
) : ViewModel() {

    // Main settings state
    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt

    private val _temperature = MutableStateFlow("")
    val temperature: StateFlow<String> = _temperature

    private val _maxTokens = MutableStateFlow("")
    val maxTokens: StateFlow<String> = _maxTokens

    private val _selectedModel = MutableStateFlow(LLMModel.DEFAULT)
    val selectedModel: StateFlow<LLMModel> = _selectedModel

    private val _ollamaBaseUrl = MutableStateFlow("")
    val ollamaBaseUrl: StateFlow<String> = _ollamaBaseUrl

    private val _developerProfile = MutableStateFlow("")
    val developerProfile: StateFlow<String> = _developerProfile

    // Ollama connection test state
    private val _ollamaTestState = MutableStateFlow<OllamaTestState>(OllamaTestState.Idle)
    val ollamaTestState: StateFlow<OllamaTestState> = _ollamaTestState

    // MCP multi-server state
    val mcpServers: StateFlow<List<MCPServerConfig>> = getMcpServersUseCase.asFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectedServers: StateFlow<List<String>> = mcpClientManager.connectedServers

    private val _connectingServers = MutableStateFlow<Set<String>>(emptySet())
    val connectingServers: StateFlow<Set<String>> = _connectingServers

    private val _serverErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val serverErrors: StateFlow<Map<String, String>> = _serverErrors

    val mcpToolsByServer: StateFlow<Map<String, List<MCPTool>>> = getMCPToolsUseCase()
        .map { tools -> tools.groupBy { it.serverName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _showAddServerDialog = MutableStateFlow(false)
    val showAddServerDialog: StateFlow<Boolean> = _showAddServerDialog

    // Track initial values to detect changes (using StateFlow so combine reacts to changes)
    private val _initialSystemPrompt = MutableStateFlow<String?>(null)
    private val _initialTemperature = MutableStateFlow<String?>(null)
    private val _initialMaxTokens = MutableStateFlow<String?>(null)
    private val _initialSelectedModel = MutableStateFlow<LLMModel?>(null)
    private val _initialOllamaBaseUrl = MutableStateFlow<String?>(null)
    private val _initialDeveloperProfile = MutableStateFlow<String?>(null)

    private data class SettingsState(
        val systemPrompt: String,
        val temperature: String,
        val maxTokens: String,
        val model: LLMModel,
        val ollamaBaseUrl: String,
        val developerProfile: String,
    )

    private val _currentSettings = combine(
        combine(_systemPrompt, _temperature, _maxTokens) { a, b, c -> Triple(a, b, c) },
        combine(_selectedModel, _ollamaBaseUrl, _developerProfile) { a, b, c -> Triple(a, b, c) }
    ) { (systemPrompt, temperature, maxTokens), (model, ollamaBaseUrl, developerProfile) ->
        SettingsState(systemPrompt, temperature, maxTokens, model, ollamaBaseUrl, developerProfile)
    }

    private val _initialSettings = combine(
        combine(_initialSystemPrompt, _initialTemperature, _initialMaxTokens) { a, b, c -> Triple(a, b, c) },
        combine(_initialSelectedModel, _initialOllamaBaseUrl, _initialDeveloperProfile) { a, b, c -> Triple(a, b, c) }
    ) { (systemPrompt, temperature, maxTokens), (model, ollamaBaseUrl, developerProfile) ->
        if (systemPrompt == null || temperature == null || maxTokens == null || model == null || ollamaBaseUrl == null || developerProfile == null) {
            null
        } else {
            SettingsState(systemPrompt, temperature, maxTokens, model, ollamaBaseUrl, developerProfile)
        }
    }

    val hasChanges: StateFlow<Boolean> = combine(_currentSettings, _initialSettings) { current, initial ->
        // Don't show changes until initial values are loaded
        if (initial == null) false else current != initial
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _systemPrompt.value = getSystemPromptUseCase()
            _temperature.value = getTemperatureUseCase()?.toString().orEmpty()
            _maxTokens.value = getMaxTokensUseCase().toString()
            _selectedModel.value = getModelUseCase()
            _ollamaBaseUrl.value = getOllamaBaseUrlUseCase()
            _developerProfile.value = getDeveloperProfileUseCase()

            // Store initial values for change detection
            _initialSystemPrompt.value = _systemPrompt.value
            _initialTemperature.value = _temperature.value
            _initialMaxTokens.value = _maxTokens.value
            _initialSelectedModel.value = _selectedModel.value
            _initialOllamaBaseUrl.value = _ollamaBaseUrl.value
            _initialDeveloperProfile.value = _developerProfile.value
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

    fun onOllamaBaseUrlChange(url: String) {
        _ollamaBaseUrl.value = url
        _ollamaTestState.value = OllamaTestState.Idle
    }

    fun onDeveloperProfileChange(profile: String) {
        _developerProfile.value = profile
    }

    fun testOllamaConnection() {
        val url = _ollamaBaseUrl.value.ifBlank { "http://localhost:11434/" }
        viewModelScope.launch {
            _ollamaTestState.value = OllamaTestState.Testing
            testOllamaConnectionUseCase(url)
                .onSuccess { message ->
                    _ollamaTestState.value = OllamaTestState.Success(message)
                }
                .onFailure { error ->
                    _ollamaTestState.value = OllamaTestState.Error(error.message ?: "Connection failed")
                }
        }
    }

    // MCP Server management
    fun showAddServerDialog() {
        _showAddServerDialog.value = true
    }

    fun hideAddServerDialog() {
        _showAddServerDialog.value = false
    }

    fun addServer(name: String, command: String) {
        val parts = command.split(" ").filter { it.isNotBlank() }
        if (parts.isEmpty()) return

        val config = MCPServerConfig(
            name = name,
            command = parts.first(),
            args = parts.drop(1),
        )

        viewModelScope.launch {
            addMcpServerUseCase(config)
            Logger.i(TAG) { "Added MCP server: $name" }
        }
    }

    fun removeServer(serverName: String) {
        viewModelScope.launch {
            removeMcpServerUseCase(serverName)
            _serverErrors.value = _serverErrors.value - serverName
            Logger.i(TAG) { "Removed MCP server: $serverName" }
        }
    }

    fun connectServer(serverName: String) {
        val server = mcpServers.value.find { it.name == serverName } ?: return

        viewModelScope.launch {
            _connectingServers.value = _connectingServers.value + serverName
            _serverErrors.value = _serverErrors.value - serverName

            connectMCPServerUseCase(server)
                .onSuccess {
                    Logger.i(TAG) { "Connected to MCP server: $serverName" }
                }
                .onFailure { error ->
                    Logger.e(TAG, error) { "Failed to connect to MCP server: $serverName" }
                    _serverErrors.value = _serverErrors.value + (serverName to (error.message ?: "Failed to connect"))
                }

            _connectingServers.value = _connectingServers.value - serverName
        }
    }

    fun disconnectServer(serverName: String) {
        viewModelScope.launch {
            disconnectMCPServerUseCase(serverName)
            _serverErrors.value = _serverErrors.value - serverName
            Logger.i(TAG) { "Disconnected from MCP server: $serverName" }
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            setSystemPromptUseCase(_systemPrompt.value)
            setTemperatureUseCase(_temperature.value.toDoubleOrNull()?.coerceIn(0.0, 1.0))
            setMaxTokensUseCase(_maxTokens.value.toIntOrNull()?.coerceIn(1, 8192) ?: 1024)
            setModelUseCase(_selectedModel.value)
            setOllamaBaseUrlUseCase(_ollamaBaseUrl.value.ifBlank { "http://localhost:11434/" })
            setDeveloperProfileUseCase(_developerProfile.value)
            _temperature.value = getTemperatureUseCase()?.toString().orEmpty()
            _maxTokens.value = getMaxTokensUseCase().toString()

            // Update initial values so hasChanges becomes false
            _initialSystemPrompt.value = _systemPrompt.value
            _initialTemperature.value = _temperature.value
            _initialMaxTokens.value = _maxTokens.value
            _initialSelectedModel.value = _selectedModel.value
            _initialOllamaBaseUrl.value = _ollamaBaseUrl.value
            _initialDeveloperProfile.value = _developerProfile.value
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}

sealed class OllamaTestState {
    data object Idle : OllamaTestState()
    data object Testing : OllamaTestState()
    data class Success(val message: String) : OllamaTestState()
    data class Error(val message: String) : OllamaTestState()
}
