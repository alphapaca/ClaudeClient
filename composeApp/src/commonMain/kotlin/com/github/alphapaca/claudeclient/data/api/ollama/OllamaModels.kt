package com.github.alphapaca.claudeclient.data.api.ollama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null,
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String,
)

@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null,
)

@Serializable
data class OllamaChatResponse(
    val model: String,
    val message: OllamaMessage,
    @SerialName("done_reason")
    val doneReason: String? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
)
