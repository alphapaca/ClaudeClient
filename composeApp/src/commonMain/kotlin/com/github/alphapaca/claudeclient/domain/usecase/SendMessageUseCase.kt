package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.ConversationRepository
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class SendMessageUseCase(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Sends a message to the conversation.
     * If conversationId is NEW_CONVERSATION_ID (-1), creates a new conversation.
     * @return the actual conversationId used (may be newly created)
     */
    suspend operator fun invoke(conversationId: Long, message: String): Long {
        val model = settingsRepository.getModel()
        val systemPrompt = settingsRepository.getSystemPrompt()
        val temperature = settingsRepository.getTemperature()
        val maxTokens = settingsRepository.getMaxTokens()
        return conversationRepository.sendMessage(conversationId, message, model, systemPrompt, temperature, maxTokens)
    }
}