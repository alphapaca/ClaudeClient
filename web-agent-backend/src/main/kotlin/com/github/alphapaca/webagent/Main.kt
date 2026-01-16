package com.github.alphapaca.webagent

import ai.koog.agents.core.tools.ToolRegistry
import com.github.alphapaca.embeddingindexer.VectorStore
import com.github.alphapaca.embeddingindexer.VoyageAIService
import com.github.alphapaca.webagent.agent.CodebaseQAAgent
import com.github.alphapaca.webagent.api.configureRouting
import com.github.alphapaca.webagent.config.AppConfig
import com.github.alphapaca.webagent.data.service.CodeIndexerService
import com.github.alphapaca.webagent.mcp.GitHubIssuesMCPService
import java.io.File
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WebAgentServer")

fun main() {
    logger.info("Starting Web Agent Backend Server (Koog Edition)...")

    // Load configuration
    val config = try {
        AppConfig.load()
    } catch (e: Exception) {
        logger.error("Failed to load configuration: ${e.message}")
        logger.error("Required environment variables: ANTHROPIC_API_KEY, VOYAGE_API_KEY")
        logger.error("Optional: GITHUB_TOKEN, VECTOR_DB_PATH, CLAUDE_MD_PATH, PROJECT_PATH")
        throw e
    }

    logger.info("Configuration loaded:")
    logger.info("  Port: ${config.port}")
    logger.info("  LLM Model: ${config.llmModel}")
    logger.info("  Vector DB: ${config.vectorDbPath}")
    logger.info("  Project path: ${config.projectPath}")
    logger.info("  CLAUDE.md: ${config.claudeMdPath}")
    logger.info("  GitHub: ${config.githubOwner}/${config.githubRepo}")
    logger.info("  Auto-index: ${config.autoIndex}")

    // Initialize code search services only if enabled
    var vectorStore: VectorStore? = null
    var voyageService: VoyageAIService? = null

    if (config.enableCodeSearch) {
        // Check if database exists and index if needed
        val indexerService = CodeIndexerService(config.voyageApiKey)

        if (!indexerService.isDatabaseReady(config.vectorDbPath)) {
            if (config.autoIndex) {
                logger.info("Database not found or empty, starting indexing...")
                logger.info("This may take a few minutes on first run...")

                val result = runBlocking {
                    indexerService.indexCodebase(
                        projectPath = config.projectPath,
                        dbPath = config.vectorDbPath,
                        onProgress = { msg -> logger.info("Indexing: $msg") }
                    )
                }
                when (result) {
                    is CodeIndexerService.IndexResult.Success -> {
                        logger.info("Indexing complete: ${result.filesIndexed} files, ${result.chunksCreated} chunks")
                    }
                    is CodeIndexerService.IndexResult.Failure -> {
                        logger.error("Indexing failed: ${result.error}")
                        logger.error("Please ensure PROJECT_PATH points to a valid Kotlin project")
                        throw RuntimeException("Failed to index codebase: ${result.error}")
                    }
                }
            } else {
                logger.warn("Database not found and AUTO_INDEX is disabled")
                logger.warn("Code search will be disabled")
            }
        }

        // Only initialize if database is ready
        if (indexerService.isDatabaseReady(config.vectorDbPath)) {
            vectorStore = VectorStore(config.vectorDbPath)
            logger.info("Vector store initialized with ${vectorStore.getCodeChunkCount()} code chunks")
            voyageService = VoyageAIService(config.voyageApiKey)
        }
    } else {
        logger.info("Code search is disabled (ENABLE_CODE_SEARCH=false)")
    }

    // Load CLAUDE.md content
    val claudeMdContent = loadClaudeMdContent(config.claudeMdPath)
    logger.info("CLAUDE.md loaded (${claudeMdContent.length} chars)")

    val githubBaseUrl = "https://github.com/${config.githubOwner}/${config.githubRepo}"

    // Initialize GitHub Issues MCP service if enabled
    var mcpService: GitHubIssuesMCPService? = null
    var mcpToolRegistry: ToolRegistry? = null

    if (config.enableIssueSearch) {
        // Try multiple locations for the JAR
        val possiblePaths = listOf(
            System.getenv("GITHUB_ISSUES_MCP_JAR"),
            "${System.getProperty("user.dir")}/github-issues-mcp/build/libs/github-issues-mcp-1.0.0-all.jar",
            "${System.getProperty("user.dir")}/../github-issues-mcp/build/libs/github-issues-mcp-1.0.0-all.jar",
        )
        val mcpJarPath = possiblePaths.filterNotNull().firstOrNull { File(it).exists() }

        if (mcpJarPath != null) {
            logger.info("Starting GitHub Issues MCP server from: $mcpJarPath")
            mcpService = GitHubIssuesMCPService(
                jarPath = mcpJarPath,
                githubToken = config.githubToken,
                githubOwner = config.githubOwner,
                githubRepo = config.githubRepo,
            )
            try {
                mcpToolRegistry = runBlocking { mcpService.start() }
                logger.info("GitHub Issues MCP service started successfully")
            } catch (e: Exception) {
                logger.error("Failed to start GitHub Issues MCP service: ${e.message}", e)
                mcpService = null
            }
        } else {
            logger.warn("GitHub Issues MCP JAR not found in any of the expected locations")
            logger.warn("Searched: ${possiblePaths.filterNotNull()}")
            logger.warn("GitHub issue search will be disabled")
            logger.warn("Build it with: ./gradlew :github-issues-mcp:jar")
        }
    } else {
        logger.info("Issue search is disabled (ENABLE_ISSUE_SEARCH=false)")
    }

    // Create agent service (manages agent lifecycle per request)
    val agentService = CodebaseQAAgent.create(
        config = config,
        vectorStore = vectorStore,
        voyageService = voyageService,
        claudeMdContent = claudeMdContent,
        githubBaseUrl = githubBaseUrl,
        mcpToolRegistry = mcpToolRegistry,
    )
    logger.info("Koog agent service initialized with model: ${config.llmModel}")

    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down...")
        mcpService?.close()
        vectorStore?.close()
        voyageService?.close()
    })

    // Start server
    logger.info("Starting server on port ${config.port}...")
    embeddedServer(Netty, port = config.port) {
        configureSerialization()
        configureCORS()
        configureRouting(agentService, vectorStore, voyageService, config)
    }.start(wait = true)
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

fun Application.configureCORS() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost() // In production, restrict to specific origins
    }
}

private fun loadClaudeMdContent(path: String): String {
    val file = File(path)
    return if (file.exists()) {
        file.readText()
    } else {
        val projectFile = File(".", "CLAUDE.md")
        if (projectFile.exists()) {
            projectFile.readText()
        } else {
            "# CLAUDE.md not found\nNo project documentation available."
        }
    }
}

