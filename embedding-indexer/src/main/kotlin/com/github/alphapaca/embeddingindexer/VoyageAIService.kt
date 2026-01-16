package com.github.alphapaca.embeddingindexer

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class VoyageAIService(
    private val apiKey: String,
    private val socksProxyHost: String? = System.getenv("SOCKS_PROXY_HOST"),
    private val socksProxyPort: Int? = System.getenv("SOCKS_PROXY_PORT")?.toIntOrNull(),
) {

    private val logger = LoggerFactory.getLogger(VoyageAIService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true  // Required to include 'model' field with default value
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }

        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(120, TimeUnit.SECONDS)
                writeTimeout(120, TimeUnit.SECONDS)

                if (socksProxyHost != null && socksProxyPort != null) {
                    proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksProxyHost, socksProxyPort)))
                    logger.info("Configured SOCKS proxy: $socksProxyHost:$socksProxyPort")
                }
            }
        }
    }

    /**
     * Get embeddings for a list of texts.
     *
     * @param texts List of texts to embed
     * @param model Embedding model to use (default: voyage-3.5-lite, use voyage-code-3 for code)
     */
    suspend fun getEmbeddings(
        texts: List<String>,
        model: String = DEFAULT_EMBEDDING_MODEL
    ): List<List<Float>> {
        require(texts.isNotEmpty()) { "Texts list cannot be empty" }
        require(texts.size <= MAX_BATCH_SIZE) { "Batch size cannot exceed $MAX_BATCH_SIZE" }

        val request = EmbeddingRequest(input = texts, model = model)
        val requestJson = json.encodeToString(EmbeddingRequest.serializer(), request)
        logger.debug("Request body (${requestJson.length} chars): ${requestJson.take(200)}...${requestJson.takeLast(100)}")

        val response = client.post(BASE_URL) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(requestJson)
        }

        val responseText = response.bodyAsText()

        if (!response.status.isSuccess()) {
            logger.error("API error (${response.status}): $responseText")
            throw RuntimeException("VoyageAI API error: ${response.status} - $responseText")
        }

        logger.debug("Raw response: ${responseText.take(500)}...")

        val embeddingResponse: EmbeddingResponse = json.decodeFromString(responseText)

        logger.debug("Embedded ${texts.size} texts, used ${embeddingResponse.usage.totalTokens} tokens")

        // Sort by index to maintain order
        return embeddingResponse.data
            .sortedBy { it.index }
            .map { it.embedding }
    }

    /**
     * Get embeddings for a large list of texts, processing in batches.
     *
     * @param texts List of texts to embed
     * @param model Embedding model to use (default: voyage-3.5-lite, use voyage-code-3 for code)
     * @param batchSize Number of texts per API request
     * @param onProgress Callback for progress updates
     */
    suspend fun getEmbeddingsBatched(
        texts: List<String>,
        model: String = DEFAULT_EMBEDDING_MODEL,
        batchSize: Int = MAX_BATCH_SIZE,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<List<Float>> {
        val results = mutableListOf<List<Float>>()
        val batches = texts.chunked(batchSize)

        batches.forEachIndexed { batchIndex, batch ->
            logger.info("Processing batch ${batchIndex + 1}/${batches.size} (${batch.size} texts)")

            val embeddings = retryWithBackoff {
                getEmbeddings(batch, model)
            }

            results.addAll(embeddings)
            onProgress(results.size, texts.size)

            // Rate limiting: wait between batches
            if (batchIndex < batches.size - 1) {
                delay(BATCH_DELAY_MS)
            }
        }

        return results
    }

    suspend fun rerank(
        query: String,
        documents: List<String>,
        topK: Int? = null,
    ): List<RerankResult> {
        require(documents.isNotEmpty()) { "Documents list cannot be empty" }
        require(documents.size <= MAX_RERANK_DOCUMENTS) { "Cannot rerank more than $MAX_RERANK_DOCUMENTS documents" }

        val request = RerankRequest(
            query = query,
            documents = documents,
            topK = topK,
        )
        val requestJson = json.encodeToString(RerankRequest.serializer(), request)
        logger.debug("Rerank request: ${requestJson.take(300)}...")

        val response = client.post(RERANK_URL) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(requestJson)
        }

        val responseText = response.bodyAsText()
        System.err.println("Rerank raw response: $responseText")

        if (!response.status.isSuccess()) {
            System.err.println("Rerank API error (${response.status}): $responseText")
            throw RuntimeException("VoyageAI Rerank API error: ${response.status} - $responseText")
        }

        val rerankResponse: RerankResponse = json.decodeFromString(responseText)
        System.err.println("Reranked ${documents.size} documents, used ${rerankResponse.tokenCount()} tokens")

        return rerankResponse.resultsList()
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
                logger.warn("Attempt ${attempt + 1} failed: ${e.message}, retrying in ${currentDelay}ms")
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
        private const val RERANK_URL = "https://api.voyageai.com/v1/rerank"
        private const val MAX_BATCH_SIZE = 128
        private const val MAX_RERANK_DOCUMENTS = 1000
        private const val BATCH_DELAY_MS = 100L

        // Available embedding models
        const val DEFAULT_EMBEDDING_MODEL = "voyage-3.5-lite"
        const val CODE_EMBEDDING_MODEL = "voyage-code-3"
    }
}
