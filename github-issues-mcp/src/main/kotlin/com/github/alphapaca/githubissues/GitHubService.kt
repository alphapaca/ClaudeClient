package com.github.alphapaca.githubissues

import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Exception thrown when GitHub API returns an error response.
 */
class GitHubApiException(
    val statusCode: Int,
    message: String,
    val responseBody: String? = null,
) : Exception("GitHub API error ($statusCode): $message")

class GitHubService(
    private val token: String?,
    private val owner: String,
    private val repo: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseUrl = "https://api.github.com"

    /**
     * Search issues in the repository.
     * Uses GitHub's search API to find issues matching the query.
     */
    suspend fun searchIssues(
        query: String,
        state: String = "all",
        labels: List<String> = emptyList(),
        limit: Int = 10,
    ): List<GitHubIssue> {
        System.err.println("[GitHubService] searchIssues called")

        // Build search query: repo:owner/repo + is:issue + user query + state filter
        val searchQuery = buildString {
            append("repo:$owner/$repo is:issue ")
            append(query)
            if (state != "all") {
                append(" state:$state")
            }
            labels.forEach { label ->
                append(" label:\"$label\"")
            }
        }
        System.err.println("[GitHubService] searchQuery: $searchQuery")

        val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
        val url = URL("$baseUrl/search/issues?q=$encodedQuery&per_page=${limit.coerceIn(1, 100)}&sort=updated&order=desc")
        System.err.println("[GitHubService] URL: $url")

        System.err.println("[GitHubService] Calling executeGet...")
        val responseBody = executeGet(url)
        System.err.println("[GitHubService] Got response: ${responseBody.length} chars")

        System.err.println("[GitHubService] Parsing response...")
        val response = json.decodeFromString(GitHubSearchResponse.serializer(), responseBody)
        System.err.println("[GitHubService] Found ${response.items.size} items")

        return response.items
    }

    /**
     * Get a specific issue by number.
     */
    suspend fun getIssue(issueNumber: Int): GitHubIssue {
        val url = URL("$baseUrl/repos/$owner/$repo/issues/$issueNumber")
        val responseBody = executeGet(url)
        return json.decodeFromString(GitHubIssue.serializer(), responseBody)
    }

    /**
     * List issues in the repository.
     */
    suspend fun listIssues(
        state: String = "all",
        labels: List<String> = emptyList(),
        limit: Int = 10,
    ): List<GitHubIssue> {
        val labelsParam = if (labels.isNotEmpty()) "&labels=${labels.joinToString(",")}" else ""
        val url = URL("$baseUrl/repos/$owner/$repo/issues?state=$state&per_page=${limit.coerceIn(1, 100)}&sort=updated&direction=desc$labelsParam")

        val responseBody = executeGet(url)
        return json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GitHubIssue.serializer()), responseBody)
    }

    /**
     * Create a new issue in the repository.
     */
    suspend fun createIssue(
        title: String,
        body: String? = null,
        labels: List<String> = emptyList(),
        assignees: List<String> = emptyList(),
    ): GitHubIssue {
        if (token.isNullOrBlank()) {
            throw GitHubApiException(401, "GITHUB_TOKEN is required to create issues")
        }

        val url = URL("$baseUrl/repos/$owner/$repo/issues")
        val requestBody = buildString {
            append("{")
            append("\"title\":${escapeJsonString(title)}")
            if (!body.isNullOrBlank()) {
                append(",\"body\":${escapeJsonString(body)}")
            }
            if (labels.isNotEmpty()) {
                append(",\"labels\":[${labels.joinToString(",") { escapeJsonString(it) }}]")
            }
            if (assignees.isNotEmpty()) {
                append(",\"assignees\":[${assignees.joinToString(",") { escapeJsonString(it) }}]")
            }
            append("}")
        }

        System.err.println("[GitHubService] Creating issue: $title")
        val responseBody = executePost(url, requestBody)
        return json.decodeFromString(GitHubIssue.serializer(), responseBody)
    }

    private fun executeGet(url: URL): String {
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000

        val responseCode = connection.responseCode

        if (responseCode != 200) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw GitHubApiException(
                statusCode = responseCode,
                message = getErrorMessage(responseCode, errorBody),
                responseBody = errorBody,
            )
        }

        return connection.inputStream.bufferedReader().readText()
    }

    private fun executePost(url: URL, body: String): String {
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        connection.doOutput = true

        connection.outputStream.bufferedWriter().use { it.write(body) }

        val responseCode = connection.responseCode

        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw GitHubApiException(
                statusCode = responseCode,
                message = getErrorMessage(responseCode, errorBody),
                responseBody = errorBody,
            )
        }

        return connection.inputStream.bufferedReader().readText()
    }

    private fun getErrorMessage(statusCode: Int, body: String): String {
        return when (statusCode) {
            401 -> "Unauthorized - check GITHUB_TOKEN"
            403 -> "Forbidden - rate limited or insufficient permissions"
            404 -> "Repository or issue not found"
            422 -> "Invalid request: $body"
            else -> "HTTP $statusCode"
        }
    }

    private fun escapeJsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    fun close() {
        // Nothing to close with HttpURLConnection
    }
}
