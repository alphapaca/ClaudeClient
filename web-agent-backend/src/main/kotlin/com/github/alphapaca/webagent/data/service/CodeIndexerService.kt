package com.github.alphapaca.webagent.data.service

import com.github.alphapaca.embeddingindexer.KotlinChunker
import com.github.alphapaca.embeddingindexer.VectorStore
import com.github.alphapaca.embeddingindexer.VoyageAIService
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Service for indexing the codebase into the vector database.
 */
class CodeIndexerService(
    private val voyageApiKey: String,
) {
    private val logger = LoggerFactory.getLogger(CodeIndexerService::class.java)

    /**
     * Check if the database exists and has code chunks indexed.
     */
    fun isDatabaseReady(dbPath: String): Boolean {
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            return false
        }

        // Try to open and check if it has code chunks
        return try {
            VectorStore(dbPath, wipeOnInit = false).use { store ->
                store.getCodeChunkCount() > 0
            }
        } catch (e: Exception) {
            logger.warn("Failed to check database: ${e.message}")
            false
        }
    }

    /**
     * Index the codebase and create the vector database.
     */
    suspend fun indexCodebase(
        projectPath: String,
        dbPath: String,
        onProgress: ((String) -> Unit)? = null
    ): IndexResult {
        logger.info("Starting code indexing...")
        onProgress?.invoke("Starting code indexing...")

        // Validate project path
        val projectDir = File(projectPath)
        if (!projectDir.exists() || !projectDir.isDirectory) {
            val error = "Invalid project path: $projectPath"
            logger.error(error)
            return IndexResult.Failure(error)
        }

        // Create database directory
        File(dbPath).parentFile?.mkdirs()

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
        onProgress?.invoke("Found ${kotlinFiles.size} Kotlin files")

        if (kotlinFiles.isEmpty()) {
            val error = "No Kotlin files found in $projectPath"
            logger.warn(error)
            return IndexResult.Failure(error)
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
        onProgress?.invoke("Created ${allChunks.size} code chunks")

        if (allChunks.isEmpty()) {
            val error = "No chunks to process"
            logger.warn(error)
            return IndexResult.Failure(error)
        }

        // Generate embeddings using voyage-code-3
        val voyageService = VoyageAIService(voyageApiKey)
        try {
            logger.info("Generating embeddings with ${VoyageAIService.CODE_EMBEDDING_MODEL}...")
            onProgress?.invoke("Generating embeddings (this may take a few minutes)...")

            val embeddings = voyageService.getEmbeddingsBatched(
                texts = allChunks.map { it.content },
                model = VoyageAIService.CODE_EMBEDDING_MODEL,
                batchSize = 128
            ) { processed, total ->
                val percent = (processed * 100 / total)
                logger.info("Embedding progress: $processed/$total ($percent%)")
                onProgress?.invoke("Generating embeddings: $percent%")
            }

            logger.info("Generated ${embeddings.size} embeddings")
            onProgress?.invoke("Storing in database...")

            // Store in database
            VectorStore(dbPath, wipeOnInit = true).use { store ->
                // Initialize code-specific tables
                store.initCodeTables()

                // Insert all chunks with embeddings
                store.insertCodeChunks(allChunks.zip(embeddings))

                val count = store.getCodeChunkCount()
                logger.info("Indexed $count code chunks in database")
                onProgress?.invoke("Indexed $count code chunks")

                return IndexResult.Success(
                    filesIndexed = kotlinFiles.size,
                    chunksCreated = allChunks.size,
                    dbPath = dbPath
                )
            }
        } finally {
            voyageService.close()
        }
    }

    sealed class IndexResult {
        data class Success(
            val filesIndexed: Int,
            val chunksCreated: Int,
            val dbPath: String
        ) : IndexResult()

        data class Failure(val error: String) : IndexResult()
    }
}
