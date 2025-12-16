package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager

class CallMCPToolUseCase(
    private val mcpClientManager: MCPClientManager,
) {
    suspend operator fun invoke(
        serverName: String,
        toolName: String,
        arguments: Map<String, Any?> = emptyMap(),
    ): Result<String> = mcpClientManager.callTool(serverName, toolName, arguments)
}
