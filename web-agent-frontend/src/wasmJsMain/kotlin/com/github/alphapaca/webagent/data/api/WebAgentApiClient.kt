package com.github.alphapaca.webagent.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class WebAgentApiClient {
    private val client = HttpClient(Js) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    // Use relative URL so it works with the same origin
    private val baseUrl = "/api"

    /**
     * Ask a question to the web agent.
     */
    suspend fun ask(
        question: String,
        includeIssues: Boolean = true,
        maxCodeResults: Int = 10,
    ): Result<AskResponse> {
        return try {
            val response: AskResponse = client.post("$baseUrl/ask") {
                contentType(ContentType.Application.Json)
                setBody(AskRequest(question, includeIssues, maxCodeResults))
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search code directly.
     */
    suspend fun search(query: String, limit: Int = 10): Result<SearchResponse> {
        return try {
            val response: SearchResponse = client.get("$baseUrl/search") {
                parameter("q", query)
                parameter("limit", limit)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check server health.
     */
    suspend fun health(): Result<HealthResponse> {
        return try {
            val response: HealthResponse = client.get("$baseUrl/health").body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
