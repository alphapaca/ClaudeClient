package com.github.alphapaca.claudeclient.data.api

import com.github.alphapaca.claudeclient.BuildConfig
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

object ClaudeApiClientFactory {
    private const val BASE_URL = "https://api.anthropic.com/"
    private const val API_VERSION = "2023-06-01"

    fun create(json: Json): HttpClient {
        return HttpClient(OkHttp) {
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
