package com.github.alphapaca.claudeclient.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.github.alphapaca.claudeclient.data.db.ClaudeClientDatabase
import com.github.alphapaca.claudeclient.data.db.Message
import com.github.alphapaca.claudeclient.data.parser.ContentBlockParser
import com.github.alphapaca.claudeclient.domain.model.Conversation
import com.github.alphapaca.claudeclient.domain.model.ConversationInfo
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.model.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.github.alphapaca.claudeclient.data.db.Conversation as DbConversation

@OptIn(ExperimentalUuidApi::class)
class ConversationLocalDataSource(
    private val database: ClaudeClientDatabase,
    private val contentBlockParser: ContentBlockParser,
) {
    private val queries = database.conversationQueries

    fun getAllConversationsFlow(): Flow<List<ConversationInfo>> {
        return queries.getAllConversations()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toConversationInfo() } }
    }

    fun getConversationFlow(conversationId: String): Flow<Conversation> {
        return queries.getMessagesByConversationId(conversationId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { messages ->
                Conversation(messages.map { it.toConversationItem() })
            }
    }

    suspend fun createConversation(name: String): String = withContext(Dispatchers.IO) {
        val id = Uuid.random().toString()
        val now = System.currentTimeMillis()
        queries.insertConversation(id, name, now, now)
        id
    }

    suspend fun getMostRecentConversationId(): String? = withContext(Dispatchers.IO) {
        queries.getMostRecentConversation().executeAsOneOrNull()?.id
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        queries.deleteMessagesByConversationId(conversationId)
        queries.deleteConversation(conversationId)
    }

    suspend fun saveMessage(conversationId: String, position: Int, item: ConversationItem) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        when (item) {
            is ConversationItem.User -> {
                queries.insertMessage(
                    conversationId = conversationId,
                    position = position.toLong(),
                    type = MESSAGE_TYPE_USER,
                    content = item.content,
                    modelApiName = null,
                    inputTokens = null,
                    outputTokens = null,
                    inferenceTimeMs = null,
                    stopReason = null,
                    compactedMessageCount = null,
                    createdAt = now,
                )
            }

            is ConversationItem.Assistant -> {
                queries.insertMessage(
                    conversationId = conversationId,
                    position = position.toLong(),
                    type = MESSAGE_TYPE_ASSISTANT,
                    content = item.rawContent,
                    modelApiName = item.model.apiName,
                    inputTokens = item.inputTokens.toLong(),
                    outputTokens = item.outputTokens.toLong(),
                    inferenceTimeMs = item.inferenceTimeMs,
                    stopReason = item.stopReason.name,
                    compactedMessageCount = null,
                    createdAt = now,
                )
            }

            is ConversationItem.Summary -> {
                queries.insertMessage(
                    conversationId = conversationId,
                    position = position.toLong(),
                    type = MESSAGE_TYPE_SUMMARY,
                    content = item.content,
                    modelApiName = null,
                    inputTokens = null,
                    outputTokens = null,
                    inferenceTimeMs = null,
                    stopReason = null,
                    compactedMessageCount = item.compactedMessageCount.toLong(),
                    createdAt = now,
                )
            }
        }

        queries.updateConversationTimestamp(now, conversationId)
    }

    suspend fun clearConversation(conversationId: String) = withContext(Dispatchers.IO) {
        queries.deleteMessagesByConversationId(conversationId)
        val now = System.currentTimeMillis()
        queries.updateConversationTimestamp(now, conversationId)
    }

    suspend fun replaceConversationMessages(conversationId: String, items: List<ConversationItem>) {
        withContext(Dispatchers.IO) {
            queries.deleteMessagesByConversationId(conversationId)
        }
        items.forEachIndexed { index, item ->
            saveMessage(conversationId, index, item)
        }
    }

    private fun Message.toConversationItem(): ConversationItem {
        return when (type) {
            MESSAGE_TYPE_USER -> ConversationItem.User(content)

            MESSAGE_TYPE_ASSISTANT -> ConversationItem.Assistant(
                content = contentBlockParser.parse(content),
                model = LLMModel.fromApiName(modelApiName ?: ""),
                inputTokens = inputTokens?.toInt() ?: 0,
                outputTokens = outputTokens?.toInt() ?: 0,
                inferenceTimeMs = inferenceTimeMs ?: 0L,
                stopReason = stopReason?.let { StopReason.valueOf(it) } ?: StopReason.UNKNOWN,
            )

            MESSAGE_TYPE_SUMMARY -> ConversationItem.Summary(
                content = content,
                compactedMessageCount = compactedMessageCount?.toInt() ?: 0,
            )

            else -> ConversationItem.User(content) // Fallback
        }
    }

    suspend fun incrementUnreadCount(conversationId: String) = withContext(Dispatchers.IO) {
        queries.incrementUnreadCount(conversationId)
    }

    suspend fun resetUnreadCount(conversationId: String) = withContext(Dispatchers.IO) {
        queries.resetUnreadCount(conversationId)
    }

    private fun DbConversation.toConversationInfo(): ConversationInfo {
        return ConversationInfo(
            id = id,
            name = name,
            updatedAt = updatedAt,
            unreadCount = unreadCount.toInt(),
        )
    }

    companion object {
        private const val MESSAGE_TYPE_USER = "user"
        private const val MESSAGE_TYPE_ASSISTANT = "assistant"
        private const val MESSAGE_TYPE_SUMMARY = "summary"
    }
}
