package com.github.alphapaca.claudeclient.presentation.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.alphapaca.claudeclient.domain.usecase.GetABikeUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetWeatherUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getWeatherUseCase: GetWeatherUseCase,
    private val getABikeUseCase: GetABikeUseCase,
    getConversationUseCase: GetConversationUseCase,
) : ViewModel() {
    private val errorHandlingScope = viewModelScope + CoroutineExceptionHandler { _, exception ->
        _error.value = exception.message ?: "Unknown error occurred"
        Log.e("ChatViewModel", "error occured", exception)
        _isLoading.value = false
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val chatItems: Flow<List<ChatItem>> = combine(
        getConversationUseCase(),
        isLoading,
    ) { conversationItems, isLoading ->
        val mapped = conversationItems.map { ChatItem.Conversation(it) }
        if (isLoading) mapped else mapped + suggests
    }

    fun sendMessage(userMessage: String) {
        launchWithLoading {
            sendMessageUseCase(userMessage)
        }
    }

    fun onSuggestClick(suggest: ChatItem.Suggest) {
        when (suggest) {
            ChatItem.Suggest.GetWeather -> launchWithLoading { getWeatherUseCase() }
            ChatItem.Suggest.GetABike -> launchWithLoading { getABikeUseCase() }
        }
    }

    private fun launchWithLoading(block: suspend () -> Unit) {
        errorHandlingScope.launch {
            _isLoading.value = true
            _error.value = null
            block()
            _isLoading.value = false
        }
    }

    private companion object {
        private val suggests = ChatItem.SuggestGroup(
            listOf(
                ChatItem.Suggest.GetWeather,
                ChatItem.Suggest.GetABike,
            )
        )
    }
}