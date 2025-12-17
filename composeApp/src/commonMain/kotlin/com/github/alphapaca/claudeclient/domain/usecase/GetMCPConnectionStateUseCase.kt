package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetMCPConnectionStateUseCase(
    private val mcpClientManager: MCPClientManager,
) {
    operator fun invoke(): Flow<Boolean> = mcpClientManager.connectedServers.map { it.isNotEmpty() }
}
