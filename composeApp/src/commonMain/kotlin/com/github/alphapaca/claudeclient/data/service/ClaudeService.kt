package com.github.alphapaca.claudeclient.data.service

import co.touchlab.kermit.Logger
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
import io.ktor.client.statement.bodyAsText
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

        val response = client.post("v1/messages") {
            setBody(request)
        }

        val responseText = response.bodyAsText()

        Logger.v(TAG) { "Content: $responseText" }

        val responseDeserialized: ClaudeMessageResponse = response.body()

        return LLMResponse(
            content = responseDeserialized.content.firstOrNull()?.text.orEmpty(),
            inputTokens = responseDeserialized.usage.inputTokens,
            outputTokens = responseDeserialized.usage.outputTokens,
            stopReason = StopReason.fromClaudeReason(responseDeserialized.stopReason),
        )
    }

    private fun ConversationItem.toClaudeMessage(): ClaudeMessage {
        return when (this) {
            is ConversationItem.User -> ClaudeMessage(
                role = ROLE_USER,
                content = content
            )
            is ConversationItem.Assistant -> ClaudeMessage(
                role = ROLE_ASSISTANT,
                content = content.toMessageContent()
            )
            is ConversationItem.Summary -> ClaudeMessage(
                role = ROLE_USER,
                content = "[Previous conversation summary: $content]"
            )
        }
    }

    private fun List<ConversationItem.ContentBlock>.toMessageContent(): String {
        return joinToString("\n") { block ->
            when (block) {
                is ConversationItem.ContentBlock.Text -> block.text
                is ConversationItem.ContentBlock.Widget -> json.encodeToString(
                    ConversationItem.ContentBlock.Widget.serializer(),
                    block
                )
            }
        }
    }

    private companion object {
        const val TAG = "ClaudeService"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"
    }
}
