package com.github.alphapaca.claudeclient.data.mcp

import kotlinx.coroutines.flow.StateFlow

interface MCPClientManager {
    val connectedServers: StateFlow<List<String>>
    val availableTools: StateFlow<List<MCPTool>>

    suspend fun connectServer(config: MCPServerConfig): Result<Unit>
    suspend fun disconnectServer(serverName: String)
    suspend fun disconnectAll()

    suspend fun listTools(): List<MCPTool>
    suspend fun callTool(serverName: String, toolName: String, arguments: Map<String, Any?>): Result<String>
}
