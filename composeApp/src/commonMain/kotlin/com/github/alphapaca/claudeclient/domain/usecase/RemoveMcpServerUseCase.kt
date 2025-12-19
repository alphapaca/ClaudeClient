package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class RemoveMcpServerUseCase(
    private val settingsRepository: SettingsRepository,
    private val mcpClientManager: MCPClientManager,
) {
    suspend operator fun invoke(serverName: String) {
        mcpClientManager.disconnectServer(serverName)
        settingsRepository.removeMcpServer(serverName)
    }
}
