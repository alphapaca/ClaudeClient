package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class GetSystemPromptUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): String {
        return settingsRepository.getSystemPrompt()
    }
}