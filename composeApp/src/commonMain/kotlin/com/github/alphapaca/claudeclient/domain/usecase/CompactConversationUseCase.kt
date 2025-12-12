package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.ConversationRepository
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class CompactConversationUseCase(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() {
        val model = settingsRepository.getModel()
        val systemPrompt = settingsRepository.getSystemPrompt()
        val temperature = settingsRepository.getTemperature()
        val maxTokens = settingsRepository.getMaxTokens()
        conversationRepository.compactConversation(
            model = model,
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens,
        )
    }
}
