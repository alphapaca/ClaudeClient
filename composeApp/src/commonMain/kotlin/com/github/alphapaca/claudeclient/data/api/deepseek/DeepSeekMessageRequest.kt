package com.github.alphapaca.claudeclient.data.api.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class DeepSeekMessageRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val tools: List<DeepSeekTool>? = null,
)

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String
)

@Serializable
data class DeepSeekTool(
    val type: String = "function",
    val function: DeepSeekFunction,
)

@Serializable
data class DeepSeekFunction(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject? = null,
)
