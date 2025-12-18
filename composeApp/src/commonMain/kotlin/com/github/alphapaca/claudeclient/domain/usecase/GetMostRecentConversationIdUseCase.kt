package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.ConversationRepository

class GetMostRecentConversationIdUseCase(
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(): String? {
        return conversationRepository.getMostRecentConversationId()
    }
}
