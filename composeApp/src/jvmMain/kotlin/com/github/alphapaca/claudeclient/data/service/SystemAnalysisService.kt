package com.github.alphapaca.claudeclient.data.service

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.api.ollama.OllamaChatRequest
import com.github.alphapaca.claudeclient.data.api.ollama.OllamaChatResponse
import com.github.alphapaca.claudeclient.data.api.ollama.OllamaMessage
import com.github.alphapaca.claudeclient.data.api.ollama.OllamaOptions
import com.github.alphapaca.claudeclient.data.mcp.MCPTool
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.model.StopReason
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisMessage
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/**
 * Service for handling LLM interactions in system analysis sessions.
 * Uses local Ollama for processing to keep analysis data local.
 */
class SystemAnalysisService(
    private val client: HttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun sendMessage(
        messages: List<SystemAnalysisMessage>,
        systemPrompt: String,
        maxTokens: Int,
    ): SystemAnalysisLLMResponse {
        val ollamaMessages = buildList {
            add(OllamaMessage(role = ROLE_SYSTEM, content = systemPrompt))
            messages.forEach { msg ->
                when (msg) {
                    is SystemAnalysisMessage.User -> add(OllamaMessage(role = ROLE_USER, content = msg.content))
                    is SystemAnalysisMessage.Assistant -> add(OllamaMessage(role = ROLE_ASSISTANT, content = msg.content))
                    is SystemAnalysisMessage.ToolResult -> add(OllamaMessage(role = ROLE_USER, content = "[Tool Result: ${msg.toolName}]\n${msg.result}"))
                }
            }
        }

        val request = OllamaChatRequest(
            model = LLMModel.LLAMA_3_2.apiName,
            messages = ollamaMessages,
            stream = false,
            options = OllamaOptions(
                temperature = 0.7,
                numPredict = maxTokens,
            ),
        )

        val baseUrl = settingsRepository.getOllamaBaseUrl().trimEnd('/')
        val responseText = client.post("$baseUrl/api/chat") {
            setBody(request)
        }.bodyAsText()

        // Ollama may return streaming NDJSON even with stream=false
        val responses = responseText.lines()
            .filter { it.isNotBlank() }
            .map { json.decodeFromString<OllamaChatResponse>(it) }

        val finalResponse = responses.lastOrNull()
            ?: throw IllegalStateException("No response from Ollama")

        val fullContent = responses.joinToString("") { it.message.content }

        Logger.d(TAG) { "Response: model=${finalResponse.model}, doneReason=${finalResponse.doneReason}, prompt=${finalResponse.promptEvalCount}, completion=${finalResponse.evalCount}" }
        Logger.v(TAG) { "Content: ${fullContent.take(500)}" }

        return SystemAnalysisLLMResponse(
            content = fullContent,
            inputTokens = finalResponse.promptEvalCount ?: 0,
            outputTokens = finalResponse.evalCount ?: 0,
            stopReason = StopReason.fromOllamaReason(finalResponse.doneReason),
        )
    }

    private companion object {
        const val TAG = "SystemAnalysisService"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"
        const val ROLE_SYSTEM = "system"
    }
}

data class SystemAnalysisLLMResponse(
    val content: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val stopReason: StopReason,
)
