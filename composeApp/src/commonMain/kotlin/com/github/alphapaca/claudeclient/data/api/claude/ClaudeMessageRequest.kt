package com.github.alphapaca.claudeclient.data.api.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClaudeMessageRequest(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val messages: List<ClaudeMessage>,
    val system: String? = null,
    val temperature: Double? = null
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String
)
