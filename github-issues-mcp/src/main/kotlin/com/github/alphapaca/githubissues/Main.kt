package com.github.alphapaca.githubissues

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Properties

fun main(): Unit = runBlocking {
    val token = loadEnvParameter("GITHUB_TOKEN")
    val owner = loadEnvParameter("GITHUB_OWNER") ?: "anthropics"
    val repo = loadEnvParameter("GITHUB_REPO") ?: "claude-code"

    val gitHubService = GitHubService(token, owner, repo)

    System.err.println("GitHub Issues MCP Server starting...")
    System.err.println("Repository: $owner/$repo")

    val server = Server(
        serverInfo = Implementation(
            name = "github-issues-mcp",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            )
        )
    )

    // Tool: search_github_issues
    server.addTool(
        name = "search_github_issues",
        description = """Search GitHub issues in the $owner/$repo repository.
            |Use this to find related issues, bugs, feature requests, or discussions.
            |Returns issue titles, descriptions, labels, and status.
            |
            |Examples:
            |- Find bugs: query="bug crash" state="open"
            |- Find feature requests: query="feature request" labels=["enhancement"]
            |- Search specific topic: query="MCP integration"
        """.trimMargin(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Search query for issues (searches title and body)"))
                })
                put("state", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("open"))
                        add(JsonPrimitive("closed"))
                        add(JsonPrimitive("all"))
                    })
                    put("description", JsonPrimitive("Issue state filter (default: all)"))
                })
                put("labels", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                    put("description", JsonPrimitive("Filter by labels (e.g., [\"bug\", \"enhancement\"])"))
                })
                put("limit", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("Max results to return (default: 10, max: 30)"))
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

        val state = request.arguments?.get("state")?.jsonPrimitive?.content ?: "all"
        val labels = request.arguments?.get("labels")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val limit = request.arguments?.get("limit")?.jsonPrimitive?.int?.coerceIn(1, 30) ?: 10

        try {
            System.err.println("Searching issues: query='$query', state=$state, labels=$labels, limit=$limit")
            System.err.println("About to call gitHubService.searchIssues...")
            System.err.flush()

            val issues = gitHubService.searchIssues(query, state, labels, limit)

            System.err.println("searchIssues returned ${issues.size} results")
            System.err.flush()

            if (issues.isEmpty()) {
                System.err.println("No issues found, creating CallToolResult...")
                System.err.flush()
                val result = CallToolResult(
                    content = listOf(TextContent(text = "No issues found matching: $query"))
                )
                System.err.println("CallToolResult created: $result")
                System.err.flush()
                return@addTool result
            }

            System.err.println("Formatting issues...")
            val formatted = formatIssues(issues)
            System.err.println("Returning CallToolResult with ${formatted.length} chars")
            System.err.flush()

            CallToolResult(
                content = listOf(TextContent(text = formatted))
            )
        } catch (e: Exception) {
            System.err.println("Error searching issues: ${e.message}")
            e.printStackTrace(System.err)
            System.err.flush()
            CallToolResult(
                content = listOf(TextContent(text = "Error searching issues: ${e.message}")),
                isError = true
            )
        }
    }

    // Tool: get_github_issue
    server.addTool(
        name = "get_github_issue",
        description = "Get details of a specific GitHub issue by number.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("issue_number", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("The issue number to retrieve"))
                })
            },
            required = listOf("issue_number")
        )
    ) { request ->
        val issueNumber = request.arguments?.get("issue_number")?.jsonPrimitive?.int
            ?: return@addTool CallToolResult(
                content = listOf(TextContent(text = "Error: 'issue_number' parameter is required")),
                isError = true
            )

        try {
            System.err.println("Fetching issue #$issueNumber")
            val issue = gitHubService.getIssue(issueNumber)
            val formatted = formatIssueDetail(issue)
            CallToolResult(
                content = listOf(TextContent(text = formatted))
            )
        } catch (e: Exception) {
            System.err.println("Error fetching issue: ${e.message}")
            CallToolResult(
                content = listOf(TextContent(text = "Error fetching issue #$issueNumber: ${e.message}")),
                isError = true
            )
        }
    }

    // Tool: list_github_issues
    server.addTool(
        name = "list_github_issues",
        description = "List recent issues in the repository, optionally filtered by state and labels.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("state", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("open"))
                        add(JsonPrimitive("closed"))
                        add(JsonPrimitive("all"))
                    })
                    put("description", JsonPrimitive("Issue state filter (default: open)"))
                })
                put("labels", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                    put("description", JsonPrimitive("Filter by labels"))
                })
                put("limit", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("Max results (default: 10, max: 30)"))
                })
            },
            required = emptyList()
        )
    ) { request ->
        val state = request.arguments?.get("state")?.jsonPrimitive?.content ?: "open"
        val labels = request.arguments?.get("labels")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val limit = request.arguments?.get("limit")?.jsonPrimitive?.int?.coerceIn(1, 30) ?: 10

        try {
            System.err.println("Listing issues: state=$state, labels=$labels, limit=$limit")
            val issues = gitHubService.listIssues(state, labels, limit)

            if (issues.isEmpty()) {
                return@addTool CallToolResult(
                    content = listOf(TextContent(text = "No issues found with state=$state"))
                )
            }

            val formatted = formatIssues(issues)
            CallToolResult(
                content = listOf(TextContent(text = formatted))
            )
        } catch (e: Exception) {
            System.err.println("Error listing issues: ${e.message}")
            CallToolResult(
                content = listOf(TextContent(text = "Error listing issues: ${e.message}")),
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
        gitHubService.close()
        done.complete()
    }

    server.connect(transport)
    System.err.println("GitHub Issues MCP Server ready")

    done.join()
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

private fun formatIssueDetail(issue: GitHubIssue): String {
    return buildString {
        appendLine("=== Issue #${issue.number}: ${issue.title} ===")
        appendLine()
        appendLine("State: ${issue.state.uppercase()}")
        appendLine("URL: ${issue.htmlUrl}")
        issue.user?.let { appendLine("Author: ${it.login}") }
        if (issue.labels.isNotEmpty()) {
            appendLine("Labels: ${issue.labels.joinToString(", ") { it.name }}")
        }
        appendLine("Created: ${issue.createdAt}")
        appendLine("Updated: ${issue.updatedAt}")
        issue.closedAt?.let { appendLine("Closed: $it") }
        appendLine("Comments: ${issue.comments}")
        appendLine()
        appendLine("--- Description ---")
        appendLine(issue.body ?: "(No description)")
    }
}

/**
 * Load a parameter from environment variables or local.properties file.
 */
fun loadEnvParameter(key: String): String? {
    // First try environment variable
    System.getenv(key)?.let { return it }

    // Try local.properties in current directory
    val localPropsFile = File("local.properties")
    if (localPropsFile.exists()) {
        val props = Properties()
        localPropsFile.inputStream().use { props.load(it) }
        props.getProperty(key)?.let { return it }
    }

    // Try local.properties in home directory
    val homePropsFile = File(System.getProperty("user.home"), ".github-issues-mcp/config.properties")
    if (homePropsFile.exists()) {
        val props = Properties()
        homePropsFile.inputStream().use { props.load(it) }
        props.getProperty(key)?.let { return it }
    }

    return null
}
