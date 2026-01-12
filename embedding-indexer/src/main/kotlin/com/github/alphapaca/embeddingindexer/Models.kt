package com.github.alphapaca.embeddingindexer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TextChunk(
    val id: Long = 0,
    val content: String,
    val startOffset: Int,
    val endOffset: Int,
    val paragraphNumber: Int = 0,
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

// Reranking models
@Serializable
data class RerankRequest(
    val query: String,
    val documents: List<String>,
    val model: String = "rerank-2",
    @SerialName("top_k")
    val topK: Int? = null,
)

@Serializable
data class RerankResponse(
    // Try both 'results' and 'data' field names
    val results: List<RerankResult>? = null,
    val data: List<RerankResult>? = null,
    // Token count could be at top level or in usage object
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
    val usage: RerankUsage? = null,
) {
    fun resultsList(): List<RerankResult> = results ?: data ?: emptyList()
    fun tokenCount(): Int = totalTokens ?: usage?.totalTokens ?: 0
}

@Serializable
data class RerankUsage(
    @SerialName("total_tokens")
    val totalTokens: Int,
)

@Serializable
data class RerankResult(
    val index: Int,
    @SerialName("relevance_score")
    val relevanceScore: Float,
)
