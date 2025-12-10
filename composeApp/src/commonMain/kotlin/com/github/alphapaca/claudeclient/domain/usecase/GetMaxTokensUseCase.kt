package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class GetMaxTokensUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): Int {
        return settingsRepository.getMaxTokens()
    }
}
