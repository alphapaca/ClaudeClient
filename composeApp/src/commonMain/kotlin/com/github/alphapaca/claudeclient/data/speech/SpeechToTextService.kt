package com.github.alphapaca.claudeclient.data.speech

import kotlinx.coroutines.flow.StateFlow

/**
 * Service for speech-to-text functionality.
 * Platform-specific implementations use Android SpeechRecognizer (Android) and Vosk (JVM).
 */
interface SpeechToTextService {
    /**
     * Current state of the speech recognition.
     */
    val state: StateFlow<SpeechState>

    /**
     * Whether speech recognition is available on this platform/device.
     */
    val isAvailable: Boolean

    /**
     * Start listening for speech input.
     */
    suspend fun startListening()

    /**
     * Stop listening and process the final result.
     */
    suspend fun stopListening()

    /**
     * Cancel listening without processing any result.
     */
    fun cancelListening()
}

/**
 * Represents the current state of speech recognition.
 */
sealed class SpeechState {
    /**
     * Not listening, ready to start.
     */
    data object Idle : SpeechState()

    /**
     * Actively listening for speech.
     */
    data object Listening : SpeechState()

    /**
     * Processing speech with optional partial results.
     */
    data class Processing(val partialText: String = "") : SpeechState()

    /**
     * Successfully recognized speech.
     */
    data class Result(val text: String) : SpeechState()

    /**
     * An error occurred during speech recognition.
     * @param message Human-readable error message
     * @param isPermissionError True if the error is due to missing microphone permission (Android only)
     */
    data class Error(val message: String, val isPermissionError: Boolean = false) : SpeechState()
}

/**
 * Factory function to create a platform-specific SpeechToTextService.
 */
expect fun createSpeechToTextService(): SpeechToTextService
