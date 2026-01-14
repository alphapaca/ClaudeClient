package com.github.alphapaca.reviewagent.service

import com.github.alphapaca.embeddingindexer.CodeSearchResult
import com.github.alphapaca.embeddingindexer.VectorStore
import com.github.alphapaca.embeddingindexer.VoyageAIService
import com.github.alphapaca.reviewagent.model.DiffHunk
import com.github.alphapaca.reviewagent.model.DiffLineType
import com.github.alphapaca.reviewagent.model.FileDiff
import com.github.alphapaca.reviewagent.model.ReviewComment
import org.slf4j.LoggerFactory

class ReviewService(
    private val vectorStore: VectorStore,
    private val voyageService: VoyageAIService,
    private val claudeService: ClaudeReviewService,
    private val claudeMdContent: String?
) {
    private val logger = LoggerFactory.getLogger(ReviewService::class.java)

    /**
     * Review all changed files and generate comments.
     */
    suspend fun reviewChanges(fileDiffs: List<FileDiff>): List<ReviewComment> {
        val allComments = mutableListOf<ReviewComment>()

        for (fileDiff in fileDiffs) {
            if (!isReviewableFile(fileDiff.filePath)) {
                logger.info("Skipping non-reviewable file: ${fileDiff.filePath}")
                continue
            }

            logger.info("Reviewing: ${fileDiff.filePath}")

            // For each hunk, search for relevant context and generate review
            for (hunk in fileDiff.hunks) {
                val comments = reviewHunk(fileDiff.filePath, hunk)
                allComments.addAll(comments)
            }
        }

        return allComments
    }

    private suspend fun reviewHunk(
        filePath: String,
        hunk: DiffHunk
    ): List<ReviewComment> {
        // 1. Build search query from the changed code
        val changedCode = hunk.lines
            .filter { it.type == DiffLineType.ADDITION }
            .joinToString("\n") { it.content }

        if (changedCode.isBlank()) {
            return emptyList() // Only deletions, skip
        }

        // 2. Search for relevant context using RAG
        val context = try {
            val queryEmbedding = voyageService.getEmbeddings(
                listOf(changedCode),
                VoyageAIService.CODE_EMBEDDING_MODEL
            ).first()

            val relevantChunks = vectorStore.searchSimilarCode(queryEmbedding, limit = 5)
            buildContext(relevantChunks)
        } catch (e: Exception) {
            logger.warn("Failed to get RAG context: ${e.message}")
            ""
        }

        // 3. Call Claude to review
        val reviewResult = claudeService.reviewCode(
            filePath = filePath,
            hunk = hunk,
            context = context,
            claudeMdInstructions = claudeMdContent
        )

        return reviewResult.comments
    }

    private fun buildContext(chunks: List<CodeSearchResult>): String {
        if (chunks.isEmpty()) return ""

        return buildString {
            appendLine("## Relevant Code Context")
            appendLine()
            for ((index, chunk) in chunks.withIndex()) {
                appendLine("### Context ${index + 1}: ${chunk.name} (${chunk.filePath}:${chunk.startLine})")
                appendLine("```kotlin")
                appendLine(chunk.content)
                appendLine("```")
                appendLine()
            }
        }
    }

    private fun isReviewableFile(filePath: String): Boolean {
        val reviewableExtensions = setOf("kt", "java", "ts", "tsx", "js", "jsx", "py", "go", "rs", "swift")
        val extension = filePath.substringAfterLast('.', "")

        // Skip test files, generated code, config files
        val skipPatterns = listOf(
            "/build/", "/generated/", "/.gradle/", "/.idea/",
            "Test.kt", "Tests.kt", "Spec.kt", "_test.go",
            ".lock", ".json", ".yml", ".yaml", ".xml", ".md",
            "build.gradle", "settings.gradle", "gradle.properties"
        )

        return extension in reviewableExtensions &&
                skipPatterns.none { filePath.contains(it) }
    }
}
