package com.github.alphapaca.claudeclient.data.repository

import com.github.alphapaca.claudeclient.data.api.MessageRequest
import com.github.alphapaca.claudeclient.data.api.MessageResponse
import com.github.alphapaca.claudeclient.data.mapper.ConversationApiMapper
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

class ConversationRepository(
    private val client: HttpClient,
    private val mapper: ConversationApiMapper,
) {
    private val conversation: MutableStateFlow<List<ConversationItem>> = MutableStateFlow(emptyList())

    fun getConversation(): Flow<List<ConversationItem>> = conversation.asSharedFlow()

    suspend fun sendMessage(message: String, systemPrompt: String) {
        conversation.value += ConversationItem.Text(ConversationItem.Text.Role.USER, message)
        val request = MessageRequest(
            model = "claude-sonnet-4-5",
            maxTokens = 1024,
            messages = conversation.value.map { mapper.toApiMessage(it) },
            system = systemPrompt.takeIf(String::isNotBlank),
        )
        val response = sendMessageToServer(request)
        conversation.value += listOfNotNull(mapper.toConversationItem(response))
    }

    private suspend fun sendMessageToServer(request: MessageRequest): MessageResponse {
        val response: MessageResponse = client.post("v1/messages") {
            setBody(request)
        }.body()
        return response
    }
}