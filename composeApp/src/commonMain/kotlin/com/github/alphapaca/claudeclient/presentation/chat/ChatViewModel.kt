package com.github.alphapaca.claudeclient.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.domain.model.Conversation
import com.github.alphapaca.claudeclient.domain.model.ConversationInfo
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.domain.usecase.ClearConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.CompactConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.DeleteConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetABikeUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetAllConversationsUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMostRecentConversationIdUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetTemperatureFlowUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetWeatherUseCase
import com.github.alphapaca.claudeclient.domain.usecase.ResetUnreadCountUseCase
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
    private val resetUnreadCountUseCase: ResetUnreadCountUseCase,
    getTemperatureFlowUseCase: GetTemperatureFlowUseCase,
) : ViewModel() {

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    val conversations: Flow<List<ConversationInfo>> = getAllConversationsUseCase()

    init {
        viewModelScope.launch {
            val conversationId = getMostRecentConversationIdUseCase()
            _currentConversationId.value = conversationId
        }
        // Auto-reset unread count when viewing a conversation that has unread messages
        viewModelScope.launch {
            combine(conversations, _currentConversationId) { convList, currentId ->
                currentId?.let { id ->
                    convList.find { it.id == id }?.takeIf { it.unreadCount > 0 }
                }
            }.collect { conversationWithUnread ->
                conversationWithUnread?.let { resetUnreadCountUseCase(it.id) }
            }
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

    // Pending messages shown optimistically before DB confirms
    private val _pendingMessages = MutableStateFlow<List<ConversationItem>>(emptyList())

    private val currentConversation = _currentConversationId.flatMapLatest { id ->
        if (id == null) {
            flowOf(Conversation(emptyList()))
        } else {
            getConversationUseCase(id)
        }
    }

    val chatItems: Flow<List<ChatItem>> = combine(
        currentConversation,
        _pendingMessages,
        isLoading,
    ) { conversation, pendingMessages, isLoading ->
        val dbItems = conversation.items.map { ChatItem.Conversation(it) }
        val pendingItems = pendingMessages.map { ChatItem.Conversation(it) }
        val allItems = dbItems + pendingItems
        if (isLoading) allItems else allItems + suggests
    }

    val tokensUsed: Flow<Int> = currentConversation
        .map { conversation -> conversation.totalInputTokens + conversation.totalOutputTokens }

    val totalCost: Flow<Double> = currentConversation
        .map { conversation -> conversation.totalCost }

    val temperature: Flow<Double?> = getTemperatureFlowUseCase()

    fun createNewConversation() {
        if (_currentConversationId.value == null) {
            return
        }
        _pendingMessages.value = emptyList()
        _currentConversationId.value = null
    }

    fun switchConversation(conversationId: String) {
        if (conversationId != _currentConversationId.value) {
            _pendingMessages.value = emptyList()
            _currentConversationId.value = conversationId
            // Unread count is auto-reset by the observer in init
        }
    }

    fun deleteCurrentConversation() {
        val idToDelete = _currentConversationId.value ?: return

        viewModelScope.launch {
            deleteConversationUseCase(idToDelete)
            // Switch to another conversation or new conversation
            conversations.collect { list ->
                _currentConversationId.value = list.firstOrNull()?.id
                return@collect
            }
        }
    }

    fun sendMessage(userMessage: String) {
        val isNewConversation = _currentConversationId.value == null

        // Don't use optimistic UI - the DB flow updates fast enough
        // and using pending messages causes duplication issues
        launchWithLoading {
            val conversationId = sendMessageUseCase(_currentConversationId.value, userMessage)
            if (isNewConversation) {
                _currentConversationId.value = conversationId
            }
        }
    }

    fun onSuggestClick(suggest: ChatItem.Suggest) {
        when (suggest) {
            ChatItem.Suggest.GetWeather -> launchWithLoading {
                val conversationId = getWeatherUseCase(_currentConversationId.value)
                if (_currentConversationId.value == null) {
                    _currentConversationId.value = conversationId
                }
            }
            ChatItem.Suggest.GetABike -> launchWithLoading {
                val conversationId = getABikeUseCase(_currentConversationId.value)
                if (_currentConversationId.value == null) {
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
        val conversationId = _currentConversationId.value ?: return
        viewModelScope.launch {
            clearConversationUseCase(conversationId)
            _error.value = null
        }
    }

    fun compactConversation() {
        val conversationId = _currentConversationId.value ?: return
        launchWithLoading {
            compactConversationUseCase(conversationId)
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
        private val suggests = ChatItem.SuggestGroup(
            ChatItem.Suggest.entries
        )
    }
}
