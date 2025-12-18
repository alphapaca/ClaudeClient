package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.ConversationRepository

class ResetUnreadCountUseCase(
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(conversationId: String) {
        conversationRepository.resetUnreadCount(conversationId)
    }
}
