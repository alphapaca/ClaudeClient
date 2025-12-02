package com.github.alphapaca.claudeclient.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.alphapaca.claudeclient.data.api.Message
import com.github.alphapaca.claudeclient.data.api.MessageRequest
import com.github.alphapaca.claudeclient.data.repository.ClaudeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val claudeRepository: ClaudeRepository,
) : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun sendMessage(userMessage: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Add user message to conversation
            _messages.value = _messages.value + Message("user", userMessage)

            // Send to API
            val request = MessageRequest(
                model = "claude-sonnet-4-20250514",
                maxTokens = 1024,
                messages = _messages.value
            )

            claudeRepository.sendMessage(request)
                .onSuccess { response ->
                    val assistantMessage = response.content.firstOrNull()?.text
                    if (assistantMessage != null) {
                        _messages.value = _messages.value +
                                Message("assistant", assistantMessage)
                    }
                }
                .onFailure { exception ->
                    _error.value = exception.message ?: "Unknown error occurred"
                }

            _isLoading.value = false
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }
}