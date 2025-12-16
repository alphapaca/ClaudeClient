package com.github.alphapaca.claudeclient.presentation.checktools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.mcp.MCPServerConfig
import com.github.alphapaca.claudeclient.data.mcp.MCPTool
import com.github.alphapaca.claudeclient.domain.usecase.ConnectMCPServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.DisconnectMCPServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMCPToolsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CheckToolsViewModel(
    private val connectMCPServerUseCase: ConnectMCPServerUseCase,
    private val disconnectMCPServerUseCase: DisconnectMCPServerUseCase,
    private val getMCPToolsUseCase: GetMCPToolsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CheckToolsUiState>(CheckToolsUiState.Loading)
    val uiState: StateFlow<CheckToolsUiState> = _uiState.asStateFlow()

    private var connectedServerName: String? = null

    fun checkTools(command: String) {
        if (command.isBlank()) {
            _uiState.value = CheckToolsUiState.Error("Command is empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = CheckToolsUiState.Loading

            val parts = command.split(" ").filter { it.isNotBlank() }
            if (parts.isEmpty()) {
                _uiState.value = CheckToolsUiState.Error("Invalid command")
                return@launch
            }

            val config = MCPServerConfig(
                name = "check-tools-server",
                command = parts.first(),
                args = parts.drop(1),
            )

            connectMCPServerUseCase(config)
                .onSuccess {
                    connectedServerName = config.name
                    val tools = getMCPToolsUseCase().first()
                    _uiState.value = CheckToolsUiState.Success(tools)
                }
                .onFailure { error ->
                    Logger.e("CheckTools", error) { "Failed to connect to MCP server" }
                    _uiState.value = CheckToolsUiState.Error(
                        error.message ?: "Failed to connect to MCP server"
                    )
                }
        }
    }

    fun disconnect() {
        connectedServerName?.let { serverName ->
            viewModelScope.launch {
                disconnectMCPServerUseCase(serverName)
                connectedServerName = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

sealed interface CheckToolsUiState {
    data object Loading : CheckToolsUiState
    data class Success(val tools: List<MCPTool>) : CheckToolsUiState
    data class Error(val message: String) : CheckToolsUiState
}
