package com.github.alphapaca.claudeclient.data.service

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.api.ollama.OllamaChatRequest
import com.github.alphapaca.claudeclient.data.api.ollama.OllamaChatResponse
import com.github.alphapaca.claudeclient.data.api.ollama.OllamaMessage
import com.github.alphapaca.claudeclient.data.api.ollama.OllamaOptions
import com.github.alphapaca.claudeclient.data.mcp.MCPTool
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.model.StopReason
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class OllamaService(
    private val client: HttpClient,
    private val json: Json,
) : LLMService {

    override fun isServiceFor(model: LLMModel): Boolean {
        return model == LLMModel.LLAMA_3_2
    }

    override suspend fun sendMessage(
        messages: List<ConversationItem>,
        model: LLMModel,
        systemPrompt: String?,
        temperature: Double?,
        maxTokens: Int,
        tools: List<MCPTool>,
    ): LLMResponse {
        val systemMessages = systemPrompt
            ?.takeIf { it.isNotBlank() }
            ?.let { listOf(OllamaMessage(role = ROLE_SYSTEM, content = it)) }
            .orEmpty()

        val request = OllamaChatRequest(
            model = model.apiName,
            messages = systemMessages + messages.map { it.toOllamaMessage() },
            stream = false,
            options = OllamaOptions(
                temperature = temperature,
                numPredict = maxTokens,
            ),
        )

        val responseText = client.post("api/chat") {
            setBody(request)
        }.bodyAsText()

        // Ollama may return streaming NDJSON even with stream=false
        // Parse each line and accumulate the content, using the final response for metadata
        val responses = responseText.lines()
            .filter { it.isNotBlank() }
            .map { json.decodeFromString<OllamaChatResponse>(it) }

        val finalResponse = responses.lastOrNull()
            ?: throw IllegalStateException("No response from Ollama")

        // Accumulate content from all streaming chunks
        val fullContent = responses.joinToString("") { it.message.content }

        Logger.d(TAG) { "Response: model=${finalResponse.model}, doneReason=${finalResponse.doneReason}, prompt=${finalResponse.promptEvalCount}, completion=${finalResponse.evalCount}" }
        Logger.v(TAG) { "Content: ${fullContent.take(500)}" }

        return LLMResponse(
            content = fullContent,
            inputTokens = finalResponse.promptEvalCount ?: 0,
            outputTokens = finalResponse.evalCount ?: 0,
            stopReason = StopReason.fromOllamaReason(finalResponse.doneReason),
        )
    }

    private fun ConversationItem.toOllamaMessage(): OllamaMessage {
        return when (this) {
            is ConversationItem.User -> OllamaMessage(
                role = ROLE_USER,
                content = content
            )
            is ConversationItem.Assistant -> OllamaMessage(
                role = ROLE_ASSISTANT,
                content = content.toMessageContent()
            )
            is ConversationItem.Summary -> OllamaMessage(
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
        const val TAG = "OllamaService"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"
        const val ROLE_SYSTEM = "system"
    }
}
