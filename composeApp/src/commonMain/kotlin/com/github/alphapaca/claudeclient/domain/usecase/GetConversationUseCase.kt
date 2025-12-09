package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.ConversationRepository
import com.github.alphapaca.claudeclient.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

class GetConversationUseCase(
    private val conversationRepository: ConversationRepository,
) {
    operator fun invoke(): Flow<Conversation> {
        return conversationRepository.getConversation()
    }
}