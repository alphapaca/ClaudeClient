package com.github.alphapaca.claudeclient.presentation.weather

import kotlinx.serialization.Serializable

@Serializable
data class WeatherData(
    val city: String,
    val temperature: Int,
    val weatherCondition: WeatherCondition,
    val humidity: Int,
    val windSpeed: Int,
    val feelsLikeTemperature: Int,
    val highTemperature: Int,
    val lowTemperature: Int
)

enum class WeatherCondition {
    SUNNY, CLOUDY, RAINY, STORMY, SNOWY, FOGGY, PARTLY_CLOUDY
}

// Preview sample data
val sampleWeatherData = WeatherData(
    city = "Amsterdam",
    temperature = 18,
    weatherCondition = WeatherCondition.PARTLY_CLOUDY,
    humidity = 65,
    windSpeed = 15,
    feelsLikeTemperature = 16,
    highTemperature = 22,
    lowTemperature = 14
)