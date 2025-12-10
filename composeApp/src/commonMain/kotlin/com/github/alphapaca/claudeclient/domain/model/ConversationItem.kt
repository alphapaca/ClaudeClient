package com.github.alphapaca.claudeclient.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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