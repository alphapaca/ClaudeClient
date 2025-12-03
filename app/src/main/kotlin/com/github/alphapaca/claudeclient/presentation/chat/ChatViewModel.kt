package com.github.alphapaca.claudeclient.presentation.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.alphapaca.claudeclient.data.api.Message
import com.github.alphapaca.claudeclient.data.api.MessageRequest
import com.github.alphapaca.claudeclient.data.repository.ClaudeRepository
import com.github.alphapaca.claudeclient.presentation.weather.WeatherCondition
import com.github.alphapaca.claudeclient.presentation.weather.WeatherData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ChatViewModel(
    private val claudeRepository: ClaudeRepository,
    private val json: Json,
) : ViewModel() {
    private val _chatItems = MutableStateFlow<List<ChatItem>>(
        listOf(ChatItem.Suggest.ShowWeatherInRandomCity)
    )
    val chatItems: StateFlow<List<ChatItem>> = _chatItems

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun sendUserMessage(userMessage: String) {
        sendMessage(userMessage) { assistantMessage ->
            ChatItem.Text(
                Message(
                    Message.ROLE_ASSISTANT,
                    assistantMessage
                )
            )
        }
    }

    fun onSuggestClick(suggest: ChatItem.Suggest) {
        when (suggest) {
            ChatItem.Suggest.ShowWeatherInRandomCity -> {
                sendMessage(
                    """
                        You are a weather API. You must respond with ONLY valid JSON, no other text.

                        Required format:
                        {
                          "city": string,
                          "temperature": number,
                          "weatherCondition": [${WeatherCondition.entries.joinToString(separator = ", ") { "\"$it\"" }}],
                          "humidity": number,
                          "windSpeed": number,
                          "feelsLikeTemperature": number,
                          "highTemperature": number,
                          "lowTemperature": number
                        }

                        Get weather for: ${cities.random()}
                    """.trimIndent()
                ) { assistantMessage ->
                    try {
                        ChatItem.Weather(json.decodeFromString<WeatherData>(assistantMessage.removeJsonBlockWrappings()))
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "failed to parse weather", e)
                        ChatItem.Text(Message(Message.ROLE_ASSISTANT, assistantMessage))
                    }
                }
            }
        }
    }

    private fun sendMessage(
        messageContent: String,
        assistantMessageToChatItem: (assistantMessage: String) -> ChatItem
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Add user message to conversation
            _chatItems.value =
                _chatItems.value + ChatItem.Text(Message(Message.ROLE_USER, messageContent))

            // Send to API
            val request = MessageRequest(
                model = "claude-sonnet-4-20250514",
                maxTokens = 1024,
                messages = _chatItems.value.filterIsInstance<ChatItem.Conversation>()
                    .map { it.toApiMessage() }
            )
            claudeRepository.sendMessage(request)
                .onSuccess { response ->
                    val assistantMessage = response.content.firstOrNull()?.text
                    if (assistantMessage != null) {
                        _chatItems.value =
                            _chatItems.value.filterIsInstance<ChatItem.Conversation>() +
                                    assistantMessageToChatItem(assistantMessage) +
                                    ChatItem.Suggest.ShowWeatherInRandomCity
                    }
                }
                .onFailure { exception ->
                    _error.value = exception.message ?: "Unknown error occurred"
                }

            _isLoading.value = false
        }
    }

    private fun ChatItem.Conversation.toApiMessage(): Message {
        return when (this) {
            is ChatItem.Text -> message
            is ChatItem.Weather -> Message(
                role = Message.ROLE_ASSISTANT,
                content = json.encodeToString(weatherData),
            )
        }
    }

    private fun String.removeJsonBlockWrappings(): String {
        return this.removeSurrounding(prefix = "```json\n", suffix = "\n```")
    }

    private companion object {
        val cities = listOf(
            "Novosibirsk", "Omsk", "Ekaterinburg", "Geneva",
            "Basel", "Paris", "Strasbourg", "Yerevan",
        )
    }
}