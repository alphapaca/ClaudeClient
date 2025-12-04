package com.github.alphapaca.claudeclient.data.mapper

import android.util.Log
import com.github.alphapaca.claudeclient.data.api.Message
import com.github.alphapaca.claudeclient.data.api.MessageResponse
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import kotlinx.serialization.json.Json

class ConversationApiMapper(
    private val json: Json,
) {
    fun toConversationItem(response: MessageResponse): ConversationItem? {
        return response.content.firstOrNull()?.text?.let<String, ConversationItem?> { assistantMessage ->
            runCatching {
                json.decodeFromString<ConversationItem.Widget>(assistantMessage.removeJsonBlockWrappings())
            }
                .getOrElse {
                    val parts: List<ConversationItem> = assistantMessage.split("```json\n", "\n```")
                        .mapIndexed { index, part ->
                            if (index % 2 == 0) {
                                ConversationItem.Text(ConversationItem.Text.Role.ASSISTANT, part)
                            } else {
                                runCatching {
                                    json.decodeFromString<ConversationItem.Widget>(part)
                                }
                                    .getOrElse { e ->
                                        Log.e("ConversationApiMapper", "failed to parse json", e)
                                        Log.d("ConversationApiMapper", part)
                                        ConversationItem.Text(
                                            ConversationItem.Text.Role.ASSISTANT,
                                            part
                                        )
                                    }
                            }
                        }
                        .filterNot { part -> part is ConversationItem.Text && part.content.isBlank() }
                    if (parts.count() == 1) parts.first() else ConversationItem.Composed(parts)
                }
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

    private fun String.removeJsonBlockWrappings(): String {
        return this.removeSurrounding(prefix = "```json\n", suffix = "\n```")
    }

    private companion object {
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"
    }
}