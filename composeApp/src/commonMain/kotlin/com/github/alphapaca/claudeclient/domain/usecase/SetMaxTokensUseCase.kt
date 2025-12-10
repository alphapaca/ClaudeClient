package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class SetMaxTokensUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(maxTokens: Int) {
        settingsRepository.setMaxTokens(maxTokens)
    }
}
