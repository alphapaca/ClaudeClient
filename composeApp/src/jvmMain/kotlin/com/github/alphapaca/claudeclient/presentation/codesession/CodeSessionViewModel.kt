package com.github.alphapaca.claudeclient.presentation.codesession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.repository.CodeSessionRepository
import com.github.alphapaca.claudeclient.domain.model.CodeSession
import com.github.alphapaca.claudeclient.domain.model.CodeSessionInfo
import com.github.alphapaca.claudeclient.domain.model.CodeSessionMessage
import com.github.alphapaca.claudeclient.domain.model.IndexingPhase
import com.github.alphapaca.claudeclient.domain.model.IndexingProgress
import com.github.alphapaca.claudeclient.domain.model.IndexingStatus
import com.github.alphapaca.claudeclient.domain.usecase.IndexCodeSessionUseCase
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
class CodeSessionViewModel(
    private val codeSessionRepository: CodeSessionRepository,
    private val indexCodeSessionUseCase: IndexCodeSessionUseCase,
) : ViewModel() {

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    val sessions: Flow<List<CodeSessionInfo>> = codeSessionRepository.getAllSessions()

    private val errorHandlingScope = viewModelScope + CoroutineExceptionHandler { _, exception ->
        _error.value = exception.message ?: "Unknown error occurred"
        Logger.e("CodeSessionViewModel", exception) { "error occurred" }
        _isLoading.value = false
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _indexingProgress = MutableStateFlow<IndexingProgress?>(null)
    val indexingProgress: StateFlow<IndexingProgress?> = _indexingProgress.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    val currentSession: Flow<CodeSession?> = _currentSessionId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            codeSessionRepository.getSession(id)
        }
    }

    val messages: Flow<List<CodeSessionMessage>> = currentSession.map { session ->
        session?.messages ?: emptyList()
    }

    val indexingStatus: Flow<IndexingStatus?> = currentSession.map { session ->
        session?.indexingStatus
    }

    fun openCreateDialog() {
        _showCreateDialog.value = true
    }

    fun dismissCreateDialog() {
        _showCreateDialog.value = false
    }

    fun createSession(repoPath: String) {
        _showCreateDialog.value = false

        viewModelScope.launch {
            try {
                _indexingProgress.value = IndexingProgress(
                    phase = IndexingPhase.SCANNING,
                    current = 0,
                    total = 0,
                    message = "Creating session..."
                )

                val sessionId = codeSessionRepository.createSession(repoPath)
                _currentSessionId.value = sessionId

                // Start indexing in background
                indexCodeSessionUseCase(
                    sessionId = sessionId,
                    repoPath = repoPath,
                    dbPath = "$repoPath/.code-index/vectors.db",
                ) { progress ->
                    _indexingProgress.value = progress
                }

                _indexingProgress.value = null
            } catch (e: Exception) {
                Logger.e("CodeSessionViewModel") { "Failed to create session: ${e.message}" }
                _error.value = e.message
                _indexingProgress.value = null
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
            codeSessionRepository.deleteSession(idToDelete)
            _currentSessionId.value = sessions.first().firstOrNull()?.id
        }
    }

    fun sendMessage(userMessage: String) {
        val sessionId = _currentSessionId.value ?: return

        launchWithLoading {
            val result = codeSessionRepository.sendMessage(sessionId, userMessage)
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
