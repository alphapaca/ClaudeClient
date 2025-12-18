package com.github.alphapaca.claudeclient.domain.model

data class ConversationInfo(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val unreadCount: Int = 0,
)
