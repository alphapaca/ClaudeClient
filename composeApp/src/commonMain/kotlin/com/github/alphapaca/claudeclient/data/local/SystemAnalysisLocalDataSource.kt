package com.github.alphapaca.claudeclient.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.github.alphapaca.claudeclient.data.db.ClaudeClientDatabase
import com.github.alphapaca.claudeclient.data.db.SystemAnalysisMessage as DbSystemAnalysisMessage
import com.github.alphapaca.claudeclient.data.db.SystemAnalysisSession as DbSystemAnalysisSession
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.model.StopReason
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisMessage
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisSession
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisSessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SystemAnalysisLocalDataSource(
    private val database: ClaudeClientDatabase
) {
    private val queries = database.systemAnalysisSessionQueries

    fun getAllSessionsFlow(): Flow<List<SystemAnalysisSessionInfo>> {
        return queries.selectAllSessions()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toSessionInfo() } }
    }

    fun getSessionFlow(sessionId: String): Flow<SystemAnalysisSession?> {
        val sessionFlow = queries.selectSessionById(sessionId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

        val messagesFlow = queries.selectMessagesBySessionId(sessionId)
            .asFlow()
            .mapToList(Dispatchers.IO)

        return combine(sessionFlow, messagesFlow) { session, messages ->
            session?.toSession(messages.map { it.toMessage() })
        }
    }

    suspend fun createSession(name: String, projectName: String): String = withContext(Dispatchers.IO) {
        val id = Uuid.random().toString()
        val now = System.currentTimeMillis()

        queries.insertSession(
            id = id,
            name = name,
            project_name = projectName,
            created_at = now,
            updated_at = now,
        )

        id
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        queries.deleteMessagesBySessionId(sessionId)
        queries.deleteSessionById(sessionId)
    }

    suspend fun saveMessage(sessionId: String, message: SystemAnalysisMessage) = withContext(Dispatchers.IO) {
        val id = Uuid.random().toString()
        val now = System.currentTimeMillis()

        when (message) {
            is SystemAnalysisMessage.User -> {
                queries.insertMessage(
                    id = id,
                    session_id = sessionId,
                    role = ROLE_USER,
                    content = message.content,
                    model = null,
                    input_tokens = null,
                    output_tokens = null,
                    inference_time_ms = null,
                    stop_reason = null,
                    tool_name = null,
                    created_at = now,
                )
            }
            is SystemAnalysisMessage.Assistant -> {
                queries.insertMessage(
                    id = id,
                    session_id = sessionId,
                    role = ROLE_ASSISTANT,
                    content = message.content,
                    model = message.model.apiName,
                    input_tokens = message.inputTokens.toLong(),
                    output_tokens = message.outputTokens.toLong(),
                    inference_time_ms = message.inferenceTimeMs,
                    stop_reason = message.stopReason.name,
                    tool_name = null,
                    created_at = now,
                )
            }
            is SystemAnalysisMessage.ToolResult -> {
                queries.insertMessage(
                    id = id,
                    session_id = sessionId,
                    role = ROLE_TOOL,
                    content = message.result,
                    model = null,
                    input_tokens = null,
                    output_tokens = null,
                    inference_time_ms = null,
                    stop_reason = null,
                    tool_name = message.toolName,
                    created_at = now,
                )
            }
        }

        // Update session's updated_at timestamp
        queries.updateSessionTimestamp(
            updated_at = now,
            id = sessionId,
        )
    }

    private fun SelectAllSessions.toSessionInfo(): SystemAnalysisSessionInfo {
        return SystemAnalysisSessionInfo(
            id = id,
            name = name,
            projectName = project_name,
            updatedAt = updated_at,
        )
    }

    private fun DbSystemAnalysisSession.toSession(messages: List<SystemAnalysisMessage>): SystemAnalysisSession {
        return SystemAnalysisSession(
            id = id,
            name = name,
            projectName = project_name,
            messages = messages,
            createdAt = created_at,
            updatedAt = updated_at,
        )
    }

    private fun DbSystemAnalysisMessage.toMessage(): SystemAnalysisMessage {
        return when (role) {
            ROLE_USER -> SystemAnalysisMessage.User(content)
            ROLE_ASSISTANT -> SystemAnalysisMessage.Assistant(
                content = content,
                model = LLMModel.fromApiName(model ?: LLMModel.LLAMA_3_2.apiName),
                inputTokens = input_tokens?.toInt() ?: 0,
                outputTokens = output_tokens?.toInt() ?: 0,
                inferenceTimeMs = inference_time_ms ?: 0,
                stopReason = stop_reason?.let { StopReason.valueOf(it) } ?: StopReason.UNKNOWN,
            )
            ROLE_TOOL -> SystemAnalysisMessage.ToolResult(
                toolName = tool_name ?: "unknown",
                result = content,
            )
            else -> SystemAnalysisMessage.User(content) // Fallback
        }
    }

    companion object {
        private const val ROLE_USER = "user"
        private const val ROLE_ASSISTANT = "assistant"
        private const val ROLE_TOOL = "tool"
    }
}

// Type alias for the generated query result
private typealias SelectAllSessions = com.github.alphapaca.claudeclient.data.db.SelectAllSessions
