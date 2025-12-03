package com.github.alphapaca.claudeclient.presentation.chat

import com.github.alphapaca.claudeclient.data.api.Message
import com.github.alphapaca.claudeclient.presentation.weather.WeatherData

sealed interface ChatItem {
    sealed interface Conversation : ChatItem
    data class Text(
        val message: Message,
    ) : Conversation
    data class Weather(
        val weatherData: WeatherData,
    ) : Conversation

    enum class Suggest : ChatItem {
        ShowWeatherInRandomCity
    }
}