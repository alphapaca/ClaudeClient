package com.github.alphapaca.webagent.api.models

import kotlinx.serialization.Serializable

@Serializable
data class AskRequest(
    val question: String,
    val includeIssues: Boolean = true,
    val maxCodeResults: Int = 10,
)

@Serializable
data class AskResponse(
    val answer: String,
    val sources: List<SourceReference>,
    val processingTimeMs: Long,
)

@Serializable
data class SourceReference(
    val type: SourceType,
    val title: String,
    val location: String,
    val url: String? = null,
    val preview: String? = null,
)

@Serializable
enum class SourceType {
    CODE,
    DOCUMENTATION,
    GITHUB_ISSUE,
}

@Serializable
data class SearchRequest(
    val query: String,
    val limit: Int = 10,
)

@Serializable
data class SearchResponse(
    val results: List<CodeSearchResultDto>,
    val totalCount: Int,
)

@Serializable
data class CodeSearchResultDto(
    val filePath: String,
    val name: String,
    val chunkType: String,
    val startLine: Int,
    val endLine: Int,
    val content: String,
    val similarity: Float,
    val signature: String? = null,
)

@Serializable
data class HealthResponse(
    val status: String,
    val codeChunksIndexed: Int,
    val version: String = "1.0.0",
)

@Serializable
data class ErrorResponse(
    val error: String,
    val details: String? = null,
)
