package com.github.alphapaca.claudeclient.data.mcp

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a reminder notification received from an MCP server.
 * @param uri The resource URI (e.g., "reminder://123?message=Hello")
 * @param reminderId The parsed reminder ID from the URI
 * @param message The reminder message (URL-decoded from the URI query parameter)
 */
data class ReminderNotification(
    val uri: String,
    val reminderId: Long?,
    val message: String?,
)

interface MCPClientManager {
    val connectedServers: StateFlow<List<String>>
    val availableTools: StateFlow<List<MCPTool>>

    /** Flow of reminder notifications received from connected MCP servers */
    val reminderNotifications: SharedFlow<ReminderNotification>

    suspend fun connectServer(config: MCPServerConfig): Result<Unit>
    suspend fun disconnectServer(serverName: String)
    suspend fun disconnectAll()

    suspend fun listTools(): List<MCPTool>
    suspend fun callTool(serverName: String, toolName: String, arguments: Map<String, Any?>): Result<String>
}
