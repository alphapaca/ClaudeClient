package com.github.alphapaca.claudeclient.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale

/**
 * Android implementation of SpeechToTextService using Android's SpeechRecognizer API.
 */
class AndroidSpeechToTextService : SpeechToTextService, KoinComponent {
    private val context: Context by inject()

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    private var speechRecognizer: SpeechRecognizer? = null

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Logger.d { "Speech recognizer ready" }
            _state.value = SpeechState.Listening
        }

        override fun onBeginningOfSpeech() {
            Logger.d { "Speech started" }
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Audio level changed - could be used for visual feedback
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received
        }

        override fun onEndOfSpeech() {
            Logger.d { "Speech ended" }
            _state.value = SpeechState.Processing()
        }

        override fun onError(error: Int) {
            val (message, isPermissionError) = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error" to false
                SpeechRecognizer.ERROR_CLIENT -> "Client error" to false
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required" to true
                SpeechRecognizer.ERROR_NETWORK -> "Network error" to false
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout" to false
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized" to false
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy" to false
                SpeechRecognizer.ERROR_SERVER -> "Server error" to false
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected" to false
                else -> "Unknown error ($error)" to false
            }
            Logger.e { "Speech recognition error: $message" }
            _state.value = SpeechState.Error(message, isPermissionError)
            cleanupRecognizer()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            Logger.d { "Speech result: $text" }
            _state.value = if (text.isNotEmpty()) {
                SpeechState.Result(text)
            } else {
                SpeechState.Error("No speech recognized")
            }
            cleanupRecognizer()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotEmpty()) {
                Logger.d { "Partial result: $text" }
                _state.value = SpeechState.Processing(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Reserved for future events
        }
    }

    override suspend fun startListening() {
        if (!isAvailable) {
            _state.value = SpeechState.Error("Speech recognition not available on this device")
            return
        }

        // Clean up any existing recognizer
        cleanupRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            _state.value = SpeechState.Processing() // Initializing
            speechRecognizer?.startListening(intent)
            Logger.d { "Started listening" }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to start speech recognition" }
            _state.value = SpeechState.Error("Failed to start: ${e.message}")
            cleanupRecognizer()
        }
    }

    override suspend fun stopListening() {
        speechRecognizer?.stopListening()
        Logger.d { "Stopped listening" }
    }

    override fun cancelListening() {
        speechRecognizer?.cancel()
        cleanupRecognizer()
        _state.value = SpeechState.Idle
        Logger.d { "Cancelled listening" }
    }

    private fun cleanupRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

actual fun createSpeechToTextService(): SpeechToTextService = AndroidSpeechToTextService()
