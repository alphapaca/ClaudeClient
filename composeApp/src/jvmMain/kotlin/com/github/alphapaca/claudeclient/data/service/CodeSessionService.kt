package com.github.alphapaca.claudeclient.data.service

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeCacheControl
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeErrorResponse
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeMessageRequest
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeMessageResponse
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeRequestContentBlock
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeRequestMessage
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeSystemBlock
import com.github.alphapaca.claudeclient.data.tool.CodeSearchTool
import com.github.alphapaca.claudeclient.domain.model.CodeSessionMessage
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.model.StopReason
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Service for handling LLM interactions in code sessions.
 * Integrates the code search tool for semantic code lookup.
 */
class CodeSessionService(
    private val client: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun sendMessage(
        messages: List<CodeSessionMessage>,
        model: LLMModel,
        systemPrompt: String?,
        maxTokens: Int,
        codeSearchTool: CodeSearchTool,
    ): CodeSessionLLMResponse {
        val tools = listOf(codeSearchTool.definition)
        val conversationMessages = messages.map { it.toClaudeRequestMessage() }.toMutableList()

        var totalInputTokens = 0
        var totalOutputTokens = 0
        val allTextContent = mutableListOf<String>()
        var iteration = 0

        while (iteration < MAX_TOOL_ITERATIONS) {
            iteration++

            val request = ClaudeMessageRequest(
                model = model.apiName,
                maxTokens = maxTokens,
                messages = conversationMessages,
                system = systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
                    listOf(
                        ClaudeSystemBlock(
                            text = prompt,
                            cacheControl = ClaudeCacheControl(),
                        )
                    )
                },
                temperature = null,
                tools = tools,
            )

            val response = client.post("v1/messages") {
                setBody(request)
            }

            val responseText = response.bodyAsText()
            Logger.v(TAG) { "Response (iteration $iteration): $responseText" }

            // Check for HTTP errors
            if (!response.status.isSuccess()) {
                val errorResponse = try {
                    json.decodeFromString<ClaudeErrorResponse>(responseText)
                } catch (e: Exception) {
                    throw ClaudeApiException(
                        statusCode = response.status.value,
                        errorType = "unknown",
                        message = "HTTP ${response.status.value}: $responseText"
                    )
                }
                throw ClaudeApiException(
                    statusCode = response.status.value,
                    errorType = errorResponse.error.type,
                    message = errorResponse.error.message
                )
            }

            val responseDeserialized = json.decodeFromString<ClaudeMessageResponse>(responseText)
            totalInputTokens += responseDeserialized.usage.inputTokens
            totalOutputTokens += responseDeserialized.usage.outputTokens

            // Extract text content from this response
            val textParts = responseDeserialized.content
                .filter { it.type == "text" }
                .mapNotNull { it.text }
            allTextContent.addAll(textParts)

            // Check if we need to handle tool calls
            if (responseDeserialized.stopReason == "tool_use") {
                val toolUseBlocks = responseDeserialized.content.filter { it.type == "tool_use" }

                if (toolUseBlocks.isEmpty()) {
                    Logger.w(TAG) { "stop_reason is tool_use but no tool_use blocks found" }
                    break
                }

                // Add assistant message with tool_use to conversation
                val assistantToolUses = toolUseBlocks.map { block ->
                    ClaudeRequestContentBlock.ToolUse(
                        id = block.id ?: "",
                        name = block.name ?: "",
                        input = block.input ?: JsonObject(emptyMap()),
                    )
                }
                val assistantTextContent = textParts.joinToString("\n").takeIf { it.isNotBlank() }
                conversationMessages.add(
                    ClaudeRequestMessage.assistantWithToolUse(assistantTextContent, assistantToolUses)
                )

                // Execute each tool and collect results
                val toolResults = mutableListOf<ClaudeRequestContentBlock.ToolResult>()
                for (block in toolUseBlocks) {
                    val toolUseId = block.id ?: continue
                    val toolName = block.name ?: continue
                    val toolInput = block.input ?: JsonObject(emptyMap())

                    Logger.d(TAG) { "Executing tool: $toolName with input: $toolInput" }

                    val result = when (toolName) {
                        CodeSearchTool.TOOL_NAME -> {
                            try {
                                val content = codeSearchTool.execute(toolInput)
                                ClaudeRequestContentBlock.ToolResult(
                                    toolUseId = toolUseId,
                                    content = content,
                                    isError = false,
                                )
                            } catch (e: Exception) {
                                Logger.e(TAG, e) { "Code search tool execution failed" }
                                ClaudeRequestContentBlock.ToolResult(
                                    toolUseId = toolUseId,
                                    content = "Error: ${e.message}",
                                    isError = true,
                                )
                            }
                        }
                        else -> {
                            Logger.e(TAG) { "Unknown tool: $toolName" }
                            ClaudeRequestContentBlock.ToolResult(
                                toolUseId = toolUseId,
                                content = "Error: Tool '$toolName' not found",
                                isError = true,
                            )
                        }
                    }
                    toolResults.add(result)
                }

                // Add tool results as user message
                conversationMessages.add(
                    ClaudeRequestMessage(
                        role = ROLE_USER,
                        content = toolResults,
                    )
                )

                // Continue the loop to get Claude's response to the tool results
                continue
            }

            // Not a tool_use response, we're done
            Logger.d(TAG) { "Completed after $iteration iteration(s), stop_reason: ${responseDeserialized.stopReason}" }
            break
        }

        if (iteration >= MAX_TOOL_ITERATIONS) {
            Logger.w(TAG) { "Reached max tool iterations ($MAX_TOOL_ITERATIONS)" }
            allTextContent.add("[Reached maximum tool execution limit]")
        }

        return CodeSessionLLMResponse(
            content = allTextContent.joinToString("\n\n"),
            inputTokens = totalInputTokens,
            outputTokens = totalOutputTokens,
            stopReason = StopReason.END_TURN,
        )
    }

    private fun CodeSessionMessage.toClaudeRequestMessage(): ClaudeRequestMessage {
        return when (this) {
            is CodeSessionMessage.User -> ClaudeRequestMessage.text(ROLE_USER, content)
            is CodeSessionMessage.Assistant -> ClaudeRequestMessage.text(ROLE_ASSISTANT, content)
        }
    }

    private companion object {
        const val TAG = "CodeSessionService"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"
        const val MAX_TOOL_ITERATIONS = 10
    }
}

data class CodeSessionLLMResponse(
    val content: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val stopReason: StopReason,
)

class ClaudeApiException(
    val statusCode: Int,
    val errorType: String,
    message: String,
) : Exception(message) {
    val isRateLimitError: Boolean get() = errorType == "rate_limit_error"
    val isOverloadedError: Boolean get() = errorType == "overloaded_error"
}
