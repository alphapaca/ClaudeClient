package com.github.alphapaca.claudeclient.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageRequest(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val messages: List<Message>,
    val system: String? = null,
    val temperature: Double? = null
)

@Serializable
data class Message(
    val role: String,
    val content: String
) {
    companion object {
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"
    }
}