package com.github.alphapaca.claudeclient.data.mapper

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.api.Message
import com.github.alphapaca.claudeclient.data.api.MessageResponse
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import kotlinx.serialization.json.Json

class ConversationApiMapper(
    private val json: Json,
) {
    fun toConversationItem(response: MessageResponse): ConversationItem? {
        return response.content.firstOrNull()?.text?.let<String, ConversationItem?> { assistantMessage ->
            val parts: List<ConversationItem> = assistantMessage.splitByCodeTags()
                .mapIndexed { index, part ->
                    runCatching {
                        json.decodeFromString<ConversationItem.Widget>(part)
                    }
                        .getOrElse { e ->
                            val partTrimmed = part.trim()
                            if (partTrimmed.startsWith("{") && partTrimmed.endsWith("}")) {
                                Logger.e("ConversationApiMapper", e) { "failed to parse json" }
                                Logger.d("ConversationApiMapper") { partTrimmed }
                            }
                            ConversationItem.Text(
                                ConversationItem.Text.Role.ASSISTANT,
                                partTrimmed
                            )
                        }
                }
                .filterNot { part -> part is ConversationItem.Text && part.content.isBlank() }
            if (parts.count() == 1) parts.first() else ConversationItem.Composed(parts)
        }
    }

    fun toApiMessage(item: ConversationItem): Message {
        return when (item) {
            is ConversationItem.Text -> Message(
                role = item.role.toApiRole(),
                content = item.content
            )

            is ConversationItem.Widget -> Message(
                role = ROLE_ASSISTANT,
                content = json.encodeToString(item),
            )

            is ConversationItem.Composed -> {
                Message(
                    role = ROLE_ASSISTANT,
                    content = item.parts.map { toApiMessage(it) }
                        .joinToString(separator = "") { it.content }
                )
            }
        }
    }

    private fun ConversationItem.Text.Role.toApiRole(): String {
        return when (this) {
            ConversationItem.Text.Role.ASSISTANT -> ROLE_ASSISTANT
            ConversationItem.Text.Role.USER -> ROLE_USER
        }
    }

    private fun String.splitByCodeTags(): List<String> {
        val lines = split("\n")
        return buildList {
            var currentBlockLines = mutableListOf<String>()
            lines.forEach { line ->
                if (line.startsWith("```")) {
                    add(currentBlockLines.joinToString("\n"))
                    currentBlockLines = mutableListOf()
                } else {
                    currentBlockLines += line
                }
            }
            if (currentBlockLines.isNotEmpty()) {
                add(currentBlockLines.joinToString("\n"))
            }
        }
    }

    private companion object {
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"
    }
}