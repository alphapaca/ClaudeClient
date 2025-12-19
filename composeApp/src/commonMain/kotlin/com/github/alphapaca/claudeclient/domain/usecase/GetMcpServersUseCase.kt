package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.mcp.MCPServerConfig
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetMcpServersUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): List<MCPServerConfig> = settingsRepository.getMcpServers()

    fun asFlow(): Flow<List<MCPServerConfig>> = settingsRepository.getMcpServersFlow()
}
