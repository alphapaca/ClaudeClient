@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")

package com.github.alphapaca.reviewagent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateReviewRequest(
    @SerialName("commit_id") val commitId: String,
    val event: String,
    val body: String,
    val comments: List<ReviewCommentRequest>
)

@Serializable
data class ReviewCommentRequest(
    val path: String,
    val line: Int,
    val side: String,
    val body: String
)

@Serializable
data class PRInfo(
    val number: Int,
    val head: PRHead,
    val base: PRBase
)

@Serializable
data class PRHead(
    val sha: String,
    val ref: String
)

@Serializable
data class PRBase(
    val sha: String,
    val ref: String
)
