package com.github.alphapaca.claudeclient.domain.model

/**
 * Represents a code session - a chat context linked to an indexed git repository.
 */
data class CodeSession(
    val id: String,
    val name: String,
    val repoPath: String,
    val dbPath: String,
    val indexedFileCount: Int,
    val indexedChunkCount: Int,
    val indexingStatus: IndexingStatus,
    val errorMessage: String?,
    val messages: List<CodeSessionMessage>,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Lightweight metadata for displaying code sessions in lists.
 */
data class CodeSessionInfo(
    val id: String,
    val name: String,
    val repoPath: String,
    val indexingStatus: IndexingStatus,
    val updatedAt: Long,
)

/**
 * Status of the code indexing process.
 */
enum class IndexingStatus {
    PENDING,
    INDEXING,
    COMPLETED,
    FAILED;

    companion object {
        fun fromString(value: String): IndexingStatus {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: PENDING
        }
    }
}

/**
 * A message within a code session.
 */
sealed interface CodeSessionMessage {
    data class User(val content: String) : CodeSessionMessage

    data class Assistant(
        val content: String,
        val model: LLMModel,
        val inputTokens: Int,
        val outputTokens: Int,
        val inferenceTimeMs: Long,
        val stopReason: StopReason,
    ) : CodeSessionMessage
}

/**
 * Progress information during code indexing.
 */
data class IndexingProgress(
    val phase: IndexingPhase,
    val current: Int,
    val total: Int,
    val message: String = "",
)

enum class IndexingPhase {
    SCANNING,
    CHUNKING,
    EMBEDDING,
    STORING,
}
