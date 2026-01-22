package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class GetOllamaBaseUrlUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): String {
        return settingsRepository.getOllamaBaseUrl()
    }
}
