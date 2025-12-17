package com.github.alphapaca.claudeclient.data.api.claude

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class ClaudeMessageRequest(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val messages: List<ClaudeRequestMessage>,
    val system: String? = null,
    val temperature: Double? = null,
    val tools: List<ClaudeTool>? = null,
)

/**
 * Message for Claude API request.
 * Content can be either a simple string or a list of content blocks (for tool_use/tool_result).
 */
@Serializable
data class ClaudeRequestMessage(
    val role: String,
    @Serializable(with = ClaudeRequestContentSerializer::class)
    val content: List<ClaudeRequestContentBlock>,
) {
    companion object {
        fun text(role: String, text: String) = ClaudeRequestMessage(
            role = role,
            content = listOf(ClaudeRequestContentBlock.Text(text))
        )

        fun toolResult(toolUseId: String, content: String, isError: Boolean = false) = ClaudeRequestMessage(
            role = "user",
            content = listOf(ClaudeRequestContentBlock.ToolResult(toolUseId, content, isError))
        )

        fun assistantWithToolUse(textContent: String?, toolUses: List<ClaudeRequestContentBlock.ToolUse>) = ClaudeRequestMessage(
            role = "assistant",
            content = buildList {
                if (!textContent.isNullOrBlank()) {
                    add(ClaudeRequestContentBlock.Text(textContent))
                }
                addAll(toolUses)
            }
        )
    }
}

@Serializable
sealed class ClaudeRequestContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ClaudeRequestContentBlock()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonObject,
    ) : ClaudeRequestContentBlock()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        @SerialName("tool_use_id")
        val toolUseId: String,
        val content: String,
        @SerialName("is_error")
        val isError: Boolean = false,
    ) : ClaudeRequestContentBlock()
}

/**
 * Custom serializer for content field that outputs simple string content as a string,
 * and complex content (tool_use, tool_result) as an array.
 */
object ClaudeRequestContentSerializer : JsonTransformingSerializer<List<ClaudeRequestContentBlock>>(
    ListSerializer(ClaudeRequestContentBlock.serializer())
) {
    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonArray) return element

        // If content is a single text block, serialize as just the string
        if (element.size == 1) {
            val firstBlock = element[0] as? JsonObject
            if (firstBlock != null && firstBlock["type"]?.let { (it as? JsonPrimitive)?.content } == "text") {
                val textValue = (firstBlock["text"] as? JsonPrimitive)?.content
                if (textValue != null) {
                    return JsonPrimitive(textValue)
                }
            }
        }

        return element
    }
}

@Serializable
data class ClaudeTool(
    val name: String,
    val description: String? = null,
    @SerialName("input_schema")
    val inputSchema: ClaudeToolInputSchema,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ClaudeToolInputSchema(
    @EncodeDefault
    val type: String = "object",
    val properties: JsonObject? = null,
    val required: List<String>? = null,
)
