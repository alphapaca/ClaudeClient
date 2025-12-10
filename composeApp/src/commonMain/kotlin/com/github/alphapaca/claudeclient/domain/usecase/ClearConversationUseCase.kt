package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.ConversationRepository

class ClearConversationUseCase(
    private val conversationRepository: ConversationRepository,
) {
    operator fun invoke() {
        conversationRepository.clearConversation()
    }
}