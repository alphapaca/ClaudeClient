package com.github.alphapaca.claudeclient.data.api.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeepSeekMessageResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<DeepSeekChoice>,
    val usage: DeepSeekUsage,
)

@Serializable
data class DeepSeekChoice(
    val index: Int,
    val message: DeepSeekResponseMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class DeepSeekResponseMessage(
    val role: String,
    val content: String,
)

@Serializable
data class DeepSeekUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int,
)
