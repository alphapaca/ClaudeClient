package com.github.alphapaca.claudeclient.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class StopReason {
    END_TURN,
    MAX_TOKENS,
    STOP_SEQUENCE,
    TOOL_USE,
    CONTENT_FILTER,
    UNKNOWN;

    companion object {
        fun fromClaudeReason(reason: String?): StopReason = when (reason) {
            "end_turn" -> END_TURN
            "max_tokens" -> MAX_TOKENS
            "stop_sequence" -> STOP_SEQUENCE
            "tool_use" -> TOOL_USE
            else -> UNKNOWN
        }

        fun fromDeepSeekReason(reason: String?): StopReason = when (reason) {
            "stop" -> END_TURN
            "length" -> MAX_TOKENS
            "content_filter" -> CONTENT_FILTER
            "tool_calls" -> TOOL_USE
            else -> UNKNOWN
        }

        fun fromOllamaReason(reason: String?): StopReason = when (reason) {
            "stop" -> END_TURN
            "length" -> MAX_TOKENS
            else -> UNKNOWN
        }
    }
}

sealed interface ConversationItem {
    data class User(val content: String) : ConversationItem

    data class Assistant(
        val content: List<ContentBlock>,
        val model: LLMModel,
        val inputTokens: Int,
        val outputTokens: Int,
        val inferenceTimeMs: Long,
        val stopReason: StopReason,
    ) : ConversationItem {
        val cost: Double get() = model.calculateCost(inputTokens, outputTokens)

        val textContent: String
            get() = content.filterIsInstance<ContentBlock.Text>()
                .joinToString("\n") { it.text }

        val rawContent: String
            get() = content.joinToString("\n") { block ->
                when (block) {
                    is ContentBlock.Text -> block.text
                    is ContentBlock.Widget -> Json.encodeToString(block)
                }
            }
    }

    data class Summary(
        val content: String,
        val compactedMessageCount: Int,
    ) : ConversationItem

    sealed interface ContentBlock {
        data class Text(val text: String) : ContentBlock

        @Serializable
        sealed interface Widget : ContentBlock

        @Serializable
        @SerialName("weather")
        data class WeatherData(
            val city: String,
            val temperature: Int,
            val weatherCondition: Condition,
            val humidity: Int,
            val windSpeed: Int,
            val feelsLikeTemperature: Int,
            val highTemperature: Int,
            val lowTemperature: Int,
        ) : Widget {
            enum class Condition {
                SUNNY, CLOUDY, RAINY, STORMY, SNOWY, FOGGY, PARTLY_CLOUDY
            }
        }

        @Serializable
        @SerialName("bike")
        data class BikeData(
            val bikeType: String,
            val explanation: String,
            val keyFeatures: List<String>,
            val exampleModel: String,
            val examplePrice: String,
            val productUrl: String,
        ) : Widget
    }
}