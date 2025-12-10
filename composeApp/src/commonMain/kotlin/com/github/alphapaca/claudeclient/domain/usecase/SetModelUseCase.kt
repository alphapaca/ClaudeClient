package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository
import com.github.alphapaca.claudeclient.domain.model.LLMModel

class SetModelUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(model: LLMModel) {
        settingsRepository.setModel(model)
    }
}
