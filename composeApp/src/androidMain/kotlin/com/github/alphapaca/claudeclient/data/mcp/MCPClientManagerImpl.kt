package com.github.alphapaca.claudeclient.data.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MCPClientManagerImpl : MCPClientManager {

    private val _connectedServers = MutableStateFlow<List<String>>(emptyList())
    override val connectedServers: StateFlow<List<String>> = _connectedServers.asStateFlow()

    private val _availableTools = MutableStateFlow<List<MCPTool>>(emptyList())
    override val availableTools: StateFlow<List<MCPTool>> = _availableTools.asStateFlow()

    override suspend fun connectServer(config: MCPServerConfig): Result<Unit> {
        return Result.failure(UnsupportedOperationException("MCP servers are not supported on Android"))
    }

    override suspend fun disconnectServer(serverName: String) {
        // No-op on Android
    }

    override suspend fun disconnectAll() {
        // No-op on Android
    }

    override suspend fun listTools(): List<MCPTool> {
        return emptyList()
    }

    override suspend fun callTool(
        serverName: String,
        toolName: String,
        arguments: Map<String, Any?>
    ): Result<String> {
        return Result.failure(UnsupportedOperationException("MCP servers are not supported on Android"))
    }
}
