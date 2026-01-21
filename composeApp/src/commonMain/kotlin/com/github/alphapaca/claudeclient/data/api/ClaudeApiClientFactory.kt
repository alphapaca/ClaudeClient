package com.github.alphapaca.claudeclient.data.api

import ClaudeClient.composeApp.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

object ClaudeApiClientFactory {
    private const val BASE_URL = "https://api.anthropic.com/"
    private const val API_VERSION = "2023-06-01"

    fun create(json: Json): HttpClient {
        return HttpClient(OkHttp.create {
            config {
                callTimeout(300.seconds)
                readTimeout(300.seconds)
                writeTimeout(300.seconds)
            }
        }) {
            install(ContentNegotiation) {
                json(json)
            }

            install(Logging) {
                logger = Logger.ANDROID
                level = LogLevel.BODY
            }

            defaultRequest {
                url(BASE_URL)

                header("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                header("anthropic-version", API_VERSION)
                contentType(ContentType.Application.Json)
            }
        }
    }
}

object DeepSeekApiClientFactory {
    private const val BASE_URL = "https://api.deepseek.com/"

    fun create(json: Json): HttpClient {
        return HttpClient(OkHttp.create {
            config {
                callTimeout(300.seconds)
                readTimeout(300.seconds)
                writeTimeout(300.seconds)
            }
        }) {
            install(ContentNegotiation) {
                json(json)
            }

            install(Logging) {
                logger = Logger.ANDROID
                level = LogLevel.BODY
            }

            defaultRequest {
                url(BASE_URL)
                header("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
                contentType(ContentType.Application.Json)
            }
        }
    }
}

object OllamaApiClientFactory {
    private const val DEFAULT_BASE_URL = "http://localhost:11434/"
    private val CONTENT_TYPE_NDJSON = ContentType("application", "x-ndjson")

    fun create(json: Json, baseUrl: String = DEFAULT_BASE_URL): HttpClient {
        return HttpClient(OkHttp.create {
            config {
                callTimeout(300.seconds)
                readTimeout(300.seconds)
                writeTimeout(300.seconds)
            }
        }) {
            install(ContentNegotiation) {
                json(json)
                // Ollama returns application/x-ndjson even with stream=false
                json(json, CONTENT_TYPE_NDJSON)
            }

            install(Logging) {
                logger = Logger.ANDROID
                level = LogLevel.BODY
            }

            defaultRequest {
                url(baseUrl)
                contentType(ContentType.Application.Json)
            }
        }
    }
}
