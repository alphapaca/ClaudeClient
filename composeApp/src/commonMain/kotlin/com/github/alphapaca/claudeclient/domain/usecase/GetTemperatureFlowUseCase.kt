package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetTemperatureFlowUseCase(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<Double?> {
        return settingsRepository.getTemperatureFlow()
    }
}