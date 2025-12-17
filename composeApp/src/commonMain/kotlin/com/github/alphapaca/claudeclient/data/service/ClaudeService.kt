package com.github.alphapaca.claudeclient.data.service

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeMessageRequest
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeMessageResponse
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeRequestContentBlock
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeRequestMessage
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeTool
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeToolInputSchema
import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager
import com.github.alphapaca.claudeclient.data.mcp.MCPTool
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.model.StopReason
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ClaudeService(
    private val client: HttpClient,
    private val json: Json,
    private val mcpClientManager: MCPClientManager,
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
        tools: List<MCPTool>,
    ): LLMResponse {
        val claudeTools = tools.map { it.toClaudeTool() }.takeIf { it.isNotEmpty() }
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
                system = systemPrompt?.takeIf { it.isNotBlank() },
                temperature = temperature,
                tools = claudeTools,
            )

            val response = client.post("v1/messages") {
                setBody(request)
            }

            val responseText = response.bodyAsText()
            Logger.v(TAG) { "Response (iteration $iteration): $responseText" }

            val responseDeserialized: ClaudeMessageResponse = response.body()
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

                    // Find which server has this tool
                    val mcpTool = tools.find { it.name == toolName }
                    if (mcpTool == null) {
                        Logger.e(TAG) { "Tool not found: $toolName" }
                        toolResults.add(
                            ClaudeRequestContentBlock.ToolResult(
                                toolUseId = toolUseId,
                                content = "Error: Tool '$toolName' not found",
                                isError = true,
                            )
                        )
                        continue
                    }

                    // Convert JsonObject to Map<String, Any?>
                    val arguments = toolInput.mapValues { (_, value) ->
                        when {
                            value is kotlinx.serialization.json.JsonPrimitive && value.isString -> value.content
                            value is kotlinx.serialization.json.JsonPrimitive -> value.content
                            else -> value.toString()
                        }
                    }

                    val result = mcpClientManager.callTool(mcpTool.serverName, toolName, arguments)
                    result.onSuccess { content ->
                        Logger.d(TAG) { "Tool result for $toolName: ${content.take(200)}" }
                        toolResults.add(
                            ClaudeRequestContentBlock.ToolResult(
                                toolUseId = toolUseId,
                                content = content,
                                isError = false,
                            )
                        )
                    }.onFailure { error ->
                        Logger.e(TAG, error) { "Tool execution failed for $toolName" }
                        toolResults.add(
                            ClaudeRequestContentBlock.ToolResult(
                                toolUseId = toolUseId,
                                content = "Error: ${error.message}",
                                isError = true,
                            )
                        )
                    }
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

        return LLMResponse(
            content = allTextContent.joinToString("\n\n"),
            inputTokens = totalInputTokens,
            outputTokens = totalOutputTokens,
            stopReason = StopReason.END_TURN,
        )
    }

    private fun ConversationItem.toClaudeRequestMessage(): ClaudeRequestMessage {
        return when (this) {
            is ConversationItem.User -> ClaudeRequestMessage.text(ROLE_USER, content)
            is ConversationItem.Assistant -> ClaudeRequestMessage.text(
                ROLE_ASSISTANT,
                content.toMessageContent()
            )
            is ConversationItem.Summary -> ClaudeRequestMessage.text(
                ROLE_USER,
                "[Previous conversation summary: $content]"
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

    private fun MCPTool.toClaudeTool(): ClaudeTool {
        return ClaudeTool(
            name = name,
            description = description,
            inputSchema = ClaudeToolInputSchema(
                type = "object",
                properties = inputSchemaProperties,
                required = inputSchemaRequired,
            ),
        )
    }

    private companion object {
        const val TAG = "ClaudeService"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"
        const val MAX_TOOL_ITERATIONS = 10
    }
}
