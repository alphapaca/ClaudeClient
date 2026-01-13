package com.github.alphapaca.claudeclient.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.github.alphapaca.claudeclient.data.db.ClaudeClientDatabase
import com.github.alphapaca.claudeclient.data.db.CodeSessionMessage as DbCodeSessionMessage
import com.github.alphapaca.claudeclient.domain.model.CodeSession
import com.github.alphapaca.claudeclient.domain.model.CodeSessionInfo
import com.github.alphapaca.claudeclient.domain.model.CodeSessionMessage
import com.github.alphapaca.claudeclient.domain.model.IndexingStatus
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.model.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.github.alphapaca.claudeclient.data.db.CodeSession as DbCodeSession

@OptIn(ExperimentalUuidApi::class)
class CodeSessionLocalDataSource(
    private val database: ClaudeClientDatabase,
) {
    private val queries = database.codeSessionQueries

    fun getAllCodeSessionsFlow(): Flow<List<CodeSessionInfo>> {
        return queries.getAllCodeSessions()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toCodeSessionInfo() } }
    }

    fun getCodeSessionFlow(sessionId: String): Flow<CodeSession?> {
        val sessionFlow = queries.getCodeSessionById(sessionId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

        val messagesFlow = queries.getMessagesBySessionId(sessionId)
            .asFlow()
            .mapToList(Dispatchers.IO)

        return combine(sessionFlow, messagesFlow) { session, messages ->
            session?.toCodeSession(messages.map { it.toCodeSessionMessage() })
        }
    }

    suspend fun createCodeSession(
        name: String,
        repoPath: String,
        dbPath: String,
    ): String = withContext(Dispatchers.IO) {
        val id = Uuid.random().toString()
        val now = System.currentTimeMillis()
        queries.insertCodeSession(
            id = id,
            name = name,
            repoPath = repoPath,
            dbPath = dbPath,
            indexedFileCount = 0,
            indexedChunkCount = 0,
            indexingStatus = IndexingStatus.PENDING.name.lowercase(),
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
        )
        id
    }

    suspend fun updateIndexingStatus(
        sessionId: String,
        status: IndexingStatus,
        fileCount: Int = 0,
        chunkCount: Int = 0,
        errorMessage: String? = null,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        queries.updateIndexingStatus(
            indexingStatus = status.name.lowercase(),
            indexedFileCount = fileCount.toLong(),
            indexedChunkCount = chunkCount.toLong(),
            errorMessage = errorMessage,
            updatedAt = now,
            id = sessionId,
        )
    }

    suspend fun deleteCodeSession(sessionId: String) = withContext(Dispatchers.IO) {
        queries.deleteMessagesBySessionId(sessionId)
        queries.deleteCodeSession(sessionId)
    }

    suspend fun saveMessage(
        sessionId: String,
        message: CodeSessionMessage,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val position = (queries.getMaxMessagePosition(sessionId).executeAsOneOrNull()?.MAX ?: -1) + 1

        when (message) {
            is CodeSessionMessage.User -> {
                queries.insertCodeSessionMessage(
                    sessionId = sessionId,
                    position = position,
                    type = MESSAGE_TYPE_USER,
                    content = message.content,
                    modelApiName = null,
                    inputTokens = null,
                    outputTokens = null,
                    inferenceTimeMs = null,
                    stopReason = null,
                    createdAt = now,
                )
            }

            is CodeSessionMessage.Assistant -> {
                queries.insertCodeSessionMessage(
                    sessionId = sessionId,
                    position = position,
                    type = MESSAGE_TYPE_ASSISTANT,
                    content = message.content,
                    modelApiName = message.model.apiName,
                    inputTokens = message.inputTokens.toLong(),
                    outputTokens = message.outputTokens.toLong(),
                    inferenceTimeMs = message.inferenceTimeMs,
                    stopReason = message.stopReason.name,
                    createdAt = now,
                )
            }
        }

        queries.updateCodeSessionUpdatedAt(now, sessionId)
    }

    suspend fun getCodeSessionDbPath(sessionId: String): String? = withContext(Dispatchers.IO) {
        queries.getCodeSessionById(sessionId).executeAsOneOrNull()?.dbPath
    }

    suspend fun getCodeSessionRepoPath(sessionId: String): String? = withContext(Dispatchers.IO) {
        queries.getCodeSessionById(sessionId).executeAsOneOrNull()?.repoPath
    }

    private fun GetAllCodeSessions.toCodeSessionInfo(): CodeSessionInfo {
        return CodeSessionInfo(
            id = id,
            name = name,
            repoPath = repoPath,
            indexingStatus = IndexingStatus.fromString(indexingStatus),
            updatedAt = updatedAt,
        )
    }

    private fun DbCodeSession.toCodeSession(messages: List<CodeSessionMessage>): CodeSession {
        return CodeSession(
            id = id,
            name = name,
            repoPath = repoPath,
            dbPath = dbPath,
            indexedFileCount = indexedFileCount.toInt(),
            indexedChunkCount = indexedChunkCount.toInt(),
            indexingStatus = IndexingStatus.fromString(indexingStatus),
            errorMessage = errorMessage,
            messages = messages,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun DbCodeSessionMessage.toCodeSessionMessage(): CodeSessionMessage {
        return when (type) {
            MESSAGE_TYPE_USER -> CodeSessionMessage.User(content)

            MESSAGE_TYPE_ASSISTANT -> CodeSessionMessage.Assistant(
                content = content,
                model = LLMModel.fromApiName(modelApiName ?: ""),
                inputTokens = inputTokens?.toInt() ?: 0,
                outputTokens = outputTokens?.toInt() ?: 0,
                inferenceTimeMs = inferenceTimeMs ?: 0L,
                stopReason = stopReason?.let { StopReason.valueOf(it) } ?: StopReason.UNKNOWN,
            )

            else -> CodeSessionMessage.User(content) // Fallback
        }
    }

    companion object {
        private const val MESSAGE_TYPE_USER = "user"
        private const val MESSAGE_TYPE_ASSISTANT = "assistant"
    }
}

// Type alias for the generated query result
private typealias GetAllCodeSessions = com.github.alphapaca.claudeclient.data.db.GetAllCodeSessions
