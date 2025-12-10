package com.github.alphapaca.claudeclient.data.api.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClaudeMessageResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeContentBlock>,
    val model: String,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    val usage: ClaudeUsage
)

@Serializable
data class ClaudeContentBlock(
    val type: String,
    val text: String
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int
)
