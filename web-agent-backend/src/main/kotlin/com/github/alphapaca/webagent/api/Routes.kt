package com.github.alphapaca.webagent.api

import com.github.alphapaca.webagent.api.models.AskRequest
import com.github.alphapaca.webagent.api.models.ErrorResponse
import com.github.alphapaca.webagent.api.models.HealthResponse
import com.github.alphapaca.webagent.api.models.SearchResponse
import com.github.alphapaca.webagent.data.repository.RAGRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Routes")

fun Application.configureRouting(ragRepository: RAGRepository) {
    routing {
        // API routes first
        route("/api") {
            // Health check endpoint
            get("/health") {
                val codeChunkCount = try {
                    ragRepository.getCodeChunkCount()
                } catch (e: Exception) {
                    -1
                }

                call.respond(
                    HealthResponse(
                        status = if (codeChunkCount >= 0) "ok" else "degraded",
                        codeChunksIndexed = codeChunkCount,
                    )
                )
            }

            // Ask a question (RAG endpoint)
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

                    val response = ragRepository.askQuestion(
                        question = request.question,
                        includeIssues = request.includeIssues,
                        maxCodeResults = request.maxCodeResults.coerceIn(1, 20),
                    )

                    call.respond(response)
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

                    logger.info("Searching code: $query (limit: $limit)")

                    val results = ragRepository.searchCode(
                        query = query,
                        limit = limit.coerceIn(1, 50),
                    )

                    call.respond(
                        SearchResponse(
                            results = results,
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
