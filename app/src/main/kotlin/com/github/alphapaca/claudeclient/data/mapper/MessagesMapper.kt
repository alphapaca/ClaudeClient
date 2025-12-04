package com.github.alphapaca.claudeclient.data.mapper

import com.github.alphapaca.claudeclient.data.api.Message
import com.github.alphapaca.claudeclient.data.api.MessageResponse
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.utils.runCatchingSuspend
import kotlinx.serialization.json.Json

class ConversationApiMapper(
    private val json: Json,
) {
    fun toConversationItem(response: MessageResponse): ConversationItem? {
        return response.content.firstOrNull()?.text?.let { assistantMessage ->
            runCatchingSuspend {
                json.decodeFromString<ConversationItem.Widget>(assistantMessage.removeJsonBlockWrappings())
            }
                .getOrElse {
                    ConversationItem.Text(
                        role = ConversationItem.Text.Role.ASSISTANT,
                        content = assistantMessage
                    )
                }
        }
    }

    fun toApiMessage(item: ConversationItem): Message {
        return when (item) {
            is ConversationItem.Text -> Message(
                role = item.role.toApiRole(),
                content = item.content
            )

            is ConversationItem.WeatherData -> Message(
                role = ROLE_ASSISTANT,
                content = json.encodeToString(item),
            )
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