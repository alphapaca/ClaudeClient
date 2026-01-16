package com.github.alphapaca.webagent.agent.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.github.alphapaca.embeddingindexer.VectorStore
import com.github.alphapaca.embeddingindexer.VoyageAIService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("CodeSearchTool")

@LLMDescription("Tools for searching the codebase using semantic vector search")
class CodeSearchToolSet(
    private val vectorStore: VectorStore,
    private val voyageService: VoyageAIService,
    private val githubBaseUrl: String,
) : ToolSet {

    @Tool
    @LLMDescription(
        """Search the codebase for relevant code snippets using semantic similarity.
        Returns code chunks including file paths, line numbers, function/class names, and content.
        Use this to find implementation details, understand how features work, or locate specific code."""
    )
    fun searchCode(
        @LLMDescription("The search query describing what code you're looking for")
        query: String,
        @LLMDescription("Maximum number of results to return (1-10, default 5)")
        limit: Int = 5
    ): String = runBlocking {
        logger.info("=== TOOL CALL: searchCode(query='$query', limit=$limit) ===")
        val safeLimit = limit.coerceIn(1, 10)

        val results = try {
            withTimeoutOrNull(10_000) {
                val queryEmbedding = voyageService.getEmbeddings(listOf(query)).first()
                // Fetch more results and filter out macOS metadata files
                vectorStore.searchSimilarCode(queryEmbedding, safeLimit * 3)
                    .filter { !it.filePath.contains("/._") && !it.name.startsWith("._") }
                    .take(safeLimit)
            }
        } catch (e: Exception) {
            return@runBlocking "Error searching code: ${e.message}"
        }

        if (results.isNullOrEmpty()) {
            return@runBlocking "No relevant code found for query: $query"
        }

        val response = buildString {
            appendLine("Found ${results.size} relevant code snippets:")
            appendLine()
            results.forEachIndexed { index, result ->
                appendLine("--- Code Snippet ${index + 1} ---")
                appendLine("File: ${result.filePath.substringAfter("/source/")}")  // Shorter path
                appendLine("Type: ${result.chunkType.name}")
                appendLine("Name: ${result.name}")
                result.signature?.let { appendLine("Signature: $it") }
                appendLine("Lines: ${result.startLine}-${result.endLine}")
                appendLine()
                appendLine("```kotlin")
                // Truncate very long content
                val content = result.content.take(1500)
                appendLine(if (content.length < result.content.length) "$content\n// ... truncated" else content)
                appendLine("```")
                appendLine()
            }
        }
        logger.info("=== TOOL RESULT: ${response.length} chars, ${results.size} snippets ===")
        response
    }
}
