package com.github.alphapaca.claudeclient.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.domain.usecase.GetABikeUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetWeatherUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SendMessageUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetSystemPromptUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getWeatherUseCase: GetWeatherUseCase,
    private val getABikeUseCase: GetABikeUseCase,
    private val setSystemPromptUseCase: SetSystemPromptUseCase,
    getConversationUseCase: GetConversationUseCase,
) : ViewModel() {
    private val errorHandlingScope = viewModelScope + CoroutineExceptionHandler { _, exception ->
        _error.value = exception.message ?: "Unknown error occurred"
        Logger.e("ChatViewModel", exception) { "error occured" }
        _isLoading.value = false
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val chatItems: Flow<List<ChatItem>> = combine(
        getConversationUseCase(),
        isLoading,
    ) { conversation, isLoading ->
        val mapped = conversation.items.map { ChatItem.Conversation(it) }
        if (isLoading) mapped else mapped + suggests
    }

    val tokensUsed: Flow<Int> = getConversationUseCase()
        .map { conversation -> conversation.inputTokensUsed + conversation.outputTokensUsed }

    fun sendMessage(userMessage: String) {
        launchWithLoading {
            sendMessageUseCase(userMessage)
        }
    }

    fun onSuggestClick(suggest: ChatItem.Suggest) {
        when (suggest) {
            ChatItem.Suggest.GetWeather -> launchWithLoading { getWeatherUseCase() }
            ChatItem.Suggest.GetABike -> launchWithLoading { getABikeUseCase() }
            ChatItem.Suggest.GetABikeSystemPrompt -> launchWithLoading {
                setSystemPromptUseCase(
                    """
                    You are a Bike shop consultant, ask user for his preferences in several steps and at the end
                    recommend the most suitable type of bike with providing a specific example. Be specific and helpful.
    
                    After user answered all questions about his preferences, you must respond with ONLY valid JSON, no other text:
                    {
                      "type": "bike",
                      "bikeType": string, // type of bike
                      "explanation": string, // why this suits them
                      "keyFeatures": [string], // features of the bike
                      "exampleModel": string, // Brand and Model Name
                      "examplePrice": string, // ${"$"}X,XXX
                      "productUrl": string // https://...
                    }
                """.trimIndent()
                )
                sendMessage("Can you help me choose a bike?")
            }
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
            ChatItem.Suggest.entries
        )
    }
}