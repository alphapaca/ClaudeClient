package com.github.alphapaca.claudeclient.data.tool

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeTool
import com.github.alphapaca.claudeclient.data.api.claude.ClaudeToolInputSchema
import com.github.alphapaca.claudeclient.data.indexer.VectorStore
import com.github.alphapaca.claudeclient.data.indexer.VoyageAIService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tool that allows Claude to search indexed code using semantic similarity.
 */
class CodeSearchTool(
    private val vectorStore: VectorStore,
    private val voyageAIService: VoyageAIService,
) {
    /**
     * Get the Claude API tool definition.
     */
    val definition: ClaudeTool = ClaudeTool(
        name = TOOL_NAME,
        description = """Search the indexed codebase for relevant code snippets using semantic search.
            |Use this tool when you need to:
            |- Find implementations of specific functionality
            |- Understand how a feature or component works
            |- Locate classes, functions, or interfaces by their purpose
            |- Answer questions about the codebase structure
            |
            |The search uses embeddings to find semantically similar code, so describe what you're looking for in natural language.""".trimMargin(),
        inputSchema = ClaudeToolInputSchema(
            type = "object",
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Natural language description of what code you're looking for. Be specific about the functionality, component, or pattern you want to find."))
                })
                put("limit", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("Maximum number of results to return (default: 5, max: 10)"))
                })
            },
            required = listOf("query"),
        ),
    )

    /**
     * Execute a code search query.
     *
     * @param arguments The tool arguments from Claude (should contain "query" and optionally "limit")
     * @return Formatted search results as a string
     */
    suspend fun execute(arguments: JsonObject): String {
        val query = arguments["query"]?.jsonPrimitive?.content
            ?: return "Error: 'query' parameter is required"

        val limit = arguments["limit"]?.jsonPrimitive?.int?.coerceIn(1, 10) ?: 5

        Logger.d("CodeSearchTool") { "Searching for: $query (limit: $limit)" }

        return try {
            // Get embedding for the query
            val queryEmbedding = voyageAIService.getEmbeddings(
                texts = listOf(query),
                model = VoyageAIService.CODE_EMBEDDING_MODEL,
            ).first()

            // Search for similar code
            val results = vectorStore.searchSimilarCode(queryEmbedding, limit)

            if (results.isEmpty()) {
                "No relevant code found for query: \"$query\""
            } else {
                buildString {
                    appendLine("Found ${results.size} relevant code snippets:\n")

                    results.forEachIndexed { index, result ->
                        appendLine("--- Result ${index + 1} ---")
                        appendLine("File: ${result.filePath}")
                        appendLine("Lines: ${result.startLine}-${result.endLine}")
                        appendLine("Type: ${result.chunkType}")
                        appendLine("Name: ${result.name}")
                        result.parentName?.let { appendLine("Parent: $it") }
                        result.signature?.let { appendLine("Signature: $it") }
                        appendLine("Relevance: ${String.format("%.4f", 1 - result.distance)}")
                        appendLine()
                        appendLine("Code:")
                        appendLine("```kotlin")
                        // Remove the context prefix we added during indexing for cleaner output
                        val cleanContent = result.content
                            .lines()
                            .dropWhile { it.startsWith("// File:") || it.startsWith("// Class:") || it.isBlank() }
                            .joinToString("\n")
                        appendLine(cleanContent)
                        appendLine("```")
                        appendLine()
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("CodeSearchTool") { "Search failed: ${e.message}" }
            "Error searching code: ${e.message}"
        }
    }

    companion object {
        const val TOOL_NAME = "search_code"
    }
}
