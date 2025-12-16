package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class GetMcpServerCommandUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): String = settingsRepository.getMcpServerCommand()
}
