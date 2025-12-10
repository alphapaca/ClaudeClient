package com.github.alphapaca.claudeclient.data.service

import com.github.alphapaca.claudeclient.data.api.deepseek.DeepSeekMessage
import com.github.alphapaca.claudeclient.data.api.deepseek.DeepSeekMessageRequest
import com.github.alphapaca.claudeclient.data.api.deepseek.DeepSeekMessageResponse
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.json.Json

class DeepSeekService(
    private val client: HttpClient,
    private val json: Json,
) : LLMService {

    override fun isServiceFor(model: LLMModel): Boolean {
        return model == LLMModel.DEEPSEEK_V3_2
    }

    override suspend fun sendMessage(
        messages: List<ConversationItem>,
        model: LLMModel,
        systemPrompt: String?,
        temperature: Double?,
    ): LLMResponse {
        val systemMessages = systemPrompt
            ?.takeIf { it.isNotBlank() }
            ?.let { listOf(DeepSeekMessage(role = ROLE_SYSTEM, content = it)) }
            .orEmpty()

        val request = DeepSeekMessageRequest(
            model = model.apiName,
            messages = systemMessages + messages.map { it.toDeepSeekMessage() },
            maxTokens = 1024,
            // DeepSeek API temperature range is [0, 2], default 1.0
            // We normalize from [0, 1] to [0, 2] to match Claude's behavior
            // where 0 is least variability and 1 is most
            temperature = temperature?.let { it * 2.0 },
        )

        val response: DeepSeekMessageResponse = client.post("chat/completions") {
            setBody(request)
        }.body()

        val choice = response.choices.firstOrNull()
        return LLMResponse(
            content = choice?.message?.content.orEmpty(),
            inputTokens = response.usage.promptTokens,
            outputTokens = response.usage.completionTokens,
        )
    }

    private fun ConversationItem.toDeepSeekMessage(): DeepSeekMessage {
        return when (this) {
            is ConversationItem.Text.User -> DeepSeekMessage(
                role = ROLE_USER,
                content = content
            )
            is ConversationItem.Text.Assistant -> DeepSeekMessage(
                role = ROLE_ASSISTANT,
                content = content
            )
            is ConversationItem.Widget -> DeepSeekMessage(
                role = ROLE_ASSISTANT,
                content = json.encodeToString(ConversationItem.Widget.serializer(), this)
            )
            is ConversationItem.Composed -> DeepSeekMessage(
                role = ROLE_ASSISTANT,
                content = parts.joinToString("") { it.toDeepSeekMessage().content }
            )
        }
    }

    private companion object {
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"
        const val ROLE_SYSTEM = "system"
    }
}
