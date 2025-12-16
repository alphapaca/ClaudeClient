package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager

class DisconnectMCPServerUseCase(
    private val mcpClientManager: MCPClientManager,
) {
    suspend operator fun invoke(serverName: String) = mcpClientManager.disconnectServer(serverName)
}
