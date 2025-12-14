package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.ConversationRepository

class ClearConversationUseCase(
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(conversationId: Long) {
        conversationRepository.clearConversation(conversationId)
    }
}