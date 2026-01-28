package com.github.alphapaca.claudeclient.data.speech

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

/**
 * JVM implementation of SpeechToTextService using Vosk for offline speech recognition.
 */
class JvmSpeechToTextService : SpeechToTextService {
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    override val isAvailable: Boolean
        get() = checkMicrophoneAvailable()

    @Volatile
    private var isListening = false

    @Volatile
    private var recognizer: Recognizer? = null

    @Volatile
    private var model: Model? = null

    @Volatile
    private var targetDataLine: TargetDataLine? = null

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun startListening() {
        if (isListening) {
            Logger.w { "Already listening" }
            return
        }

        if (!isAvailable) {
            _state.value = SpeechState.Error("No microphone available")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                // Check if model needs extraction from bundled resources
                if (!VoskModelManager.isModelAvailable()) {
                    Logger.i { "Vosk model not found, extracting from resources..." }
                    val extractSuccess = VoskModelManager.extractModel { progress ->
                        _state.value = SpeechState.Processing(progress)
                    }
                    if (!extractSuccess) {
                        _state.value = SpeechState.Error("Failed to extract speech model.")
                        return@withContext
                    }
                }

                val modelPath = VoskModelManager.getModelPathIfAvailable()
                if (modelPath == null) {
                    _state.value = SpeechState.Error("Speech model not available")
                    return@withContext
                }

                // Initialize model (reuse if already loaded)
                if (model == null) {
                    _state.value = SpeechState.Processing("Loading speech model...")
                    Logger.d { "Loading Vosk model from: $modelPath" }
                    try {
                        model = Model(modelPath)
                        Logger.d { "Vosk model loaded successfully" }
                    } catch (e: UnsatisfiedLinkError) {
                        Logger.e(e) { "Vosk native library error" }
                        _state.value = SpeechState.Error("Voice input not supported on this system (native library error)")
                        return@withContext
                    }
                }

                _state.value = SpeechState.Processing("Starting microphone...")

                // Create recognizer with 16kHz sample rate (required by Vosk)
                val sampleRate = 16000f
                recognizer = Recognizer(model, sampleRate)

                // Set up audio capture
                val audioFormat = AudioFormat(
                    sampleRate,      // Sample rate
                    16,              // Sample size in bits
                    1,               // Channels (mono)
                    true,            // Signed
                    false            // Little endian
                )

                val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
                if (!AudioSystem.isLineSupported(info)) {
                    _state.value = SpeechState.Error("Audio format not supported by system")
                    return@withContext
                }

                targetDataLine = (AudioSystem.getLine(info) as TargetDataLine).apply {
                    open(audioFormat)
                    start()
                }

                isListening = true
                _state.value = SpeechState.Listening

                Logger.d { "Started listening with Vosk" }

                // Read audio and process
                val buffer = ByteArray(4096)
                val localRecognizer = recognizer
                val localLine = targetDataLine
                var accumulatedText = ""

                while (isListening && localRecognizer != null && localLine != null) {
                    val bytesRead = localLine.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        if (localRecognizer.acceptWaveForm(buffer, bytesRead)) {
                            // Final result for this segment
                            val result = localRecognizer.result
                            val text = parseVoskResult(result)
                            if (text.isNotEmpty()) {
                                accumulatedText = if (accumulatedText.isEmpty()) text else "$accumulatedText $text"
                                Logger.d { "Segment result: $text, accumulated: $accumulatedText" }
                                _state.value = SpeechState.Processing(accumulatedText)
                            }
                        } else {
                            // Partial result
                            val partialResult = localRecognizer.partialResult
                            val text = parseVoskPartialResult(partialResult)
                            if (text.isNotEmpty()) {
                                val displayText = if (accumulatedText.isEmpty()) text else "$accumulatedText $text"
                                _state.value = SpeechState.Processing(displayText)
                            }
                        }
                    }
                }

                // Get final result when stopped
                if (!isListening && recognizer != null) {
                    val finalResult = recognizer?.finalResult
                    val finalText = finalResult?.let { parseVoskResult(it) } ?: ""
                    val fullText = if (accumulatedText.isEmpty()) finalText
                                   else if (finalText.isEmpty()) accumulatedText
                                   else "$accumulatedText $finalText"
                    Logger.d { "Final result: $fullText" }

                    _state.value = if (fullText.isNotEmpty()) {
                        SpeechState.Result(fullText.trim())
                    } else {
                        SpeechState.Error("No speech recognized")
                    }
                }

            } catch (e: UnsatisfiedLinkError) {
                Logger.e(e) { "Vosk native library error" }
                _state.value = SpeechState.Error("Voice input not supported on this system")
            } catch (e: Exception) {
                Logger.e(e) { "Error during speech recognition" }
                _state.value = SpeechState.Error("Speech recognition failed: ${e.message}")
            } finally {
                cleanupAudio()
            }
        }
    }

    override suspend fun stopListening() {
        Logger.d { "Stopping listening" }
        isListening = false
    }

    override fun cancelListening() {
        Logger.d { "Cancelling listening" }
        isListening = false
        cleanupAudio()
        _state.value = SpeechState.Idle
    }

    private fun cleanupAudio() {
        targetDataLine?.let { line ->
            try {
                line.stop()
                line.close()
            } catch (e: Exception) {
                Logger.e(e) { "Error closing audio line" }
            }
        }
        targetDataLine = null

        recognizer?.close()
        recognizer = null

        // Note: We keep the model loaded for reuse
    }

    private fun parseVoskResult(jsonResult: String): String {
        return try {
            val jsonObject = json.parseToJsonElement(jsonResult).jsonObject
            jsonObject["text"]?.jsonPrimitive?.content?.trim() ?: ""
        } catch (e: Exception) {
            Logger.e(e) { "Failed to parse Vosk result: $jsonResult" }
            ""
        }
    }

    private fun parseVoskPartialResult(jsonResult: String): String {
        return try {
            val jsonObject = json.parseToJsonElement(jsonResult).jsonObject
            jsonObject["partial"]?.jsonPrimitive?.content?.trim() ?: ""
        } catch (e: Exception) {
            Logger.e(e) { "Failed to parse Vosk partial result: $jsonResult" }
            ""
        }
    }

    private fun checkMicrophoneAvailable(): Boolean {
        return try {
            val audioFormat = AudioFormat(16000f, 16, 1, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
            AudioSystem.isLineSupported(info)
        } catch (e: Exception) {
            Logger.e(e) { "Error checking microphone availability" }
            false
        }
    }
}

actual fun createSpeechToTextService(): SpeechToTextService = JvmSpeechToTextService()
