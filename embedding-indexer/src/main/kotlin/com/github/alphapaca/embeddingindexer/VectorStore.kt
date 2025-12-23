package com.github.alphapaca.embeddingindexer

import org.slf4j.LoggerFactory
import org.sqlite.SQLiteConfig
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager

class VectorStore(private val dbPath: String) : Closeable {

    private val logger = LoggerFactory.getLogger(VectorStore::class.java)

    private val connection: Connection by lazy {
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()

        val isNewDb = !dbFile.exists()

        // Configure SQLite to enable extension loading
        val config = SQLiteConfig()
        config.enableLoadExtension(true)

        DriverManager.getConnection("jdbc:sqlite:$dbPath", config.toProperties()).also { conn ->
            // Load sqlite-vec extension
            loadSqliteVecExtension(conn)

            if (isNewDb) {
                initDatabase(conn)
            }
        }
    }

    private fun loadSqliteVecExtension(conn: Connection) {
        // Try to load from common locations
        val possiblePaths = listOf(
            // System-installed
            "vec0",
            // Homebrew on macOS
            "/opt/homebrew/lib/sqlite-vec/vec0",
            "/usr/local/lib/sqlite-vec/vec0",
            // Linux
            "/usr/lib/sqlite-vec/vec0",
            "/usr/local/lib/sqlite-vec/vec0",
        )

        var loaded = false
        for (path in possiblePaths) {
            try {
                conn.createStatement().use { stmt ->
                    stmt.execute("SELECT load_extension('$path')")
                }
                logger.info("Loaded sqlite-vec extension from: $path")
                loaded = true
                break
            } catch (e: Exception) {
                logger.debug("Failed to load from $path: ${e.message}")
            }
        }

        if (!loaded) {
            // Try extracting from resources as fallback
            tryLoadFromResources(conn)
        }
    }

    private fun tryLoadFromResources(conn: Connection) {
        val osName = System.getProperty("os.name").lowercase()
        val libName = when {
            osName.contains("mac") -> "vec0.dylib"
            osName.contains("linux") -> "vec0.so"
            osName.contains("win") -> "vec0.dll"
            else -> throw UnsupportedOperationException("Unsupported OS: $osName")
        }

        val resourcePath = "/native/$libName"
        val inputStream = javaClass.getResourceAsStream(resourcePath)

        if (inputStream != null) {
            val tempDir = Files.createTempDirectory("sqlite-vec")
            val libPath = tempDir.resolve(libName)

            inputStream.use { input ->
                Files.copy(input, libPath)
            }

            conn.createStatement().use { stmt ->
                stmt.execute("SELECT load_extension('${libPath.toAbsolutePath()}')")
            }
            logger.info("Loaded sqlite-vec extension from resources")
        } else {
            throw RuntimeException(
                "sqlite-vec extension not found. Install it via: brew install asg017/sqlite-vec/sqlite-vec"
            )
        }
    }

    private fun initDatabase(conn: Connection) {
        conn.createStatement().use { stmt ->
            // Create chunks table
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS chunks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    content TEXT NOT NULL,
                    start_offset INTEGER NOT NULL,
                    end_offset INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Create index on offsets
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_chunks_offset ON chunks(start_offset)
                """.trimIndent()
            )

            // Create virtual table for vectors (sqlite-vec)
            // voyage-3.5-lite uses 1024 dimensions by default
            stmt.execute(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS chunks_vec USING vec0(
                    chunk_id INTEGER PRIMARY KEY,
                    embedding FLOAT[1024]
                )
                """.trimIndent()
            )
        }

        logger.info("Database initialized at: $dbPath")
    }

    fun insertChunk(chunk: TextChunk, embedding: List<Float>): Long {
        val chunkId: Long

        // Insert chunk text
        connection.prepareStatement(
            """
            INSERT INTO chunks (content, start_offset, end_offset, created_at)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, chunk.content)
            stmt.setInt(2, chunk.startOffset)
            stmt.setInt(3, chunk.endOffset)
            stmt.setLong(4, System.currentTimeMillis())
            stmt.executeUpdate()

            // Get the generated ID
            val rs = stmt.generatedKeys
            rs.next()
            chunkId = rs.getLong(1)
        }

        // Insert embedding
        connection.prepareStatement(
            """
            INSERT INTO chunks_vec (chunk_id, embedding)
            VALUES (?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setLong(1, chunkId)
            stmt.setBytes(2, embeddingToBlob(embedding))
            stmt.executeUpdate()
        }

        return chunkId
    }

    fun insertChunks(chunksWithEmbeddings: List<Pair<TextChunk, List<Float>>>) {
        connection.autoCommit = false

        try {
            val chunkStmt = connection.prepareStatement(
                """
                INSERT INTO chunks (content, start_offset, end_offset, created_at)
                VALUES (?, ?, ?, ?)
                """.trimIndent()
            )

            val vecStmt = connection.prepareStatement(
                """
                INSERT INTO chunks_vec (chunk_id, embedding)
                VALUES (?, ?)
                """.trimIndent()
            )

            val now = System.currentTimeMillis()

            for ((chunk, embedding) in chunksWithEmbeddings) {
                // Insert chunk
                chunkStmt.setString(1, chunk.content)
                chunkStmt.setInt(2, chunk.startOffset)
                chunkStmt.setInt(3, chunk.endOffset)
                chunkStmt.setLong(4, now)
                chunkStmt.executeUpdate()

                // Get generated ID
                val rs = chunkStmt.generatedKeys
                rs.next()
                val chunkId = rs.getLong(1)

                // Insert embedding
                vecStmt.setLong(1, chunkId)
                vecStmt.setBytes(2, embeddingToBlob(embedding))
                vecStmt.executeUpdate()
            }

            connection.commit()
            logger.info("Inserted ${chunksWithEmbeddings.size} chunks with embeddings")
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    private fun embeddingToBlob(embedding: List<Float>): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)

        for (value in embedding) {
            buffer.putFloat(value)
        }

        return buffer.array()
    }

    fun getChunkCount(): Int {
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT COUNT(*) FROM chunks")
            rs.next()
            return rs.getInt(1)
        }
    }

    override fun close() {
        if (!connection.isClosed) {
            connection.close()
            logger.info("Database connection closed")
        }
    }
}
