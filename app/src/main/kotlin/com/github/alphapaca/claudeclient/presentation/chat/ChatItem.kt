package com.github.alphapaca.claudeclient.presentation.chat

import com.github.alphapaca.claudeclient.domain.model.ConversationItem

sealed interface ChatItem {
    class Conversation(val item: ConversationItem) : ChatItem
    sealed interface Suggest : ChatItem {
        data object GetWeather : Suggest
    }
}