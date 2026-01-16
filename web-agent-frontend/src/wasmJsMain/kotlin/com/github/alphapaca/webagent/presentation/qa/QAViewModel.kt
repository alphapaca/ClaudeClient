package com.github.alphapaca.webagent.presentation.qa

import com.github.alphapaca.webagent.data.api.AskResponse
import com.github.alphapaca.webagent.data.api.SourceReference
import com.github.alphapaca.webagent.data.api.WebAgentApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface QAItem {
    data class Question(val text: String) : QAItem
    data class Answer(
        val content: String,
        val sources: List<SourceReference>,
        val processingTimeMs: Long,
    ) : QAItem
    data class Error(val message: String) : QAItem
    data object Loading : QAItem
}

class QAViewModel(
    private val apiClient: WebAgentApiClient = WebAgentApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val _qaItems = MutableStateFlow<List<QAItem>>(emptyList())
    val qaItems: StateFlow<List<QAItem>> = _qaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Unknown)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    init {
        checkServerHealth()
    }

    fun askQuestion(question: String) {
        if (question.isBlank() || _isLoading.value) return

        scope.launch {
            _isLoading.value = true
            _qaItems.value = _qaItems.value + QAItem.Question(question) + QAItem.Loading

            try {
                val result = apiClient.ask(question)

                // Remove loading indicator
                _qaItems.value = _qaItems.value.dropLast(1)

                result.fold(
                    onSuccess = { response ->
                        _qaItems.value = _qaItems.value + QAItem.Answer(
                            content = response.answer,
                            sources = response.sources,
                            processingTimeMs = response.processingTimeMs,
                        )
                    },
                    onFailure = { error ->
                        _qaItems.value = _qaItems.value + QAItem.Error(
                            message = error.message ?: "Unknown error occurred"
                        )
                    }
                )
            } catch (e: Exception) {
                // Remove loading indicator
                _qaItems.value = _qaItems.value.dropLast(1)
                _qaItems.value = _qaItems.value + QAItem.Error(
                    message = e.message ?: "Unknown error occurred"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearHistory() {
        _qaItems.value = emptyList()
    }

    private fun checkServerHealth() {
        scope.launch {
            try {
                val result = apiClient.health()
                result.fold(
                    onSuccess = { health ->
                        _serverStatus.value = ServerStatus.Connected(
                            codeChunksIndexed = health.codeChunksIndexed
                        )
                    },
                    onFailure = {
                        _serverStatus.value = ServerStatus.Disconnected
                    }
                )
            } catch (e: Exception) {
                _serverStatus.value = ServerStatus.Disconnected
            }
        }
    }
}

sealed interface ServerStatus {
    data object Unknown : ServerStatus
    data object Disconnected : ServerStatus
    data class Connected(val codeChunksIndexed: Int) : ServerStatus
}
