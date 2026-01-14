@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")

package com.github.alphapaca.reviewagent.model

import kotlinx.serialization.Serializable

enum class Severity {
    ISSUE,
    SUGGESTION,
    NOTE
}

data class ReviewComment(
    val filePath: String,
    val line: Int,
    val severity: Severity,
    val body: String
)

data class ReviewResult(
    val comments: List<ReviewComment>
)

@Serializable
data class ClaudeReviewOutput(
    val comments: List<ClaudeReviewComment>
)

@Serializable
data class ClaudeReviewComment(
    val line: Int,
    val severity: String,
    val body: String
)
