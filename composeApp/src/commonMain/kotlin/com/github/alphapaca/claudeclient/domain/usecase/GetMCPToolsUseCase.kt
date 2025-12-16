package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager
import com.github.alphapaca.claudeclient.data.mcp.MCPTool
import kotlinx.coroutines.flow.Flow

class GetMCPToolsUseCase(
    private val mcpClientManager: MCPClientManager,
) {
    operator fun invoke(): Flow<List<MCPTool>> = mcpClientManager.availableTools
}
