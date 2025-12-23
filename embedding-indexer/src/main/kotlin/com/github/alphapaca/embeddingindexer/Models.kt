package com.github.alphapaca.embeddingindexer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TextChunk(
    val id: Long = 0,
    val content: String,
    val startOffset: Int,
    val endOffset: Int,
)

@Serializable
data class EmbeddingRequest(
    val input: List<String>,
    val model: String = "voyage-3.5-lite",
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
