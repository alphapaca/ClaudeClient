package com.github.alphapaca

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HNStory(
    val id: Long,
    val title: String,
    val url: String? = null,
    val by: String,
    val score: Int,
    val time: Long,
    val descendants: Int? = null
)

class HackerNewsService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val baseUrl = "https://hacker-news.firebaseio.com/v0"

    suspend fun getTopStoryIds(): List<Long> {
        return client.get("$baseUrl/topstories.json").body()
    }

    suspend fun getStory(id: Long): HNStory {
        return client.get("$baseUrl/item/$id.json").body()
    }

    suspend fun getTopStories(limit: Int = 10): List<HNStory> {
        val ids = getTopStoryIds().take(limit)
        return ids.map { getStory(it) }
    }

    fun close() {
        client.close()
    }
}
