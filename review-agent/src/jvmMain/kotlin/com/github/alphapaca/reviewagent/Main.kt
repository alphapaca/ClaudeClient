package com.github.alphapaca.reviewagent

import com.github.alphapaca.embeddingindexer.KotlinChunker
import com.github.alphapaca.embeddingindexer.VectorStore
import com.github.alphapaca.embeddingindexer.VoyageAIService
import com.github.alphapaca.reviewagent.model.FileDiff
import com.github.alphapaca.reviewagent.service.ClaudeReviewService
import com.github.alphapaca.reviewagent.service.GitHubService
import com.github.alphapaca.reviewagent.service.GitService
import com.github.alphapaca.reviewagent.service.ReviewService
import com.github.alphapaca.reviewagent.util.EnvConfig
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("ReviewAgent")

fun main(): Unit = runBlocking {
    try {
        runReviewAgent()
    } catch (e: Exception) {
        logger.error("Review agent failed: ${e.message}", e)
        exitProcess(1)
    }
}

private suspend fun runReviewAgent() {
    logger.info("Starting review agent...")

    // 1. Load environment variables
    val config = EnvConfig.load()
    logger.info("Reviewing PR #${config.prNumber} in ${config.repoOwner}/${config.repoName}")

    // 2. Initialize services
    val gitService = GitService(
        repoPath = ".",
        baseSha = config.baseSha,
        headSha = config.headSha
    )
    val githubService = GitHubService(config.githubToken, config.repoOwner, config.repoName)
    val voyageService = VoyageAIService(config.voyageApiKey)
    val claudeService = ClaudeReviewService(config.anthropicApiKey)

    try {
        // 3. Read CLAUDE.md from repo root (if exists)
        val claudeMdContent = gitService.readClaudeMd()

        // 4. Get PR diff and changed files
        val fileDiffs = gitService.getPRDiff()
        if (fileDiffs.isEmpty()) {
            logger.info("No file changes found in PR")
            return
        }
        logger.info("Found ${fileDiffs.size} changed files")

        // 5. Build RAG index from changed files + surrounding context
        val vectorStore = buildIndex(gitService, voyageService, fileDiffs)

        try {
            // 6. Create ReviewService and run review
            val reviewService = ReviewService(
                vectorStore = vectorStore,
                voyageService = voyageService,
                claudeService = claudeService,
                claudeMdContent = claudeMdContent
            )

            // 7. Generate reviews for each file
            val reviews = reviewService.reviewChanges(fileDiffs)
            logger.info("Generated ${reviews.size} review comments")

            // 8. Post comments to GitHub
            if (reviews.isNotEmpty()) {
                val success = githubService.postReviewComments(
                    prNumber = config.prNumber,
                    headSha = config.headSha,
                    comments = reviews
                )
                if (success) {
                    logger.info("Successfully posted review to PR #${config.prNumber}")
                } else {
                    logger.error("Failed to post review comments")
                    exitProcess(1)
                }
            } else {
                logger.info("No issues found in PR - looks good!")
            }
        } finally {
            vectorStore.close()
        }
    } finally {
        voyageService.close()
        claudeService.close()
        githubService.close()
    }

    logger.info("Review agent completed successfully")
}

private suspend fun buildIndex(
    gitService: GitService,
    voyageService: VoyageAIService,
    fileDiffs: List<FileDiff>
): VectorStore {
    logger.info("Building code index...")

    // Get all changed files + files in same directories (for context)
    val changedPaths = fileDiffs.map { it.filePath }.toSet()
    val contextPaths = changedPaths.flatMap { path ->
        val parent = File(path).parentFile
        if (parent != null && parent.exists()) {
            parent.listFiles()
                ?.filter { it.isFile && it.extension == "kt" }
                ?.map { it.path }
                ?: emptyList()
        } else {
            emptyList()
        }
    }.toSet()

    val allPaths = (changedPaths + contextPaths)
        .filter { it.endsWith(".kt") }
        .distinct()

    logger.info("Indexing ${allPaths.size} Kotlin files...")

    // Chunk all files
    val chunker = KotlinChunker()
    val allChunks = allPaths.flatMap { path ->
        try {
            val content = gitService.readFileAtHead(path)
            if (content != null) {
                chunker.chunkFile(path)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.warn("Failed to chunk $path: ${e.message}")
            emptyList()
        }
    }

    if (allChunks.isEmpty()) {
        logger.warn("No code chunks to index")
        return VectorStore(":memory:", wipeOnInit = true).apply { initCodeTables() }
    }

    logger.info("Created ${allChunks.size} code chunks")

    // Generate embeddings
    val embeddings = voyageService.getEmbeddingsBatched(
        texts = allChunks.map { it.content },
        model = VoyageAIService.CODE_EMBEDDING_MODEL,
        batchSize = 128
    ) { current, total ->
        logger.info("Embedding progress: $current/$total")
    }

    // Store in in-memory database (ephemeral for this run)
    val store = VectorStore(":memory:", wipeOnInit = true)
    store.initCodeTables()
    store.insertCodeChunks(allChunks.zip(embeddings))

    logger.info("Indexed ${allChunks.size} code chunks")

    return store
}
