@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")

package com.github.alphapaca.reviewagent.service

import com.github.alphapaca.reviewagent.model.ClaudeReviewOutput
import com.github.alphapaca.reviewagent.model.DiffHunk
import com.github.alphapaca.reviewagent.model.DiffLineType
import com.github.alphapaca.reviewagent.model.ReviewComment
import com.github.alphapaca.reviewagent.model.ReviewResult
import com.github.alphapaca.reviewagent.model.Severity
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ClaudeReviewService(private val apiKey: String) {

    private val logger = LoggerFactory.getLogger(ClaudeReviewService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        engine { requestTimeout = 120_000 }
    }

    /**
     * Review a code hunk and generate comments.
     */
    suspend fun reviewCode(
        filePath: String,
        hunk: DiffHunk,
        context: String,
        claudeMdInstructions: String?
    ): ReviewResult {
        val systemPrompt = buildSystemPrompt(claudeMdInstructions)
        val userPrompt = buildUserPrompt(filePath, hunk, context)

        val request = ClaudeRequest(
            model = "claude-sonnet-4-5-20250514",
            maxTokens = 4096,
            system = systemPrompt,
            messages = listOf(
                ClaudeMessage(role = "user", content = userPrompt)
            )
        )

        return executeWithRetry(request, filePath, hunk)
    }

    private suspend fun executeWithRetry(
        request: ClaudeRequest,
        filePath: String,
        hunk: DiffHunk,
        maxRetries: Int = 3
    ): ReviewResult {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = client.post("https://api.anthropic.com/v1/messages") {
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                val responseText = response.bodyAsText()

                if (!response.status.isSuccess()) {
                    if (response.status.value == 429) {
                        logger.warn("Rate limited, waiting 60s before retry ${attempt + 1}/$maxRetries")
                        delay(60_000)
                        return@repeat
                    }
                    logger.error("Claude API error: ${response.status} - $responseText")
                    return ReviewResult(comments = emptyList())
                }

                val claudeResponse: ClaudeResponse = json.decodeFromString(responseText)
                return parseReviewResponse(claudeResponse, filePath, hunk)
            } catch (e: Exception) {
                logger.warn("Request failed (attempt ${attempt + 1}/$maxRetries): ${e.message}")
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay(1000L * (attempt + 1))
                }
            }
        }

        logger.error("All retries failed", lastException)
        return ReviewResult(comments = emptyList())
    }

    private fun buildSystemPrompt(claudeMdInstructions: String?): String {
        return buildString {
            appendLine("""
                You are a senior software engineer conducting a code review.
                Your task is to review code changes and provide actionable feedback.

                ## Guidelines
                - Focus on bugs, security issues, and significant code quality problems
                - Be specific and constructive
                - Reference the line number for each comment
                - Don't comment on style preferences unless they cause issues
                - If code looks good, don't force comments

                ## Response Format
                Respond with a JSON object containing an array of comments:
                {
                    "comments": [
                        {
                            "line": <line_number>,
                            "severity": "issue" | "suggestion" | "note",
                            "body": "<your comment>"
                        }
                    ]
                }

                Line numbers must reference the NEW file (additions), not the old file.
                If there are no issues, respond with: {"comments": []}
            """.trimIndent())

            if (claudeMdInstructions != null) {
                appendLine()
                appendLine("## Project-Specific Instructions (from CLAUDE.md)")
                appendLine(claudeMdInstructions)
            }
        }
    }

    private fun buildUserPrompt(
        filePath: String,
        hunk: DiffHunk,
        context: String
    ): String {
        return buildString {
            appendLine("## File: $filePath")
            appendLine()

            if (context.isNotBlank()) {
                appendLine(context)
                appendLine()
            }

            appendLine("## Code Changes to Review")
            appendLine("```diff")
            appendLine(hunk.header)
            for (line in hunk.lines) {
                val prefix = when (line.type) {
                    DiffLineType.ADDITION -> "+"
                    DiffLineType.DELETION -> "-"
                    DiffLineType.CONTEXT -> " "
                }
                val lineRef = when (line.type) {
                    DiffLineType.ADDITION -> "[L${line.newLineNumber}]"
                    DiffLineType.DELETION -> "[old]"
                    DiffLineType.CONTEXT -> "[L${line.newLineNumber}]"
                }
                appendLine("$prefix $lineRef ${line.content}")
            }
            appendLine("```")
            appendLine()
            appendLine("Please review the above changes and provide feedback in JSON format.")
        }
    }

    private fun parseReviewResponse(
        response: ClaudeResponse,
        filePath: String,
        hunk: DiffHunk
    ): ReviewResult {
        val textContent = response.content.firstOrNull { it.type == "text" }?.text
            ?: return ReviewResult(comments = emptyList())

        // Extract JSON from response (may be wrapped in markdown code block)
        val jsonContent = textContent
            .replace("```json", "")
            .replace("```", "")
            .trim()

        return try {
            val parsed: ClaudeReviewOutput = json.decodeFromString(jsonContent)
            ReviewResult(
                comments = parsed.comments.mapNotNull { c ->
                    try {
                        ReviewComment(
                            filePath = filePath,
                            line = c.line,
                            severity = Severity.valueOf(c.severity.uppercase()),
                            body = c.body
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to parse comment: ${e.message}")
                        null
                    }
                }
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse Claude response: ${e.message}")
            logger.debug("Response was: $textContent")
            ReviewResult(comments = emptyList())
        }
    }

    fun close() {
        client.close()
    }
}

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>
)

@Serializable
private data class ClaudeMessage(
    val role: String,
    val content: String
)

@Serializable
private data class ClaudeResponse(
    val id: String,
    val content: List<ClaudeContentBlock>,
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
private data class ClaudeContentBlock(
    val type: String,
    val text: String? = null
)
