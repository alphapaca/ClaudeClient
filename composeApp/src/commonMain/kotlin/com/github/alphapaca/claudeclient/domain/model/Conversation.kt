package com.github.alphapaca.claudeclient.domain.model

data class Conversation(
    val items: List<ConversationItem>,
) {
    val totalCost: Double
        get() = items.filterIsInstance<ConversationItem.Text.Assistant>().sumOf { it.cost }

    val totalInputTokens: Int
        get() = items.filterIsInstance<ConversationItem.Text.Assistant>().sumOf { it.inputTokens }

    val totalOutputTokens: Int
        get() = items.filterIsInstance<ConversationItem.Text.Assistant>().sumOf { it.outputTokens }
}