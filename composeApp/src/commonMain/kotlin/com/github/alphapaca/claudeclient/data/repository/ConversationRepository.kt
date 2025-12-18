package com.github.alphapaca.claudeclient.data.repository

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.local.ConversationLocalDataSource
import com.github.alphapaca.claudeclient.data.mcp.MCPTool
import com.github.alphapaca.claudeclient.data.parser.ContentBlockParser
import com.github.alphapaca.claudeclient.data.service.LLMService
import com.github.alphapaca.claudeclient.domain.model.Conversation
import com.github.alphapaca.claudeclient.domain.model.ConversationInfo
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.measureTimedValue

class ConversationRepository(
    private val llmServices: List<LLMService>,
    private val contentBlockParser: ContentBlockParser,
    private val localDataSource: ConversationLocalDataSource,
) {
    fun getAllConversations(): Flow<List<ConversationInfo>> {
        return localDataSource.getAllConversationsFlow()
    }

    fun getConversation(conversationId: String): Flow<Conversation> {
        return localDataSource.getConversationFlow(conversationId)
    }

    suspend fun getMostRecentConversationId(): String? {
        return localDataSource.getMostRecentConversationId()
    }

    suspend fun deleteConversation(conversationId: String) {
        localDataSource.deleteConversation(conversationId)
    }

    /**
     * Sends a message to the conversation.
     * If conversationId is null, creates a new conversation.
     * @return the actual conversationId used (may be newly created)
     */
    suspend fun sendMessage(
        conversationId: String?,
        message: String,
        model: LLMModel,
        systemPrompt: String,
        temperature: Double?,
        maxTokens: Int,
        tools: List<MCPTool> = emptyList(),
    ): String {
        val actualConversationId = if (conversationId == null) {
            val name = generateConversationName(message)
            localDataSource.createConversation(name)
        } else {
            conversationId
        }

        val currentMessages = localDataSource.getConversationFlow(actualConversationId).first()
        val userMessage = ConversationItem.User(message)
        localDataSource.saveMessage(actualConversationId, currentMessages.items.size, userMessage)

        val service = llmServices.find { it.isServiceFor(model) }
            ?: error("No LLM service found for model: ${model.displayName}")

        val messagesForApi = currentMessages.items + userMessage

        val (response, duration) = measureTimedValue {
            service.sendMessage(
                messages = messagesForApi,
                model = model,
                systemPrompt = systemPrompt.takeIf { it.isNotBlank() },
                temperature = temperature,
                maxTokens = maxTokens,
                tools = tools,
            )
        }

        Logger.d(TAG) { "LLM response content: '${response.content}'" }
        Logger.d(TAG) { "LLM response tokens: in=${response.inputTokens}, out=${response.outputTokens}" }

        val assistantMessage = ConversationItem.Assistant(
            content = contentBlockParser.parse(response.content),
            model = model,
            inputTokens = response.inputTokens,
            outputTokens = response.outputTokens,
            inferenceTimeMs = duration.inWholeMilliseconds,
            stopReason = response.stopReason,
        )

        Logger.d(TAG) { "Saving assistant message with ${assistantMessage.content.size} content blocks" }
        localDataSource.saveMessage(actualConversationId, messagesForApi.size, assistantMessage)
        Logger.d(TAG) { "Message saved to conversation $actualConversationId" }

        // Always increment unread count - UI will reset if conversation is being viewed
        localDataSource.incrementUnreadCount(actualConversationId)

        return actualConversationId
    }

    suspend fun clearConversation(conversationId: String) {
        localDataSource.clearConversation(conversationId)
    }

    suspend fun resetUnreadCount(conversationId: String) {
        localDataSource.resetUnreadCount(conversationId)
    }

    suspend fun compactConversation(
        conversationId: String,
        model: LLMModel,
        systemPrompt: String,
        temperature: Double?,
        maxTokens: Int,
        keepRecentCount: Int = 2,
    ) {
        val conversation = localDataSource.getConversationFlow(conversationId).first()
        val items = conversation.items
        if (items.size <= keepRecentCount + 1) return

        val toCompact = items.dropLast(keepRecentCount)
        val toKeep = items.takeLast(keepRecentCount)

        val service = llmServices.find { it.isServiceFor(model) }
            ?: error("No LLM service found for model: ${model.displayName}")

        val summaryPrompt = buildString {
            appendLine("Summarize the following conversation concisely, preserving all key facts, names, numbers, preferences, and decisions made. The summary will be used as context for continuing the conversation.")
            appendLine()
            toCompact.forEach { item ->
                when (item) {
                    is ConversationItem.User -> appendLine("User: ${item.content}")
                    is ConversationItem.Assistant -> appendLine("Assistant: ${item.textContent}")
                    is ConversationItem.Summary -> appendLine("Previous summary: ${item.content}")
                }
            }
        }

        val response = service.sendMessage(
            messages = listOf(ConversationItem.User(summaryPrompt)),
            model = model,
            systemPrompt = "You are a helpful assistant that creates concise conversation summaries.",
            temperature = temperature,
            maxTokens = maxTokens,
        )

        val summary = ConversationItem.Summary(
            content = response.content,
            compactedMessageCount = toCompact.size,
        )

        val newItems = listOf(summary) + toKeep
        localDataSource.replaceConversationMessages(conversationId, newItems)
    }

    private fun generateConversationName(userMessage: String): String {
        return userMessage.take(MAX_CONVERSATION_NAME_LENGTH).let {
            if (userMessage.length > MAX_CONVERSATION_NAME_LENGTH) "$it..." else it
        }
    }

    companion object {
        private const val TAG = "ConversationRepository"
        private const val MAX_CONVERSATION_NAME_LENGTH = 30
    }
}
