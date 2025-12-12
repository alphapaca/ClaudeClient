package com.github.alphapaca.claudeclient.data.service

import com.github.alphapaca.claudeclient.data.api.claude.ClaudeMessage
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeMessageRequest
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeMessageResponse
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.model.StopReason
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.json.Json

class ClaudeService(
    private val client: HttpClient,
    private val json: Json,
) : LLMService {

    override fun isServiceFor(model: LLMModel): Boolean {
        return model == LLMModel.CLAUDE_SONNET_4_5 || model == LLMModel.CLAUDE_OPUS_4_5
    }

    override suspend fun sendMessage(
        messages: List<ConversationItem>,
        model: LLMModel,
        systemPrompt: String?,
        temperature: Double?,
        maxTokens: Int,
    ): LLMResponse {
        val request = ClaudeMessageRequest(
            model = model.apiName,
            maxTokens = maxTokens,
            messages = messages.map { it.toClaudeMessage() },
            system = systemPrompt?.takeIf { it.isNotBlank() },
            temperature = temperature,
        )

        val response: ClaudeMessageResponse = client.post("v1/messages") {
            setBody(request)
        }.body()

        return LLMResponse(
            content = response.content.firstOrNull()?.text.orEmpty(),
            inputTokens = response.usage.inputTokens,
            outputTokens = response.usage.outputTokens,
            stopReason = StopReason.fromClaudeReason(response.stopReason),
        )
    }

    private fun ConversationItem.toClaudeMessage(): ClaudeMessage {
        return when (this) {
            is ConversationItem.Text.User -> ClaudeMessage(
                role = ROLE_USER,
                content = content
            )
            is ConversationItem.Text.Assistant -> ClaudeMessage(
                role = ROLE_ASSISTANT,
                content = content
            )
            is ConversationItem.Summary -> ClaudeMessage(
                role = ROLE_USER,
                content = "[Previous conversation summary: $content]"
            )
            is ConversationItem.Widget -> ClaudeMessage(
                role = ROLE_ASSISTANT,
                content = json.encodeToString(ConversationItem.Widget.serializer(), this)
            )
            is ConversationItem.Composed -> ClaudeMessage(
                role = ROLE_ASSISTANT,
                content = parts.joinToString("") { it.toClaudeMessage().content }
            )
        }
    }

    private companion object {
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"
    }
}
