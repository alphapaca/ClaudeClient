package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.mcp.MCPServerConfig
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class AddMcpServerUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(server: MCPServerConfig) = settingsRepository.addMcpServer(server)
}
