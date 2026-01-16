package com.github.alphapaca.webagent.data.api

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
data class SearchResponse(
    val results: List<CodeSearchResult>,
    val totalCount: Int,
)

@Serializable
data class CodeSearchResult(
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
