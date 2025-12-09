package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class GetTemperatureUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): Double? {
        return settingsRepository.getTemperature()
    }
}