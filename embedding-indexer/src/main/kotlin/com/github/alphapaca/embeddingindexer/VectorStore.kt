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

data class SearchResult(
    val chunkId: Long,
    val content: String,
    val distance: Float,
    val paragraphNumber: Int,
)

data class CodeSearchResult(
    val chunkId: Long,
    val content: String,
    val distance: Float,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val chunkType: CodeChunkType,
    val name: String,
    val parentName: String?,
    val signature: String?,
)

class VectorStore(
    private val dbPath: String,
    private val wipeOnInit: Boolean = false
) : Closeable {

    private val logger = LoggerFactory.getLogger(VectorStore::class.java)

    private val connection: Connection by lazy {
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()

        // Delete existing database to ensure fresh schema (only if requested)
        if (wipeOnInit && dbFile.exists()) {
            logger.info("Deleting existing database: $dbPath")
            dbFile.delete()
        }

        // Configure SQLite to enable extension loading
        val config = SQLiteConfig()
        config.enableLoadExtension(true)

        DriverManager.getConnection("jdbc:sqlite:$dbPath", config.toProperties()).also { conn ->
            // Load sqlite-vec extension
            loadSqliteVecExtension(conn)
            initDatabase(conn)
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
                    paragraph_number INTEGER NOT NULL DEFAULT 0,
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
            INSERT INTO chunks (content, start_offset, end_offset, paragraph_number, created_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, chunk.content)
            stmt.setInt(2, chunk.startOffset)
            stmt.setInt(3, chunk.endOffset)
            stmt.setInt(4, chunk.paragraphNumber)
            stmt.setLong(5, System.currentTimeMillis())
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
                INSERT INTO chunks (content, start_offset, end_offset, paragraph_number, created_at)
                VALUES (?, ?, ?, ?, ?)
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
                chunkStmt.setInt(4, chunk.paragraphNumber)
                chunkStmt.setLong(5, now)
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

    fun searchSimilar(queryEmbedding: List<Float>, limit: Int = 5): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val k = limit.coerceIn(1, 20)

        connection.prepareStatement(
            """
            SELECT
                chunks.id,
                chunks.content,
                chunks.paragraph_number,
                chunks_vec.distance
            FROM chunks_vec
            JOIN chunks ON chunks.id = chunks_vec.chunk_id
            WHERE embedding MATCH ? AND k = $k
            ORDER BY distance
            """.trimIndent()
        ).use { stmt ->
            stmt.setBytes(1, embeddingToBlob(queryEmbedding))

            val rs = stmt.executeQuery()
            while (rs.next()) {
                results.add(
                    SearchResult(
                        chunkId = rs.getLong("id"),
                        content = rs.getString("content"),
                        distance = rs.getFloat("distance"),
                        paragraphNumber = rs.getInt("paragraph_number"),
                    )
                )
            }
        }

        logger.debug("Found ${results.size} similar chunks")
        return results
    }

    override fun close() {
        if (!connection.isClosed) {
            connection.close()
            logger.info("Database connection closed")
        }
    }

    // ==================== Code Chunks Support ====================

    /**
     * Initialize code-specific tables. Call this before inserting code chunks.
     */
    fun initCodeTables() {
        connection.createStatement().use { stmt ->
            // Create code_chunks table
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS code_chunks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    content TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    start_line INTEGER NOT NULL,
                    end_line INTEGER NOT NULL,
                    chunk_type TEXT NOT NULL,
                    name TEXT NOT NULL,
                    parent_name TEXT,
                    signature TEXT,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Create indexes for code search
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_code_file ON code_chunks(file_path)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_code_name ON code_chunks(name)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_code_type ON code_chunks(chunk_type)")

            // Create virtual table for code vectors
            // voyage-code-3 uses 1024 dimensions
            stmt.execute(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS code_chunks_vec USING vec0(
                    chunk_id INTEGER PRIMARY KEY,
                    embedding FLOAT[1024]
                )
                """.trimIndent()
            )
        }

        logger.info("Code tables initialized")
    }

    /**
     * Insert a single code chunk with its embedding.
     */
    fun insertCodeChunk(chunk: CodeChunk, embedding: List<Float>): Long {
        val chunkId: Long

        connection.prepareStatement(
            """
            INSERT INTO code_chunks (content, file_path, start_line, end_line, chunk_type, name, parent_name, signature, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, chunk.content)
            stmt.setString(2, chunk.filePath)
            stmt.setInt(3, chunk.startLine)
            stmt.setInt(4, chunk.endLine)
            stmt.setString(5, chunk.chunkType.name)
            stmt.setString(6, chunk.name)
            stmt.setString(7, chunk.parentName)
            stmt.setString(8, chunk.signature)
            stmt.setLong(9, System.currentTimeMillis())
            stmt.executeUpdate()

            val rs = stmt.generatedKeys
            rs.next()
            chunkId = rs.getLong(1)
        }

        connection.prepareStatement(
            """
            INSERT INTO code_chunks_vec (chunk_id, embedding)
            VALUES (?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setLong(1, chunkId)
            stmt.setBytes(2, embeddingToBlob(embedding))
            stmt.executeUpdate()
        }

        return chunkId
    }

    /**
     * Insert multiple code chunks with their embeddings in a batch.
     */
    fun insertCodeChunks(chunksWithEmbeddings: List<Pair<CodeChunk, List<Float>>>) {
        connection.autoCommit = false

        try {
            val chunkStmt = connection.prepareStatement(
                """
                INSERT INTO code_chunks (content, file_path, start_line, end_line, chunk_type, name, parent_name, signature, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )

            val vecStmt = connection.prepareStatement(
                """
                INSERT INTO code_chunks_vec (chunk_id, embedding)
                VALUES (?, ?)
                """.trimIndent()
            )

            val now = System.currentTimeMillis()

            for ((chunk, embedding) in chunksWithEmbeddings) {
                chunkStmt.setString(1, chunk.content)
                chunkStmt.setString(2, chunk.filePath)
                chunkStmt.setInt(3, chunk.startLine)
                chunkStmt.setInt(4, chunk.endLine)
                chunkStmt.setString(5, chunk.chunkType.name)
                chunkStmt.setString(6, chunk.name)
                chunkStmt.setString(7, chunk.parentName)
                chunkStmt.setString(8, chunk.signature)
                chunkStmt.setLong(9, now)
                chunkStmt.executeUpdate()

                val rs = chunkStmt.generatedKeys
                rs.next()
                val chunkId = rs.getLong(1)

                vecStmt.setLong(1, chunkId)
                vecStmt.setBytes(2, embeddingToBlob(embedding))
                vecStmt.executeUpdate()
            }

            connection.commit()
            logger.info("Inserted ${chunksWithEmbeddings.size} code chunks with embeddings")
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    /**
     * Get the count of indexed code chunks.
     */
    fun getCodeChunkCount(): Int {
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT COUNT(*) FROM code_chunks")
            rs.next()
            return rs.getInt(1)
        }
    }

    /**
     * Search for similar code chunks using vector similarity.
     */
    fun searchSimilarCode(queryEmbedding: List<Float>, limit: Int = 5): List<CodeSearchResult> {
        val results = mutableListOf<CodeSearchResult>()
        val k = limit.coerceIn(1, 50)

        connection.prepareStatement(
            """
            SELECT
                code_chunks.id,
                code_chunks.content,
                code_chunks.file_path,
                code_chunks.start_line,
                code_chunks.end_line,
                code_chunks.chunk_type,
                code_chunks.name,
                code_chunks.parent_name,
                code_chunks.signature,
                code_chunks_vec.distance
            FROM code_chunks_vec
            JOIN code_chunks ON code_chunks.id = code_chunks_vec.chunk_id
            WHERE embedding MATCH ? AND k = $k
            ORDER BY distance
            """.trimIndent()
        ).use { stmt ->
            stmt.setBytes(1, embeddingToBlob(queryEmbedding))

            val rs = stmt.executeQuery()
            while (rs.next()) {
                results.add(
                    CodeSearchResult(
                        chunkId = rs.getLong("id"),
                        content = rs.getString("content"),
                        distance = rs.getFloat("distance"),
                        filePath = rs.getString("file_path"),
                        startLine = rs.getInt("start_line"),
                        endLine = rs.getInt("end_line"),
                        chunkType = CodeChunkType.valueOf(rs.getString("chunk_type")),
                        name = rs.getString("name"),
                        parentName = rs.getString("parent_name"),
                        signature = rs.getString("signature"),
                    )
                )
            }
        }

        logger.debug("Found ${results.size} similar code chunks")
        return results
    }

    /**
     * Search for code chunks by file path pattern.
     */
    fun searchCodeByFile(filePattern: String, limit: Int = 20): List<CodeChunk> {
        val results = mutableListOf<CodeChunk>()

        connection.prepareStatement(
            """
            SELECT id, content, file_path, start_line, end_line, chunk_type, name, parent_name, signature
            FROM code_chunks
            WHERE file_path LIKE ?
            LIMIT ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, "%$filePattern%")
            stmt.setInt(2, limit)

            val rs = stmt.executeQuery()
            while (rs.next()) {
                results.add(
                    CodeChunk(
                        id = rs.getLong("id"),
                        content = rs.getString("content"),
                        filePath = rs.getString("file_path"),
                        startLine = rs.getInt("start_line"),
                        endLine = rs.getInt("end_line"),
                        chunkType = CodeChunkType.valueOf(rs.getString("chunk_type")),
                        name = rs.getString("name"),
                        parentName = rs.getString("parent_name"),
                        signature = rs.getString("signature"),
                    )
                )
            }
        }

        return results
    }

    /**
     * Search for code chunks by symbol name.
     */
    fun searchCodeByName(namePattern: String, limit: Int = 20): List<CodeChunk> {
        val results = mutableListOf<CodeChunk>()

        connection.prepareStatement(
            """
            SELECT id, content, file_path, start_line, end_line, chunk_type, name, parent_name, signature
            FROM code_chunks
            WHERE name LIKE ? OR parent_name LIKE ?
            LIMIT ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, "%$namePattern%")
            stmt.setString(2, "%$namePattern%")
            stmt.setInt(3, limit)

            val rs = stmt.executeQuery()
            while (rs.next()) {
                results.add(
                    CodeChunk(
                        id = rs.getLong("id"),
                        content = rs.getString("content"),
                        filePath = rs.getString("file_path"),
                        startLine = rs.getInt("start_line"),
                        endLine = rs.getInt("end_line"),
                        chunkType = CodeChunkType.valueOf(rs.getString("chunk_type")),
                        name = rs.getString("name"),
                        parentName = rs.getString("parent_name"),
                        signature = rs.getString("signature"),
                    )
                )
            }
        }

        return results
    }
}
