package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager
import com.github.alphapaca.claudeclient.data.mcp.MCPServerConfig

class ConnectMCPServerUseCase(
    private val mcpClientManager: MCPClientManager,
) {
    suspend operator fun invoke(config: MCPServerConfig): Result<Unit> =
        mcpClientManager.connectServer(config)
}
