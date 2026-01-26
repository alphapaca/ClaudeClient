package com.github.alphapaca.claudeclient.domain.model

/**
 * Represents a system analysis session - a chat context for analyzing system data.
 * Uses local LLM (Ollama) for processing.
 */
data class SystemAnalysisSession(
    val id: String,
    val name: String,
    val projectName: String,
    val messages: List<SystemAnalysisMessage>,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Lightweight metadata for displaying system analysis sessions in lists.
 */
data class SystemAnalysisSessionInfo(
    val id: String,
    val name: String,
    val projectName: String,
    val updatedAt: Long,
)

/**
 * A message within a system analysis session.
 */
sealed interface SystemAnalysisMessage {
    data class User(val content: String) : SystemAnalysisMessage

    data class Assistant(
        val content: String,
        val model: LLMModel,
        val inputTokens: Int,
        val outputTokens: Int,
        val inferenceTimeMs: Long,
        val stopReason: StopReason,
    ) : SystemAnalysisMessage

    data class ToolResult(
        val toolName: String,
        val result: String,
    ) : SystemAnalysisMessage
}
