package com.github.alphapaca.embeddingindexer

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("CodeIndexer")

// Default paths - override via command line args
private val DEFAULT_PROJECT_PATH = System.getProperty("user.dir")
private val DEFAULT_DB_PATH = System.getProperty("user.home") + "/.embedding-indexer/code-vectors.db"

/**
 * Code Indexer - Indexes Kotlin source files for semantic code search.
 *
 * Usage:
 *   java -jar embedding-indexer-all.jar code [project-path] [db-path]
 *
 * Arguments:
 *   project-path  Path to the Kotlin project to index (default: current directory)
 *   db-path       Path to the output database (default: ~/.embedding-indexer/code-vectors.db)
 *
 * Environment:
 *   VOYAGEAI_API_KEY  VoyageAI API key (or set in local.properties)
 */
fun main(args: Array<String>): Unit = runBlocking {
    logger.info("Starting Code Indexer")

    // Parse arguments
    val projectPath = args.getOrNull(0) ?: DEFAULT_PROJECT_PATH
    val dbPath = args.getOrNull(1) ?: DEFAULT_DB_PATH

    logger.info("Project path: $projectPath")
    logger.info("Database path: $dbPath")

    // Validate project path
    val projectDir = File(projectPath)
    if (!projectDir.exists() || !projectDir.isDirectory) {
        logger.error("Invalid project path: $projectPath")
        return@runBlocking
    }

    // Load API key
    val apiKey = loadEnvParameter("VOYAGEAI_API_KEY")
    logger.info("API key loaded")

    // Find Kotlin files
    val kotlinFiles = projectDir
        .walkTopDown()
        .filter { it.extension == "kt" }
        .filter { file ->
            // Exclude build directories, test files, and generated code
            !file.path.contains("/build/") &&
            !file.path.contains("/generated/") &&
            !file.path.contains("/.gradle/")
        }
        .toList()

    logger.info("Found ${kotlinFiles.size} Kotlin files")

    if (kotlinFiles.isEmpty()) {
        logger.warn("No Kotlin files found in $projectPath")
        return@runBlocking
    }

    // Chunk all files
    val chunker = KotlinChunker()
    val allChunks = kotlinFiles.flatMap { file ->
        try {
            chunker.chunkFile(file.absolutePath)
        } catch (e: Exception) {
            logger.warn("Failed to chunk ${file.path}: ${e.message}")
            emptyList()
        }
    }

    logger.info("Created ${allChunks.size} code chunks from ${kotlinFiles.size} files")

    if (allChunks.isEmpty()) {
        logger.warn("No chunks to process")
        return@runBlocking
    }

    // Print chunk statistics
    val chunksByType = allChunks.groupBy { it.chunkType }
    logger.info("Chunk breakdown:")
    chunksByType.forEach { (type, chunks) ->
        logger.info("  $type: ${chunks.size}")
    }

    // Generate embeddings using voyage-code-3
    val voyageService = VoyageAIService(apiKey)
    try {
        logger.info("Generating embeddings with ${VoyageAIService.CODE_EMBEDDING_MODEL}...")

        val embeddings = voyageService.getEmbeddingsBatched(
            texts = allChunks.map { it.content },
            model = VoyageAIService.CODE_EMBEDDING_MODEL,
            batchSize = 128
        ) { processed, total ->
            logger.info("Embedding progress: $processed/$total (${(processed * 100 / total)}%)")
        }

        logger.info("Generated ${embeddings.size} embeddings (dimension: ${embeddings.firstOrNull()?.size ?: 0})")

        // Store in database
        VectorStore(dbPath, wipeOnInit = true).use { store ->
            // Initialize code-specific tables
            store.initCodeTables()

            // Insert all chunks with embeddings
            store.insertCodeChunks(allChunks.zip(embeddings))

            logger.info("Indexed ${store.getCodeChunkCount()} code chunks in database")
        }

        logger.info("=".repeat(60))
        logger.info("Code indexing complete!")
        logger.info("Database: $dbPath")
        logger.info("Files indexed: ${kotlinFiles.size}")
        logger.info("Chunks created: ${allChunks.size}")
        logger.info("=".repeat(60))

    } finally {
        voyageService.close()
    }
}

/**
 * Interactive search mode for testing the code index.
 */
suspend fun searchInteractive(dbPath: String, apiKey: String) {
    val voyageService = VoyageAIService(apiKey)
    val store = VectorStore(dbPath, wipeOnInit = false)

    try {
        println("Code Search (type 'quit' to exit)")
        println("-".repeat(40))

        while (true) {
            print("\nQuery: ")
            val query = readlnOrNull()?.trim() ?: break

            if (query.equals("quit", ignoreCase = true)) break
            if (query.isBlank()) continue

            // Get query embedding
            val queryEmbedding = voyageService.getEmbeddings(
                listOf(query),
                VoyageAIService.CODE_EMBEDDING_MODEL
            ).first()

            // Search
            val results = store.searchSimilarCode(queryEmbedding, limit = 5)

            if (results.isEmpty()) {
                println("No results found.")
                continue
            }

            println("\nResults:")
            results.forEachIndexed { index, result ->
                println("\n${index + 1}. ${result.name} (${result.chunkType})")
                println("   File: ${result.filePath}:${result.startLine}-${result.endLine}")
                println("   Distance: ${result.distance}")
                result.signature?.let { println("   Signature: $it") }
                result.parentName?.let { println("   Parent: $it") }
                println("   Preview: ${result.content.take(200).replace("\n", " ")}...")
            }
        }
    } finally {
        store.close()
        voyageService.close()
    }
}
