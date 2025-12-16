package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class SetMcpServerCommandUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(command: String) = settingsRepository.setMcpServerCommand(command)
}
