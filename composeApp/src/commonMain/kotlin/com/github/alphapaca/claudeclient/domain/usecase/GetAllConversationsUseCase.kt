package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.ConversationRepository
import com.github.alphapaca.claudeclient.domain.model.ConversationInfo
import kotlinx.coroutines.flow.Flow

class GetAllConversationsUseCase(
    private val conversationRepository: ConversationRepository,
) {
    operator fun invoke(): Flow<List<ConversationInfo>> {
        return conversationRepository.getAllConversations()
    }
}
