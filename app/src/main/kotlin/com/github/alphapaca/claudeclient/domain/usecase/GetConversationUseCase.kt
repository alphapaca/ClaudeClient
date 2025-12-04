package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.ConversationRepository
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import kotlinx.coroutines.flow.Flow

class GetConversationUseCase(
    private val conversationRepository: ConversationRepository,
) {
    operator fun invoke(): Flow<List<ConversationItem>> {
        return conversationRepository.getConversation()
    }
}