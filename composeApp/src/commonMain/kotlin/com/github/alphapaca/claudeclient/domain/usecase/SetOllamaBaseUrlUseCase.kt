package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class SetOllamaBaseUrlUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(url: String) {
        settingsRepository.setOllamaBaseUrl(url)
    }
}
