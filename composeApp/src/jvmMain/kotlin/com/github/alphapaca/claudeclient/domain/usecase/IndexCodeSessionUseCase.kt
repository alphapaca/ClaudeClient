package com.github.alphapaca.claudeclient.domain.usecase

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.indexer.KotlinChunker
import com.github.alphapaca.claudeclient.data.indexer.VectorStore
import com.github.alphapaca.claudeclient.data.indexer.VoyageAIService
import com.github.alphapaca.claudeclient.data.local.CodeSessionLocalDataSource
import com.github.alphapaca.claudeclient.domain.model.IndexingPhase
import com.github.alphapaca.claudeclient.domain.model.IndexingProgress
import com.github.alphapaca.claudeclient.domain.model.IndexingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class IndexCodeSessionUseCase(
    private val voyageAIService: VoyageAIService?,
    private val codeSessionLocalDataSource: CodeSessionLocalDataSource,
) {
    private val chunker = KotlinChunker()

    val isConfigured: Boolean get() = voyageAIService != null

    suspend operator fun invoke(
        sessionId: String,
        repoPath: String,
        dbPath: String,
        onProgress: (IndexingProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check if VoyageAI is configured
            val voyageService = voyageAIService
                ?: throw IllegalStateException("VoyageAI API key not configured. Please set VOYAGEAI_API_KEY in local.properties.")

            // Update status to indexing
            codeSessionLocalDataSource.updateIndexingStatus(
                sessionId = sessionId,
                status = IndexingStatus.INDEXING,
            )

            // Phase 1: Scanning files
            onProgress(IndexingProgress(IndexingPhase.SCANNING, 0, 0, "Scanning repository..."))

            val repoDir = File(repoPath)
            if (!repoDir.exists() || !repoDir.isDirectory) {
                throw IllegalArgumentException("Invalid repository path: $repoPath")
            }

            val kotlinFiles = repoDir
                .walkTopDown()
                .filter { it.extension == "kt" }
                .filter { file ->
                    !file.path.contains("/build/") &&
                    !file.path.contains("/.gradle/") &&
                    !file.path.contains("/generated/")
                }
                .toList()

            Logger.i("IndexCodeSession") { "Found ${kotlinFiles.size} Kotlin files" }
            onProgress(IndexingProgress(IndexingPhase.SCANNING, kotlinFiles.size, kotlinFiles.size, "Found ${kotlinFiles.size} files"))

            if (kotlinFiles.isEmpty()) {
                codeSessionLocalDataSource.updateIndexingStatus(
                    sessionId = sessionId,
                    status = IndexingStatus.COMPLETED,
                    fileCount = 0,
                    chunkCount = 0,
                )
                return@withContext Result.success(Unit)
            }

            // Phase 2: Chunking files
            onProgress(IndexingProgress(IndexingPhase.CHUNKING, 0, kotlinFiles.size, "Chunking files..."))

            val allChunks = kotlinFiles.flatMapIndexed { index, file ->
                onProgress(IndexingProgress(IndexingPhase.CHUNKING, index + 1, kotlinFiles.size, "Chunking: ${file.name}"))
                try {
                    chunker.chunkFile(file.absolutePath)
                } catch (e: Exception) {
                    Logger.w("IndexCodeSession") { "Failed to chunk ${file.path}: ${e.message}" }
                    emptyList()
                }
            }

            Logger.i("IndexCodeSession") { "Created ${allChunks.size} chunks" }
            onProgress(IndexingProgress(IndexingPhase.CHUNKING, allChunks.size, allChunks.size, "Created ${allChunks.size} chunks"))

            if (allChunks.isEmpty()) {
                codeSessionLocalDataSource.updateIndexingStatus(
                    sessionId = sessionId,
                    status = IndexingStatus.COMPLETED,
                    fileCount = kotlinFiles.size,
                    chunkCount = 0,
                )
                return@withContext Result.success(Unit)
            }

            // Phase 3: Generating embeddings
            onProgress(IndexingProgress(IndexingPhase.EMBEDDING, 0, allChunks.size, "Generating embeddings..."))

            val embeddings = voyageService.getEmbeddingsBatched(
                texts = allChunks.map { it.content },
                model = VoyageAIService.CODE_EMBEDDING_MODEL,
            ) { processed, total ->
                onProgress(IndexingProgress(IndexingPhase.EMBEDDING, processed, total, "Embedding: $processed/$total"))
            }

            Logger.i("IndexCodeSession") { "Generated ${embeddings.size} embeddings" }

            // Phase 4: Storing in vector database
            onProgress(IndexingProgress(IndexingPhase.STORING, 0, allChunks.size, "Storing in database..."))

            VectorStore(dbPath, wipeOnInit = true).use { store ->
                store.insertCodeChunks(allChunks.zip(embeddings))

                val chunkCount = store.getChunkCount()
                Logger.i("IndexCodeSession") { "Stored $chunkCount chunks in database" }

                onProgress(IndexingProgress(IndexingPhase.STORING, chunkCount, chunkCount, "Indexing complete!"))

                // Update session with final stats
                codeSessionLocalDataSource.updateIndexingStatus(
                    sessionId = sessionId,
                    status = IndexingStatus.COMPLETED,
                    fileCount = kotlinFiles.size,
                    chunkCount = chunkCount,
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("IndexCodeSession") { "Indexing failed: ${e.message}" }

            codeSessionLocalDataSource.updateIndexingStatus(
                sessionId = sessionId,
                status = IndexingStatus.FAILED,
                errorMessage = e.message,
            )

            Result.failure(e)
        }
    }
}
