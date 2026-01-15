package com.github.alphapaca.webagent

import com.github.alphapaca.embeddingindexer.VectorStore
import com.github.alphapaca.embeddingindexer.VoyageAIService
import com.github.alphapaca.webagent.api.configureRouting
import com.github.alphapaca.webagent.config.AppConfig
import com.github.alphapaca.webagent.data.repository.RAGRepository
import com.github.alphapaca.webagent.data.service.ClaudeQAService
import com.github.alphapaca.webagent.data.service.CodeIndexerService
import com.github.alphapaca.webagent.data.service.DirectGitHubService
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

fun main(): Unit = runBlocking {
    logger.info("Starting Web Agent Backend Server...")

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
    logger.info("  Vector DB: ${config.vectorDbPath}")
    logger.info("  Project path: ${config.projectPath}")
    logger.info("  CLAUDE.md: ${config.claudeMdPath}")
    logger.info("  GitHub: ${config.githubOwner}/${config.githubRepo}")
    logger.info("  Auto-index: ${config.autoIndex}")

    // Check if database exists and index if needed
    val indexerService = CodeIndexerService(config.voyageApiKey)

    if (!indexerService.isDatabaseReady(config.vectorDbPath)) {
        if (config.autoIndex) {
            logger.info("Database not found or empty, starting indexing...")
            logger.info("This may take a few minutes on first run...")

            when (val result = indexerService.indexCodebase(
                projectPath = config.projectPath,
                dbPath = config.vectorDbPath,
                onProgress = { msg -> logger.info("Indexing: $msg") }
            )) {
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
            logger.warn("Run the indexer manually or set AUTO_INDEX=true")
        }
    }

    // Initialize services
    val vectorStore = VectorStore(config.vectorDbPath)
    logger.info("Vector store initialized with ${vectorStore.getCodeChunkCount()} code chunks")

    val voyageService = VoyageAIService(config.voyageApiKey)
    val claudeService = ClaudeQAService(config.anthropicApiKey)

    // Initialize direct GitHub service (simpler than MCP)
    val githubService: DirectGitHubService? = try {
        DirectGitHubService(
            token = config.githubToken,
            owner = config.githubOwner,
            repo = config.githubRepo,
        ).also {
            logger.info("Direct GitHub service initialized for ${config.githubOwner}/${config.githubRepo}")
        }
    } catch (e: Exception) {
        logger.warn("Failed to initialize GitHub service: ${e.message}")
        null
    }

    // Load CLAUDE.md content
    val claudeMdContent = RAGRepository.loadClaudeMdContent(config.claudeMdPath)
    logger.info("CLAUDE.md loaded (${claudeMdContent.length} chars)")

    // Create RAG repository
    val ragRepository = RAGRepository(
        vectorStore = vectorStore,
        voyageService = voyageService,
        claudeService = claudeService,
        githubService = githubService,
        claudeMdContent = claudeMdContent,
        githubBaseUrl = "https://github.com/${config.githubOwner}/${config.githubRepo}",
        enableCodeSearch = config.enableCodeSearch,
        enableIssueSearch = config.enableIssueSearch,
    )
    if (!config.enableCodeSearch) {
        logger.warn("Code search is disabled (ENABLE_CODE_SEARCH=false)")
    }
    if (!config.enableIssueSearch) {
        logger.warn("Issue search is disabled (ENABLE_ISSUE_SEARCH=false)")
    }

    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down...")
        ragRepository.close()
    })

    // Start server
    logger.info("Starting server on port ${config.port}...")
    embeddedServer(Netty, port = config.port) {
        configureSerialization()
        configureCORS()
        configureRouting(ragRepository)
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

