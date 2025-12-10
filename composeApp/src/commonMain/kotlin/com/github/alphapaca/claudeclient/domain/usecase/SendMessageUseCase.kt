package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.ConversationRepository
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class SendMessageUseCase(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(message: String) {
        val model = settingsRepository.getModel()
        val systemPrompt = settingsRepository.getSystemPrompt()
        val temperature = settingsRepository.getTemperature()
        val maxTokens = settingsRepository.getMaxTokens()
        return conversationRepository.sendMessage(message, model, systemPrompt, temperature, maxTokens)
    }
}