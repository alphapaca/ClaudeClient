package com.github.alphapaca.claudeclient.data.indexer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class CodeChunkType {
    FILE,
    CLASS,
    INTERFACE,
    OBJECT,
    FUNCTION,
    PROPERTY,
    COMPANION,
}

@Serializable
data class CodeChunk(
    val id: Long = 0,
    val content: String,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val chunkType: CodeChunkType,
    val name: String,
    val parentName: String? = null,
    val signature: String? = null,
)

data class CodeSearchResult(
    val chunkId: Long,
    val content: String,
    val distance: Float,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val chunkType: CodeChunkType,
    val name: String,
    val parentName: String?,
    val signature: String?,
)

@Serializable
data class EmbeddingRequest(
    val input: List<String>,
    val model: String = "voyage-code-3",
)

@Serializable
data class EmbeddingResponse(
    val data: List<EmbeddingData>,
    val model: String,
    val usage: EmbeddingUsage,
)

@Serializable
data class EmbeddingData(
    val embedding: List<Float>,
    val index: Int,
)

@Serializable
data class EmbeddingUsage(
    @SerialName("total_tokens")
    val totalTokens: Int,
)
