package com.github.alphapaca.webagent.api

import ai.koog.agents.core.agent.AIAgentService
import com.github.alphapaca.embeddingindexer.VectorStore
import com.github.alphapaca.embeddingindexer.VoyageAIService
import com.github.alphapaca.webagent.api.models.*
import com.github.alphapaca.webagent.config.AppConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Routes")

private suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelayMs: Long = 2000,
    maxDelayMs: Long = 30000,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            val isRateLimitError = e.message?.contains("429") == true ||
                    e.message?.contains("rate_limit") == true ||
                    e.message?.contains("Rate limit") == true

            if (!isRateLimitError || attempt == maxRetries - 1) {
                throw e
            }

            logger.warn("Rate limit hit, attempt ${attempt + 1}/$maxRetries, waiting ${currentDelay}ms before retry")
            delay(currentDelay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
        }
    }
    throw IllegalStateException("Should not reach here")
}

fun Application.configureRouting(
    agentService: AIAgentService<String, String, *>,
    vectorStore: VectorStore?,
    voyageService: VoyageAIService?,
    config: AppConfig,
) {
    val githubBaseUrl = "https://github.com/${config.githubOwner}/${config.githubRepo}"
    routing {
        // API routes first
        route("/api") {
            // Health check endpoint
            get("/health") {
                val codeChunkCount = try {
                    vectorStore?.getCodeChunkCount() ?: 0
                } catch (e: Exception) {
                    -1
                }

                call.respond(
                    HealthResponse(
                        status = "ok",
                        codeChunksIndexed = codeChunkCount,
                    )
                )
            }

            // Ask a question (RAG endpoint using Koog agent)
            post("/ask") {
                try {
                    val request = call.receive<AskRequest>()

                    if (request.question.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(error = "Question cannot be empty")
                        )
                        return@post
                    }

                    logger.info("Received question: ${request.question.take(100)}...")
                    val startTime = System.currentTimeMillis()

                    // Run the Koog agent service with retry for rate limits
                    logger.info("Starting agent run...")
                    val answer = retryWithBackoff {
                        agentService.createAgentAndRun(request.question)
                    }
                    logger.info("Agent completed, answer length: ${answer.length}")

                    val processingTime = System.currentTimeMillis() - startTime
                    logger.info("Question answered in ${processingTime}ms")

                    // Build source references
                    val sources = buildSourceReferences(config, githubBaseUrl)

                    call.respond(
                        AskResponse(
                            answer = answer,
                            sources = sources,
                            processingTimeMs = processingTime,
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error processing question: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            error = "Failed to process question",
                            details = e.message,
                        )
                    )
                }
            }

            // Direct code search endpoint
            get("/search") {
                try {
                    val query = call.parameters["q"]
                    val limit = call.parameters["limit"]?.toIntOrNull() ?: 10

                    if (query.isNullOrBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(error = "Query parameter 'q' is required")
                        )
                        return@get
                    }

                    if (!config.enableCodeSearch || vectorStore == null || voyageService == null) {
                        call.respond(
                            SearchResponse(results = emptyList(), totalCount = 0)
                        )
                        return@get
                    }

                    logger.info("Searching code: $query (limit: $limit)")

                    val results = withTimeoutOrNull(10_000) {
                        val queryEmbedding = voyageService.getEmbeddings(listOf(query)).first()
                        vectorStore.searchSimilarCode(queryEmbedding, limit.coerceIn(1, 50))
                    } ?: emptyList()

                    call.respond(
                        SearchResponse(
                            results = results.map { result ->
                                CodeSearchResultDto(
                                    filePath = result.filePath,
                                    name = result.name,
                                    chunkType = result.chunkType.name,
                                    startLine = result.startLine,
                                    endLine = result.endLine,
                                    content = result.content,
                                    similarity = 1f - result.distance,
                                    signature = result.signature,
                                )
                            },
                            totalCount = results.size,
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error searching code: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            error = "Failed to search code",
                            details = e.message,
                        )
                    )
                }
            }
        }

        // Serve static web frontend (after API routes)
        get("/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
            val resourcePath = if (path.isEmpty()) "web/index.html" else "web/$path"

            val resource = this::class.java.classLoader.getResource(resourcePath)
            if (resource != null) {
                val bytes = resource.readBytes()
                val contentType = when {
                    resourcePath.endsWith(".html") -> ContentType.Text.Html
                    resourcePath.endsWith(".js") -> ContentType.Application.JavaScript
                    resourcePath.endsWith(".wasm") -> ContentType("application", "wasm")
                    resourcePath.endsWith(".css") -> ContentType.Text.CSS
                    resourcePath.endsWith(".json") -> ContentType.Application.Json
                    resourcePath.endsWith(".map") -> ContentType.Application.Json
                    resourcePath.endsWith(".ico") -> ContentType("image", "x-icon")
                    else -> ContentType.Application.OctetStream
                }
                call.respondBytes(bytes, contentType)
            } else {
                // SPA fallback - serve index.html for unknown routes
                val indexResource = this::class.java.classLoader.getResource("web/index.html")
                if (indexResource != null) {
                    call.respondBytes(indexResource.readBytes(), ContentType.Text.Html)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

private fun buildSourceReferences(config: AppConfig, githubBaseUrl: String): List<SourceReference> {
    return buildList {
        // Always include documentation source
        add(
            SourceReference(
                type = SourceType.DOCUMENTATION,
                title = "CLAUDE.md",
                location = "CLAUDE.md",
                url = "$githubBaseUrl/blob/main/CLAUDE.md",
            )
        )

        if (config.enableCodeSearch) {
            add(
                SourceReference(
                    type = SourceType.CODE,
                    title = "Codebase Search",
                    location = "Vector Search",
                    url = githubBaseUrl,
                )
            )
        }

        if (config.enableIssueSearch) {
            add(
                SourceReference(
                    type = SourceType.GITHUB_ISSUE,
                    title = "GitHub Issues",
                    location = "GitHub Issues",
                    url = "$githubBaseUrl/issues",
                )
            )
        }
    }
}
