package com.github.alphapaca.webagent.data.service

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.slf4j.LoggerFactory
import java.io.Closeable

/**
 * Client for connecting to the github-issues-mcp server.
 */
class GitHubMCPClient(
    private val mcpJarPath: String,
    private val githubToken: String?,
    private val githubOwner: String,
    private val githubRepo: String,
) : Closeable {
    private val logger = LoggerFactory.getLogger(GitHubMCPClient::class.java)

    private var client: Client? = null
    private var process: Process? = null
    private var isConnected = false

    /**
     * Connect to the GitHub Issues MCP server.
     */
    suspend fun connect() {
        logger.info("connect() called, isConnected=$isConnected")
        if (isConnected) return

        withContext(Dispatchers.IO) {
            logger.info("Starting MCP process: java -jar $mcpJarPath")

            val env = buildMap {
                put("GITHUB_OWNER", githubOwner)
                put("GITHUB_REPO", githubRepo)
                if (!githubToken.isNullOrBlank()) {
                    put("GITHUB_TOKEN", githubToken)
                }
            }
            logger.info("Environment vars: GITHUB_OWNER=$githubOwner, GITHUB_REPO=$githubRepo, GITHUB_TOKEN=${if (githubToken.isNullOrBlank()) "NOT SET" else "SET"}")

            val processBuilder = ProcessBuilder("java", "-jar", mcpJarPath)
                .apply {
                    environment().putAll(env)
                    redirectErrorStream(false)
                }

            process = processBuilder.start()
            logger.info("MCP process started, PID=${process!!.pid()}")

            // Read stderr in background for debugging
            Thread {
                process!!.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        logger.debug("MCP stderr: $line")
                    }
                }
            }.start()

            logger.info("Creating StdioClientTransport...")
            val transport = StdioClientTransport(
                input = process!!.inputStream.asSource().buffered(),
                output = process!!.outputStream.asSink().buffered(),
            )

            logger.info("Creating MCP Client...")
            client = Client(
                clientInfo = Implementation(name = "web-agent-backend", version = "1.0.0")
            )

            logger.info("Calling client.connect(transport)...")
            client!!.connect(transport)
            isConnected = true
            logger.info("Connected to GitHub Issues MCP server successfully!")
        }
    }

    /**
     * Search GitHub issues related to a query.
     */
    suspend fun searchIssues(query: String, limit: Int = 5): String? {
        logger.info("searchIssues called: query='$query', limit=$limit, isConnected=$isConnected")

        if (!isConnected) {
            logger.warn("Not connected to GitHub Issues MCP server")
            return null
        }

        return try {
            logger.info("Calling MCP callTool...")
            val arguments = mapOf(
                "query" to query,
                "limit" to limit,
                "state" to "all"
            )
            logger.info("Arguments: $arguments")

            val result = client!!.callTool(
                name = "search_github_issues",
                arguments = arguments
            )
            logger.info("callTool returned! Result content size: ${result.content.size}")

            val textContent = result.content
                .filterIsInstance<TextContent>()
                .joinToString("\n") { it.text ?: "" }
                .takeIf { it.isNotBlank() }

            logger.info("Extracted text content length: ${textContent?.length ?: 0}")
            logger.info("searchIssues returning result")
            textContent
        } catch (e: Exception) {
            logger.error("Error searching GitHub issues: ${e.message}", e)
            null
        }
    }

    override fun close() {
        try {
            process?.destroyForcibly()
            process?.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.warn("Error closing GitHub MCP client: ${e.message}")
        }
        isConnected = false
    }
}
