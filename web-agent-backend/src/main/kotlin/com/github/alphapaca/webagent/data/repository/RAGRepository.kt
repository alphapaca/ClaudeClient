package com.github.alphapaca.webagent.data.repository

import com.github.alphapaca.embeddingindexer.CodeSearchResult
import com.github.alphapaca.embeddingindexer.VectorStore
import com.github.alphapaca.embeddingindexer.VoyageAIService
import com.github.alphapaca.webagent.api.models.AskResponse
import com.github.alphapaca.webagent.api.models.CodeSearchResultDto
import com.github.alphapaca.webagent.api.models.SourceReference
import com.github.alphapaca.webagent.api.models.SourceType
import com.github.alphapaca.webagent.data.service.ClaudeQAService
import com.github.alphapaca.webagent.data.service.DirectGitHubService
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File

class RAGRepository(
    private val vectorStore: VectorStore,
    private val voyageService: VoyageAIService,
    private val claudeService: ClaudeQAService,
    private val githubService: DirectGitHubService?,
    private val claudeMdContent: String,
    private val githubBaseUrl: String = "https://github.com/alphapaca/ClaudeClient",
    private val enableCodeSearch: Boolean = true,
    private val enableIssueSearch: Boolean = true,
) : Closeable {

    private val logger = LoggerFactory.getLogger(RAGRepository::class.java)

    /**
     * Answer a question using RAG.
     */
    suspend fun askQuestion(
        question: String,
        includeIssues: Boolean = true,
        maxCodeResults: Int = 10,
    ): AskResponse {
        val startTime = System.currentTimeMillis()
        val sources = mutableListOf<SourceReference>()

        logger.info("Processing question: ${question.take(100)}...")

        // 1. Try to generate embedding for the question and search code
        val codeResults = if (enableCodeSearch) {
            logger.debug("Starting VoyageAI embedding request...")
            try {
                withTimeoutOrNull(10_000) {
                    logger.debug("Inside timeout block, calling VoyageAI...")
                    val questionEmbedding = voyageService.getEmbeddings(listOf(question)).first()
                    logger.debug("Generated question embedding, searching vector store...")
                    vectorStore.searchSimilarCode(questionEmbedding, maxCodeResults)
                } ?: run {
                    logger.warn("VoyageAI embedding timed out after 10 seconds")
                    logger.info("Falling back to answer without code search...")
                    emptyList()
                }
            } catch (e: Exception) {
                logger.warn("Failed to generate embeddings (VoyageAI unavailable): ${e.message}")
                logger.info("Falling back to answer without code search...")
                emptyList()
            }
        } else {
            logger.info("Code search is disabled, answering with documentation only...")
            emptyList()
        }
        logger.debug("Found ${codeResults.size} relevant code chunks")

        // 3. Build code context and sources
        val codeContext = buildCodeContext(codeResults)
        codeResults.forEach { result ->
            sources.add(
                SourceReference(
                    type = SourceType.CODE,
                    title = "${result.chunkType.name}: ${result.name}",
                    location = "${result.filePath}:${result.startLine}-${result.endLine}",
                    url = "$githubBaseUrl/blob/main/${result.filePath}#L${result.startLine}-L${result.endLine}",
                    preview = result.content.take(150),
                )
            )
        }

        // 4. Add CLAUDE.md as documentation source
        sources.add(
            SourceReference(
                type = SourceType.DOCUMENTATION,
                title = "CLAUDE.md",
                location = "CLAUDE.md",
                url = "$githubBaseUrl/blob/main/CLAUDE.md",
            )
        )

        // 5. Search GitHub issues if enabled (using direct API)
        val issuesContext: String? = if (enableIssueSearch && includeIssues && githubService != null) {
            try {
                logger.info("Searching GitHub issues (direct API)...")
                val issues = githubService.searchIssues(question, limit = 5)
                logger.debug("GitHub search returned: ${issues?.take(50) ?: "null"}")
                if (!issues.isNullOrBlank()) {
                    sources.add(
                        SourceReference(
                            type = SourceType.GITHUB_ISSUE,
                            title = "Related GitHub Issues",
                            location = "GitHub Issues",
                            url = "$githubBaseUrl/issues",
                        )
                    )
                }
                logger.info("GitHub issues search completed, found: ${!issues.isNullOrBlank()}")
                issues
            } catch (e: Exception) {
                logger.error("Failed to search GitHub issues: ${e.message}", e)
                null
            }
        } else {
            if (!enableIssueSearch) {
                logger.info("Issue search is disabled")
            }
            null
        }

        // 6. Call Claude to generate the answer
        logger.info("Calling Claude to generate answer...")
        logger.debug("Context sizes: code=${codeContext.length}, doc=${claudeMdContent.length}, issues=${issuesContext?.length ?: 0}")
        val answer = try {
            claudeService.answer(
                question = question,
                codeContext = codeContext,
                documentationContext = claudeMdContent,
                issuesContext = issuesContext,
            )
        } catch (e: Exception) {
            logger.error("Claude API call failed: ${e.message}", e)
            throw e
        }
        logger.info("Claude returned answer with ${answer.length} chars")

        val processingTime = System.currentTimeMillis() - startTime
        logger.info("Question answered in ${processingTime}ms")

        return AskResponse(
            answer = answer,
            sources = sources,
            processingTimeMs = processingTime,
        )
    }

    /**
     * Search code directly without asking Claude.
     */
    suspend fun searchCode(query: String, limit: Int = 10): List<CodeSearchResultDto> {
        if (!enableCodeSearch) {
            logger.info("Code search is disabled")
            return emptyList()
        }
        val queryEmbedding = try {
            withTimeoutOrNull(10_000) {
                voyageService.getEmbeddings(listOf(query)).first()
            } ?: run {
                logger.warn("VoyageAI search embedding timed out")
                return emptyList()
            }
        } catch (e: Exception) {
            logger.warn("Failed to generate search embeddings: ${e.message}")
            return emptyList()
        }
        val results = vectorStore.searchSimilarCode(queryEmbedding, limit)

        return results.map { result ->
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
        }
    }

    private fun buildCodeContext(results: List<CodeSearchResult>): String {
        return buildString {
            results.forEachIndexed { index, result ->
                appendLine("--- Code Snippet ${index + 1} ---")
                appendLine("File: ${result.filePath}")
                appendLine("Type: ${result.chunkType.name}")
                appendLine("Name: ${result.name}")
                result.parentName?.let { appendLine("Parent: $it") }
                result.signature?.let { appendLine("Signature: $it") }
                appendLine("Lines: ${result.startLine}-${result.endLine}")
                appendLine()
                appendLine("```kotlin")
                appendLine(result.content)
                appendLine("```")
                appendLine()
            }
        }
    }

    fun getCodeChunkCount(): Int = vectorStore.getCodeChunkCount()

    override fun close() {
        vectorStore.close()
        voyageService.close()
        claudeService.close()
        githubService?.close()
    }

    companion object {
        fun loadClaudeMdContent(path: String): String {
            val file = File(path)
            return if (file.exists()) {
                file.readText()
            } else {
                // Try relative to project root
                val projectFile = File(".", "CLAUDE.md")
                if (projectFile.exists()) {
                    projectFile.readText()
                } else {
                    "# CLAUDE.md not found\nNo project documentation available."
                }
            }
        }
    }
}
