package com.github.alphapaca.claudeclient.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.domain.model.Conversation
import com.github.alphapaca.claudeclient.domain.model.ConversationInfo
import com.github.alphapaca.claudeclient.domain.usecase.ClearConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.CompactConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.DeleteConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetABikeUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetAllConversationsUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMostRecentConversationIdUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetTemperatureFlowUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetWeatherUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SendMessageUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetSystemPromptUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getWeatherUseCase: GetWeatherUseCase,
    private val getABikeUseCase: GetABikeUseCase,
    private val setSystemPromptUseCase: SetSystemPromptUseCase,
    private val clearConversationUseCase: ClearConversationUseCase,
    private val compactConversationUseCase: CompactConversationUseCase,
    private val deleteConversationUseCase: DeleteConversationUseCase,
    private val getConversationUseCase: GetConversationUseCase,
    private val getAllConversationsUseCase: GetAllConversationsUseCase,
    private val getMostRecentConversationIdUseCase: GetMostRecentConversationIdUseCase,
    getTemperatureFlowUseCase: GetTemperatureFlowUseCase,
) : ViewModel() {

    private val _currentConversationId = MutableStateFlow(NEW_CONVERSATION_ID)
    val currentConversationId: StateFlow<Long> = _currentConversationId.asStateFlow()

    init {
        viewModelScope.launch {
            _currentConversationId.value = getMostRecentConversationIdUseCase() ?: NEW_CONVERSATION_ID
        }
    }

    private val errorHandlingScope = viewModelScope + CoroutineExceptionHandler { _, exception ->
        _error.value = exception.message ?: "Unknown error occurred"
        Logger.e("ChatViewModel", exception) { "error occurred" }
        _isLoading.value = false
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val currentConversation = _currentConversationId.flatMapLatest { id ->
        if (id == NEW_CONVERSATION_ID) {
            flowOf(Conversation(emptyList()))
        } else {
            getConversationUseCase(id)
        }
    }

    val chatItems: Flow<List<ChatItem>> = combine(
        currentConversation,
        isLoading,
    ) { conversation, isLoading ->
        val mapped = conversation.items.map { ChatItem.Conversation(it) }
        if (isLoading) mapped else mapped + suggests
    }

    val tokensUsed: Flow<Int> = currentConversation
        .map { conversation -> conversation.totalInputTokens + conversation.totalOutputTokens }

    val totalCost: Flow<Double> = currentConversation
        .map { conversation -> conversation.totalCost }

    val temperature: Flow<Double?> = getTemperatureFlowUseCase()

    val conversations: Flow<List<ConversationInfo>> = getAllConversationsUseCase()

    fun createNewConversation() {
        if (_currentConversationId.value == NEW_CONVERSATION_ID) {
            return
        }
        _currentConversationId.value = NEW_CONVERSATION_ID
    }

    fun switchConversation(conversationId: Long) {
        if (conversationId != _currentConversationId.value) {
            _currentConversationId.value = conversationId
        }
    }

    fun deleteCurrentConversation() {
        val idToDelete = _currentConversationId.value
        if (idToDelete == NEW_CONVERSATION_ID) {
            return
        }

        viewModelScope.launch {
            deleteConversationUseCase(idToDelete)
            // Switch to another conversation or new conversation
            conversations.collect { list ->
                _currentConversationId.value = list.firstOrNull()?.id ?: NEW_CONVERSATION_ID
                return@collect
            }
        }
    }

    fun sendMessage(userMessage: String) {
        launchWithLoading {
            val conversationId = sendMessageUseCase(_currentConversationId.value, userMessage)
            if (_currentConversationId.value == NEW_CONVERSATION_ID) {
                _currentConversationId.value = conversationId
            }
        }
    }

    fun onSuggestClick(suggest: ChatItem.Suggest) {
        when (suggest) {
            ChatItem.Suggest.GetWeather -> launchWithLoading {
                val conversationId = getWeatherUseCase(_currentConversationId.value)
                if (_currentConversationId.value == NEW_CONVERSATION_ID) {
                    _currentConversationId.value = conversationId
                }
            }
            ChatItem.Suggest.GetABike -> launchWithLoading {
                val conversationId = getABikeUseCase(_currentConversationId.value)
                if (_currentConversationId.value == NEW_CONVERSATION_ID) {
                    _currentConversationId.value = conversationId
                }
            }
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

    fun clearMessages() {
        viewModelScope.launch {
            clearConversationUseCase(_currentConversationId.value)
            _error.value = null
        }
    }

    fun compactConversation() {
        launchWithLoading {
            compactConversationUseCase(_currentConversationId.value)
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

    companion object {
        const val NEW_CONVERSATION_ID = -1L

        private val suggests = ChatItem.SuggestGroup(
            ChatItem.Suggest.entries
        )
    }
}
