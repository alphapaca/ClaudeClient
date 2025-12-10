package com.github.alphapaca.claudeclient.data.repository

import com.github.alphapaca.claudeclient.data.service.LLMService
import com.github.alphapaca.claudeclient.domain.model.Conversation
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.measureTimedValue

class ConversationRepository(
    private val llmServices: List<LLMService>,
) {
    private val conversation: MutableStateFlow<Conversation> = MutableStateFlow(Conversation(emptyList()))

    fun getConversation(): Flow<Conversation> = conversation.asSharedFlow()

    suspend fun sendMessage(message: String, model: LLMModel, systemPrompt: String, temperature: Double?, maxTokens: Int) {
        conversation.value = conversation.value.copy(
            items = conversation.value.items + ConversationItem.Text.User(message)
        )

        val service = llmServices.find { it.isServiceFor(model) }
            ?: error("No LLM service found for model: ${model.displayName}")

        val (response, duration) = measureTimedValue {
            service.sendMessage(
                messages = conversation.value.items,
                model = model,
                systemPrompt = systemPrompt.takeIf { it.isNotBlank() },
                temperature = temperature,
                maxTokens = maxTokens,
            )
        }

        conversation.value = conversation.value.copy(
            items = conversation.value.items + ConversationItem.Text.Assistant(
                content = response.content,
                model = model,
                inputTokens = response.inputTokens,
                outputTokens = response.outputTokens,
                inferenceTimeMs = duration.inWholeMilliseconds,
                stopReason = response.stopReason,
            ),
        )
    }

    fun clearConversation() {
        conversation.value = Conversation(emptyList())
    }
}