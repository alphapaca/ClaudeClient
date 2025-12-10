package com.github.alphapaca.claudeclient.data.api.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeepSeekMessageRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val temperature: Double? = null,
)

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String
)
