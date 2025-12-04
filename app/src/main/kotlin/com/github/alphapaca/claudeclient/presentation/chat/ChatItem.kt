package com.github.alphapaca.claudeclient.presentation.chat

import com.github.alphapaca.claudeclient.domain.model.ConversationItem

sealed interface ChatItem {
    class Conversation(val item: ConversationItem) : ChatItem

    class SuggestGroup(val suggests: List<Suggest>) : ChatItem

    enum class Suggest {
        GetWeather, GetABike
    }
}