package com.github.alphapaca.webagent.mcp

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File

class GitHubIssuesMCPService(
    private val jarPath: String,
    private val githubToken: String?,
    private val githubOwner: String,
    private val githubRepo: String,
) : Closeable {

    private val logger = LoggerFactory.getLogger(GitHubIssuesMCPService::class.java)
    private var process: Process? = null
    private var toolRegistry: ToolRegistry? = null

    suspend fun start(): ToolRegistry {
        logger.info("Starting GitHub Issues MCP server...")
        logger.info("JAR path: $jarPath")
        logger.info("Repository: $githubOwner/$githubRepo")

        val jarFile = File(jarPath)
        if (!jarFile.exists()) {
            throw IllegalStateException("GitHub Issues MCP JAR not found at: $jarPath")
        }

        val env = mutableMapOf<String, String>().apply {
            githubToken?.let { put("GITHUB_TOKEN", it) }
            put("GITHUB_OWNER", githubOwner)
            put("GITHUB_REPO", githubRepo)
        }

        val processBuilder = ProcessBuilder(
            "java", "-jar", jarPath
        ).apply {
            environment().putAll(env)
            redirectErrorStream(false)
        }

        process = processBuilder.start()

        // Check if process started successfully
        Thread.sleep(500)
        if (process?.isAlive != true) {
            val exitCode = process?.exitValue() ?: -1
            val errorOutput = process?.errorStream?.bufferedReader()?.readText() ?: ""
            throw IllegalStateException("MCP server failed to start (exit code: $exitCode): $errorOutput")
        }

        // Read stderr in background for debugging
        Thread {
            process?.errorStream?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    logger.debug("MCP stderr: $line")
                }
            }
        }.start()

        logger.info("MCP server process started, creating transport...")

        val transport = McpToolRegistryProvider.defaultStdioTransport(process!!)

        logger.info("Creating tool registry from transport...")

        toolRegistry = McpToolRegistryProvider.fromTransport(
            transport = transport,
            name = "web-agent-backend",
            version = "1.0.0"
        )

        logger.info("GitHub Issues MCP server started successfully")
        return toolRegistry!!
    }

    override fun close() {
        logger.info("Stopping GitHub Issues MCP server...")
        process?.let {
            it.destroyForcibly()
            it.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
        }
        process = null
        toolRegistry = null
        logger.info("GitHub Issues MCP server stopped")
    }
}
