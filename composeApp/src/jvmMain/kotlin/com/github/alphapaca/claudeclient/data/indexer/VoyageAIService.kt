package com.github.alphapaca.claudeclient.data.indexer

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class VoyageAIService(private val apiKey: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            requestTimeout = 120_000
        }
    }

    suspend fun getEmbeddings(
        texts: List<String>,
        model: String = CODE_EMBEDDING_MODEL
    ): List<List<Float>> {
        require(texts.isNotEmpty()) { "Texts list cannot be empty" }
        require(texts.size <= MAX_BATCH_SIZE) { "Batch size cannot exceed $MAX_BATCH_SIZE" }

        val request = EmbeddingRequest(input = texts, model = model)
        val requestJson = json.encodeToString(EmbeddingRequest.serializer(), request)

        val response = client.post(BASE_URL) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(requestJson)
        }

        val responseText = response.bodyAsText()

        if (!response.status.isSuccess()) {
            Logger.e("VoyageAI") { "API error (${response.status}): $responseText" }
            throw RuntimeException("VoyageAI API error: ${response.status} - $responseText")
        }

        val embeddingResponse: EmbeddingResponse = json.decodeFromString(responseText)
        Logger.d("VoyageAI") { "Embedded ${texts.size} texts, used ${embeddingResponse.usage.totalTokens} tokens" }

        return embeddingResponse.data
            .sortedBy { it.index }
            .map { it.embedding }
    }

    suspend fun getEmbeddingsBatched(
        texts: List<String>,
        model: String = CODE_EMBEDDING_MODEL,
        batchSize: Int = MAX_BATCH_SIZE,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<List<Float>> {
        val results = mutableListOf<List<Float>>()
        val batches = texts.chunked(batchSize)

        batches.forEachIndexed { batchIndex, batch ->
            Logger.d("VoyageAI") { "Processing batch ${batchIndex + 1}/${batches.size} (${batch.size} texts)" }

            val embeddings = retryWithBackoff {
                getEmbeddings(batch, model)
            }

            results.addAll(embeddings)
            onProgress(results.size, texts.size)

            if (batchIndex < batches.size - 1) {
                delay(BATCH_DELAY_MS)
            }
        }

        return results
    }

    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) throw e
                Logger.w("VoyageAI") { "Attempt ${attempt + 1} failed: ${e.message}, retrying in ${currentDelay}ms" }
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        throw IllegalStateException("Should not reach here")
    }

    fun close() {
        client.close()
    }

    companion object {
        private const val BASE_URL = "https://api.voyageai.com/v1/embeddings"
        private const val MAX_BATCH_SIZE = 128
        private const val BATCH_DELAY_MS = 100L

        const val CODE_EMBEDDING_MODEL = "voyage-code-3"
    }
}
