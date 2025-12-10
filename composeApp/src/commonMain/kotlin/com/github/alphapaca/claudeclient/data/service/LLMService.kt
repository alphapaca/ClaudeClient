package com.github.alphapaca.claudeclient.data.service

import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.domain.model.LLMModel

data class LLMResponse(
    val content: String,
    val inputTokens: Int,
    val outputTokens: Int,
)

interface LLMService {
    fun isServiceFor(model: LLMModel): Boolean

    suspend fun sendMessage(
        messages: List<ConversationItem>,
        model: LLMModel,
        systemPrompt: String?,
        temperature: Double?,
    ): LLMResponse
}
