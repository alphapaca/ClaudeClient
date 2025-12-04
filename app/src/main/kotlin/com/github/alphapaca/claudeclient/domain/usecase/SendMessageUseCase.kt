package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.repository.ConversationRepository

class SendMessageUseCase(
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(message: String) {

        return conversationRepository.sendMessage(message)
    }
}