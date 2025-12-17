package com.github.alphapaca.claudeclient.data.api.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
    val text: String? = null,
    // tool_use fields
    val id: String? = null,
    val name: String? = null,
    val input: JsonObject? = null,
    // tool_result fields
    @SerialName("tool_use_id")
    val toolUseId: String? = null,
    val content: String? = null,
    @SerialName("is_error")
    val isError: Boolean? = null,
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int
)
