package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class SetTemperatureUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(temperature: Double?) {
        settingsRepository.setTemperature(temperature)
    }
}