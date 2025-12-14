package com.github.alphapaca.claudeclient.data.parser

import com.github.alphapaca.claudeclient.domain.model.ConversationItem.ContentBlock
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ContentBlockParserTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val parser = ContentBlockParser(json)

    @Test
    fun `parse plain text returns single text block`() {
        val content = "Hello, this is a simple message"
        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertIs<ContentBlock.Text>(result[0])
        assertEquals(content, (result[0] as ContentBlock.Text).text)
    }

    @Test
    fun `parse weather json returns weather widget`() {
        val content = """{"type":"weather","city":"Berlin","temperature":15,"weatherCondition":"SUNNY","humidity":60,"windSpeed":10,"feelsLikeTemperature":14,"highTemperature":18,"lowTemperature":12}"""
        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertIs<ContentBlock.WeatherData>(result[0])
        val weather = result[0] as ContentBlock.WeatherData
        assertEquals("Berlin", weather.city)
        assertEquals(15, weather.temperature)
        assertEquals(ContentBlock.WeatherData.Condition.SUNNY, weather.weatherCondition)
    }

    @Test
    fun `parse bike json returns bike widget`() {
        val content = """{"type":"bike","bikeType":"Mountain Bike","explanation":"Great for trails","keyFeatures":["Suspension","Wide tires"],"exampleModel":"Trek X-Caliber","examplePrice":"$1,500","productUrl":"https://example.com"}"""
        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertIs<ContentBlock.BikeData>(result[0])
        val bike = result[0] as ContentBlock.BikeData
        assertEquals("Mountain Bike", bike.bikeType)
        assertEquals(2, bike.keyFeatures.size)
    }

    @Test
    fun `parse text before json returns text and widget blocks`() {
        val content = """Here's the weather:
{"type":"weather","city":"Berlin","temperature":15,"weatherCondition":"SUNNY","humidity":60,"windSpeed":10,"feelsLikeTemperature":14,"highTemperature":18,"lowTemperature":12}"""
        val result = parser.parse(content)

        assertEquals(2, result.size)
        assertIs<ContentBlock.Text>(result[0])
        assertEquals("Here's the weather:", (result[0] as ContentBlock.Text).text)
        assertIs<ContentBlock.WeatherData>(result[1])
    }

    @Test
    fun `parse text after json returns widget and text blocks`() {
        val content = """{"type":"weather","city":"Berlin","temperature":15,"weatherCondition":"SUNNY","humidity":60,"windSpeed":10,"feelsLikeTemperature":14,"highTemperature":18,"lowTemperature":12}
Enjoy your day!"""
        val result = parser.parse(content)

        assertEquals(2, result.size)
        assertIs<ContentBlock.WeatherData>(result[0])
        assertIs<ContentBlock.Text>(result[1])
        assertEquals("Enjoy your day!", (result[1] as ContentBlock.Text).text)
    }

    @Test
    fun `parse text before and after json returns three blocks`() {
        val content = """Here's the weather for Berlin:
{"type":"weather","city":"Berlin","temperature":15,"weatherCondition":"SUNNY","humidity":60,"windSpeed":10,"feelsLikeTemperature":14,"highTemperature":18,"lowTemperature":12}
Have a great day!"""
        val result = parser.parse(content)

        assertEquals(3, result.size)
        assertIs<ContentBlock.Text>(result[0])
        assertIs<ContentBlock.WeatherData>(result[1])
        assertIs<ContentBlock.Text>(result[2])
    }

    @Test
    fun `parse multiple widgets returns multiple widget blocks`() {
        val content = """{"type":"weather","city":"Berlin","temperature":15,"weatherCondition":"SUNNY","humidity":60,"windSpeed":10,"feelsLikeTemperature":14,"highTemperature":18,"lowTemperature":12}
And here's a bike recommendation:
{"type":"bike","bikeType":"City Bike","explanation":"Perfect for urban commuting","keyFeatures":["Lightweight"],"exampleModel":"Giant Escape","examplePrice":"$800","productUrl":"https://example.com"}"""
        val result = parser.parse(content)

        assertEquals(3, result.size)
        assertIs<ContentBlock.WeatherData>(result[0])
        assertIs<ContentBlock.Text>(result[1])
        assertIs<ContentBlock.BikeData>(result[2])
    }

    @Test
    fun `parse ignores non-widget json objects`() {
        val content = """Here's some data: {"name": "John", "age": 30} and more text."""
        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertIs<ContentBlock.Text>(result[0])
        assertEquals(content, (result[0] as ContentBlock.Text).text)
    }

    @Test
    fun `parse handles nested braces in strings`() {
        val content = """{"type":"bike","bikeType":"Mountain Bike","explanation":"Great for {rough} terrain","keyFeatures":["Test"],"exampleModel":"Trek","examplePrice":"$1000","productUrl":"https://example.com"}"""
        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertIs<ContentBlock.BikeData>(result[0])
        val bike = result[0] as ContentBlock.BikeData
        assertEquals("Great for {rough} terrain", bike.explanation)
    }

    @Test
    fun `parse handles escaped quotes in strings`() {
        val content = """{"type":"bike","bikeType":"Mountain Bike","explanation":"Called \"The Beast\"","keyFeatures":["Test"],"exampleModel":"Trek","examplePrice":"$1000","productUrl":"https://example.com"}"""
        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertIs<ContentBlock.BikeData>(result[0])
        val bike = result[0] as ContentBlock.BikeData
        assertEquals("Called \"The Beast\"", bike.explanation)
    }

    @Test
    fun `parse empty string returns single empty text block`() {
        val content = ""
        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertIs<ContentBlock.Text>(result[0])
        assertEquals("", (result[0] as ContentBlock.Text).text)
    }

    @Test
    fun `parse invalid widget json keeps it as text`() {
        val content = """{"type":"weather","city":"Berlin"}"""  // Missing required fields
        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertIs<ContentBlock.Text>(result[0])
    }
}
