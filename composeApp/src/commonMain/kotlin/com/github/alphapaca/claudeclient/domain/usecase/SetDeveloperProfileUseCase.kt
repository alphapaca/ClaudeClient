package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class SetDeveloperProfileUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(profile: String) {
        settingsRepository.setDeveloperProfile(profile)
    }
}
