package com.github.alphapaca.embeddingindexer

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Properties

private val logger = LoggerFactory.getLogger("EmbeddingIndexer")

// Hardcoded path to the text file to index
private const val TEXT_FILE_PATH = "/path/to/text/sample.txt"

// Database path
private val DB_PATH = System.getProperty("user.home") + "/.embedding-indexer/vectors.db"

fun main(): Unit = runBlocking {
    logger.info("Starting Embedding Indexer")

    // 1. Load API key
    val apiKey = loadApiKey()
    logger.info("API key loaded")

    // 2. Read text file
    val textFile = File(TEXT_FILE_PATH)
    if (!textFile.exists()) {
        logger.error("Text file not found: $TEXT_FILE_PATH")
        return@runBlocking
    }

    val text = textFile.readText()
    logger.info("Read ${text.length} characters from ${textFile.name}")

    // 3. Chunk by sentences
    val chunker = TextChunker()
    val chunks = chunker.chunkBySentences(text)
    logger.info("Created ${chunks.size} chunks")

    if (chunks.isEmpty()) {
        logger.warn("No chunks to process")
        return@runBlocking
    }

    // 4. Get embeddings from VoyageAI
    val voyageService = VoyageAIService(apiKey)
    try {
        val embeddings = voyageService.getEmbeddingsBatched(
            texts = chunks.map { it.content },
            batchSize = 128
        ) { processed, total ->
            logger.info("Embedding progress: $processed/$total")
        }

        logger.info("Generated ${embeddings.size} embeddings (dimension: ${embeddings.firstOrNull()?.size ?: 0})")

        // 5. Store in SQLite with sqlite-vec
        VectorStore(DB_PATH).use { store ->
            store.insertChunks(chunks.zip(embeddings))
            logger.info("Indexed ${store.getChunkCount()} total chunks in database")
        }

        logger.info("Indexing complete! Database saved to: $DB_PATH")

    } finally {
        voyageService.close()
    }
}

private fun loadApiKey(): String {
    // 1. Try environment variable first
    System.getenv("VOYAGEAI_API_KEY")?.let {
        logger.debug("Using API key from environment variable")
        return it
    }

    // 2. Try local.properties in project root
    val localPropsFile = File("local.properties")
    if (localPropsFile.exists()) {
        val props = Properties().apply { load(localPropsFile.inputStream()) }
        props.getProperty("VOYAGEAI_API_KEY")?.let {
            logger.debug("Using API key from local.properties")
            return it
        }
    }

    // 3. Try user home config
    val homePropsFile = File(System.getProperty("user.home"), ".embedding-indexer/config.properties")
    if (homePropsFile.exists()) {
        val props = Properties().apply { load(homePropsFile.inputStream()) }
        props.getProperty("VOYAGEAI_API_KEY")?.let {
            logger.debug("Using API key from ~/.embedding-indexer/config.properties")
            return it
        }
    }

    throw IllegalStateException(
        """
        VOYAGEAI_API_KEY not found. Set it via one of:
        1. Environment variable: export VOYAGEAI_API_KEY=your_key
        2. local.properties file: VOYAGEAI_API_KEY=your_key
        3. ~/.embedding-indexer/config.properties: VOYAGEAI_API_KEY=your_key
        """.trimIndent()
    )
}
