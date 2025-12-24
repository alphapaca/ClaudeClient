package com.github.alphapaca.foundationsearch

import com.github.alphapaca.embeddingindexer.VectorStore
import com.github.alphapaca.embeddingindexer.VoyageAIService
import com.github.alphapaca.embeddingindexer.loadEnvParameter
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

fun main(): Unit = runBlocking {
//    val dbPath = loadEnvParameter("FOUNDATION_DB_PATH")
    val dbPath = System.getProperty("user.home") + "/.embedding-indexer/vectors.db"
    val voyageApiKey = loadEnvParameter("VOYAGEAI_API_KEY")

    val vectorStore = VectorStore(dbPath)
    val voyageService = VoyageAIService(voyageApiKey)

    System.err.println("Foundation Search MCP Server starting...")
    System.err.println("Database: $dbPath")
    System.err.println("Chunks in database: ${vectorStore.getChunkCount()}")

    val server = Server(
        serverInfo = Implementation(
            name = "mcp-foundation-search",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            )
        )
    )

    server.addTool(
        name = "search_foundation",
        description = """Search through Isaac Asimov's Foundation series using semantic search.
            |This is a RAG (Retrieval Augmented Generation) tool - use it to find relevant
            |passages from the Foundation books before answering questions about:
            |- The Foundation universe, planets, and history
            |- Characters (Hari Seldon, The Mule, Salvor Hardin, Arkady Darell, etc.)
            |- Psychohistory and its predictions
            |- Plot events from any of the Foundation novels
            |
            |Returns the most semantically similar text passages from the indexed books.
            |IMPORTANT: Use the returned passages to ground your answers. Cite specific
            |passages when relevant.""".trimMargin(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("The search query - a question or topic about Foundation series"))
                })
                put("limit", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("Number of passages to return (default: 5, max: 10)"))
                })
            },
            required = listOf("query")
        )
    ) { request ->
        val query = request.arguments?.get("query")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(
                content = listOf(TextContent(text = "Error: 'query' parameter is required")),
                isError = true
            )

        val limit = request.arguments?.get("limit")
            ?.let { (it as? JsonPrimitive)?.int }
            ?.coerceIn(1, 10)
            ?: 5

        try {
            System.err.println("Searching for: $query (limit: $limit)")

            // Get embedding for query
            val queryEmbeddings = voyageService.getEmbeddings(listOf(query))
            val queryEmbedding = queryEmbeddings.first()

            // Search for similar passages
            val results = vectorStore.searchSimilar(queryEmbedding, limit)

            if (results.isEmpty()) {
                return@addTool CallToolResult(
                    content = listOf(TextContent(text = "No relevant passages found for: $query"))
                )
            }

            val passages = results.mapIndexed { index, result ->
                """
                |--- Passage ${index + 1} (relevance: ${String.format("%.3f", 1 - result.distance)}) ---
                |${result.content}
                """.trimMargin()
            }.joinToString("\n\n")

            CallToolResult(
                content = listOf(
                    TextContent(
                        text = """Found ${results.size} relevant passages from Foundation series:
                            |
                            |$passages
                            |
                            |---
                            |Use these passages to inform your response about the Foundation series.""".trimMargin()
                    )
                )
            )
        } catch (e: Exception) {
            System.err.println("Error during search: ${e.message}")
            e.printStackTrace(System.err)
            CallToolResult(
                content = listOf(TextContent(text = "Error searching Foundation: ${e.message}")),
                isError = true
            )
        }
    }

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )

    val done = Job()
    server.onClose {
        voyageService.close()
        vectorStore.close()
        done.complete()
    }

    server.createSession(transport)
    System.err.println("Foundation Search MCP Server ready")

    done.join()
}
