package com.github.alphapaca.claudeclient.domain.model

data class Conversation(
    val items: List<ConversationItem>,
    val inputTokensUsed: Int,
    val outputTokensUsed: Int,
)