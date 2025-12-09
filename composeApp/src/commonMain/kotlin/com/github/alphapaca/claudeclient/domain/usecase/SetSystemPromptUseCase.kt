package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class SetSystemPromptUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(newPrompt: String) {
        settingsRepository.setSystemPrompt(newPrompt)
    }
}