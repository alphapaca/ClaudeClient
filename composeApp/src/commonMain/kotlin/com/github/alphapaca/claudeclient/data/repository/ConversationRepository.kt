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

    suspend fun compactConversation(
        model: LLMModel,
        systemPrompt: String,
        temperature: Double?,
        maxTokens: Int,
        keepRecentCount: Int = 2,
    ) {
        val items = conversation.value.items
        if (items.size <= keepRecentCount + 1) return // Nothing to compact

        val toCompact = items.dropLast(keepRecentCount)
        val toKeep = items.takeLast(keepRecentCount)

        val service = llmServices.find { it.isServiceFor(model) }
            ?: error("No LLM service found for model: ${model.displayName}")

        val summaryPrompt = buildString {
            appendLine("Summarize the following conversation concisely, preserving all key facts, names, numbers, preferences, and decisions made. The summary will be used as context for continuing the conversation.")
            appendLine()
            toCompact.forEach { item ->
                when (item) {
                    is ConversationItem.Text.User -> appendLine("User: ${item.content}")
                    is ConversationItem.Text.Assistant -> appendLine("Assistant: ${item.content}")
                    is ConversationItem.Summary -> appendLine("Previous summary: ${item.content}")
                    is ConversationItem.Widget -> appendLine("Assistant: [widget data]")
                    is ConversationItem.Composed -> appendLine("Assistant: [composed response]")
                }
            }
        }

        val response = service.sendMessage(
            messages = listOf(ConversationItem.Text.User(summaryPrompt)),
            model = model,
            systemPrompt = "You are a helpful assistant that creates concise conversation summaries.",
            temperature = temperature,
            maxTokens = maxTokens,
        )

        val summary = ConversationItem.Summary(
            content = response.content,
            compactedMessageCount = toCompact.size,
        )

        conversation.value = conversation.value.copy(
            items = listOf(summary) + toKeep
        )
    }
}