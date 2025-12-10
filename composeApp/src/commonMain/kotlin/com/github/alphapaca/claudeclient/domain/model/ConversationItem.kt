package com.github.alphapaca.claudeclient.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    }
}

sealed interface ConversationItem {
    sealed interface Text : ConversationItem {
        val content: String

        data class User(override val content: String) : Text
        data class Assistant(
            override val content: String,
            val model: LLMModel,
            val inputTokens: Int,
            val outputTokens: Int,
            val inferenceTimeMs: Long,
            val stopReason: StopReason,
        ) : Text {
            val cost: Double get() = model.calculateCost(inputTokens, outputTokens)
        }
    }

    data class Composed(
        val parts: List<ConversationItem>,
    ) : ConversationItem

    @Serializable
    sealed interface Widget : ConversationItem

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