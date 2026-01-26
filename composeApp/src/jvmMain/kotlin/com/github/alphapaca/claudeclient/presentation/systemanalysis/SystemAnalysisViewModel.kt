package com.github.alphapaca.claudeclient.presentation.systemanalysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.repository.SystemAnalysisRepository
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisMessage
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisSession
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisSessionInfo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

@OptIn(ExperimentalCoroutinesApi::class)
class SystemAnalysisViewModel(
    private val systemAnalysisRepository: SystemAnalysisRepository,
) : ViewModel() {

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    val sessions: Flow<List<SystemAnalysisSessionInfo>> = systemAnalysisRepository.getAllSessions()

    private val errorHandlingScope = viewModelScope + CoroutineExceptionHandler { _, exception ->
        _error.value = exception.message ?: "Unknown error occurred"
        Logger.e("SystemAnalysisViewModel", exception) { "error occurred" }
        _isLoading.value = false
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    val currentSession: Flow<SystemAnalysisSession?> = _currentSessionId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            systemAnalysisRepository.getSession(id)
        }
    }

    val messages: Flow<List<SystemAnalysisMessage>> = currentSession.map { session ->
        session?.messages ?: emptyList()
    }

    fun openCreateDialog() {
        _showCreateDialog.value = true
    }

    fun dismissCreateDialog() {
        _showCreateDialog.value = false
    }

    fun createSession(projectName: String) {
        _showCreateDialog.value = false

        viewModelScope.launch {
            try {
                val sessionId = systemAnalysisRepository.createSession(projectName)
                _currentSessionId.value = sessionId
            } catch (e: Exception) {
                Logger.e("SystemAnalysisViewModel") { "Failed to create session: ${e.message}" }
                _error.value = e.message
            }
        }
    }

    fun selectSession(sessionId: String) {
        if (sessionId != _currentSessionId.value) {
            _currentSessionId.value = sessionId
            _error.value = null
        }
    }

    fun deleteCurrentSession() {
        val idToDelete = _currentSessionId.value ?: return

        viewModelScope.launch {
            systemAnalysisRepository.deleteSession(idToDelete)
            _currentSessionId.value = sessions.first().firstOrNull()?.id
        }
    }

    fun sendMessage(userMessage: String) {
        val sessionId = _currentSessionId.value ?: return

        launchWithLoading {
            val result = systemAnalysisRepository.sendMessage(sessionId, userMessage)
            result.onFailure { e ->
                _error.value = e.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun launchWithLoading(block: suspend () -> Unit) {
        errorHandlingScope.launch {
            _isLoading.value = true
            _error.value = null
            block()
            _isLoading.value = false
        }
    }
}
