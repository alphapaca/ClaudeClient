package com.github.alphapaca.webagent.data.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class GitHubIssue(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val comments: Int,
    val labels: List<GitHubLabel> = emptyList(),
    val user: GitHubUser? = null,
)

@Serializable
data class GitHubLabel(
    val name: String,
)

@Serializable
data class GitHubUser(
    val login: String,
)

@Serializable
data class GitHubSearchResponse(
    @SerialName("total_count") val totalCount: Int,
    val items: List<GitHubIssue>,
)

/**
 * Direct GitHub API client without MCP.
 */
class DirectGitHubService(
    private val token: String?,
    private val owner: String,
    private val repo: String,
) : Closeable {
    private val logger = LoggerFactory.getLogger(DirectGitHubService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseUrl = "https://api.github.com"

    // Common stop words to filter out from search
    private val stopWords = setOf(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could", "should",
        "may", "might", "must", "shall", "can", "need", "dare", "ought", "used",
        "to", "of", "in", "for", "on", "with", "at", "by", "from", "as", "into",
        "about", "like", "through", "after", "over", "between", "out", "against",
        "during", "without", "before", "under", "around", "among", "this", "that",
        "these", "those", "i", "you", "he", "she", "it", "we", "they", "what",
        "which", "who", "whom", "any", "all", "each", "every", "both", "few",
        "more", "most", "other", "some", "such", "no", "not", "only", "own",
        "same", "so", "than", "too", "very", "just", "issue", "issues", "problem",
        "problems", "bug", "bugs", "error", "errors", "question", "questions",
        "there", "how", "why", "when", "where", "related", "regarding", "concerning"
    )

    /**
     * Extract meaningful keywords from a query.
     */
    private fun extractKeywords(query: String): List<String> {
        return query.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
            .take(5) // Limit to 5 keywords
    }

    /**
     * Search issues in the repository.
     */
    fun searchIssues(query: String, limit: Int = 5): String? {
        logger.info("searchIssues called: query='$query', limit=$limit")

        return try {
            // Extract keywords from query for better search results
            val keywords = extractKeywords(query)
            logger.debug("Extracted keywords: $keywords")

            // Build search query: repo:owner/repo + is:issue + keywords
            val keywordQuery = keywords.joinToString(" ")
            val searchQuery = "repo:$owner/$repo is:issue $keywordQuery"
            val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
            val url = URL("$baseUrl/search/issues?q=$encodedQuery&per_page=${limit.coerceIn(1, 30)}&sort=updated&order=desc")

            logger.debug("Request URL: $url")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val responseCode = connection.responseCode
            logger.debug("Response code: $responseCode")

            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                logger.warn("GitHub API error ($responseCode): $errorBody")
                return null
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            val response = json.decodeFromString(GitHubSearchResponse.serializer(), responseBody)

            logger.info("Found ${response.items.size} issues")

            if (response.items.isEmpty()) {
                return "No issues found matching: $query"
            }

            formatIssues(response.items)
        } catch (e: Exception) {
            logger.error("Error searching GitHub issues: ${e.message}", e)
            null
        }
    }

    private fun formatIssues(issues: List<GitHubIssue>): String {
        return buildString {
            appendLine("Found ${issues.size} issue(s):")
            appendLine()
            issues.forEach { issue ->
                appendLine("--- Issue #${issue.number}: ${issue.title} ---")
                appendLine("State: ${issue.state.uppercase()}")
                appendLine("URL: ${issue.htmlUrl}")
                if (issue.labels.isNotEmpty()) {
                    appendLine("Labels: ${issue.labels.joinToString(", ") { it.name }}")
                }
                appendLine("Created: ${issue.createdAt}")
                appendLine("Comments: ${issue.comments}")
                if (!issue.body.isNullOrBlank()) {
                    val preview = issue.body.take(300).let {
                        if (issue.body.length > 300) "$it..." else it
                    }
                    appendLine("Preview: $preview")
                }
                appendLine()
            }
        }
    }

    override fun close() {
        // Nothing to close
    }
}
