package com.github.alphapaca.webagent.data.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ClaudeRequest(
    val model: String = "claude-sonnet-4-20250514",
    val messages: List<ClaudeMessage>,
    val system: String? = null,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
)

@Serializable
data class ClaudeResponse(
    val id: String,
    val content: List<ClaudeContentBlock>,
    val model: String,
    @SerialName("stop_reason") val stopReason: String?,
    val usage: ClaudeUsage?,
)

@Serializable
data class ClaudeContentBlock(
    val type: String,
    val text: String? = null,
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int,
)

class ClaudeQAService(
    private val apiKey: String,
) : Closeable {
    private val logger = LoggerFactory.getLogger(ClaudeQAService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true  // Include default values in serialization
    }

    private val baseUrl = "https://api.anthropic.com/v1"

    /**
     * Ask a question with the provided context.
     */
    suspend fun answer(
        question: String,
        codeContext: String,
        documentationContext: String,
        issuesContext: String?,
    ): String {
        val systemPrompt = buildString {
            appendLine("You are a helpful assistant that answers questions about the ClaudeClient codebase.")
            appendLine("You have access to the following context to help answer questions:")
            appendLine()
            appendLine("=== PROJECT DOCUMENTATION (CLAUDE.md) ===")
            appendLine(documentationContext)
            appendLine()
            appendLine("=== RELEVANT CODE SNIPPETS ===")
            appendLine(codeContext)
            if (!issuesContext.isNullOrBlank()) {
                appendLine()
                appendLine("=== RELATED GITHUB ISSUES ===")
                appendLine(issuesContext)
            }
            appendLine()
            appendLine("Guidelines:")
            appendLine("- Base your answers on the provided context")
            appendLine("- Reference specific files and line numbers when discussing code")
            appendLine("- If the context doesn't contain enough information, say so")
            appendLine("- Use markdown formatting for code blocks and structure")
            appendLine("- Be concise but thorough")
        }

        val request = ClaudeRequest(
            messages = listOf(
                ClaudeMessage(role = "user", content = question)
            ),
            system = systemPrompt,
        )

        logger.info("Sending question to Claude: ${question.take(100)}...")

        val response: ClaudeResponse = try {
            // Use Java's HttpURLConnection for reliability
            val url = URL("$baseUrl/messages")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("anthropic-version", "2023-06-01")
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.doOutput = true

            val requestJson = json.encodeToString(ClaudeRequest.serializer(), request)
            logger.debug("Request body: ${requestJson.take(500)}...")

            connection.outputStream.use { os ->
                os.write(requestJson.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            logger.debug("Response code: $responseCode")

            if (responseCode != 200) {
                val errorResponse = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                logger.error("Claude API error ($responseCode): $errorResponse")
                throw RuntimeException("Claude API error ($responseCode): $errorResponse")
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            logger.debug("Response body: ${responseBody.take(500)}...")

            json.decodeFromString(ClaudeResponse.serializer(), responseBody)
        } catch (e: Exception) {
            logger.error("Claude API error: ${e.message}", e)
            throw RuntimeException("Failed to get answer from Claude: ${e.message}", e)
        }

        logger.info("Received response from Claude (${response.usage?.outputTokens} tokens)")

        return response.content
            .filter { it.type == "text" }
            .mapNotNull { it.text }
            .joinToString("\n")
    }

    override fun close() {
        // Nothing to close with HttpURLConnection
    }
}
