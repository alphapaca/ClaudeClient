package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.domain.model.ConversationItem

class GetWeatherUseCase(
    private val sendMessageUseCase: SendMessageUseCase,
) {
    suspend operator fun invoke(conversationId: String?): String {
        val weatherConditionsList = ConversationItem.ContentBlock.WeatherData.Condition.entries
            .joinToString(separator = ", ")
        return sendMessageUseCase(
            conversationId,
            """
                You are a weather API. You need to ask about what city weather user want to know about.
                After user answered about his city, you must respond with ONLY valid JSON, no other text.

                Required format for JSON:
                {
                  "type": "weather",
                  "city": string,
                  "temperature": number,
                  "weatherCondition": "one of $weatherConditionsList",
                  "humidity": number,
                  "windSpeed": number,
                  "feelsLikeTemperature": number,
                  "highTemperature": number,
                  "lowTemperature": number
                }
            """.trimIndent()
        )
    }
}