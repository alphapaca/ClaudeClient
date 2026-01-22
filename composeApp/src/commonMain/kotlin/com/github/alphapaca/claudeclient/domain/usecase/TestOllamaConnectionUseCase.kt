package com.github.alphapaca.claudeclient.domain.usecase

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

class TestOllamaConnectionUseCase(
    private val httpClient: HttpClient,
) {
    suspend operator fun invoke(baseUrl: String): Result<String> {
        return runCatching {
            val url = baseUrl.trimEnd('/') + "/api/tags"
            val response = httpClient.get(url)
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                // Parse model count from response if possible
                val modelCount = "\"models\":\\s*\\[".toRegex().find(body)?.let {
                    "\"name\"".toRegex().findAll(body).count()
                } ?: 0
                if (modelCount > 0) {
                    "Connected! Found $modelCount model(s)"
                } else {
                    "Connected! No models found"
                }
            } else {
                throw Exception("Server returned ${response.status}")
            }
        }
    }
}
