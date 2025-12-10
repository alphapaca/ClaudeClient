package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository
import com.github.alphapaca.claudeclient.domain.model.LLMModel

class GetModelUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): LLMModel {
        return settingsRepository.getModel()
    }
}
