package com.github.alphapaca.claudeclient.data.indexer

import co.touchlab.kermit.Logger
import org.sqlite.SQLiteConfig
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager

class VectorStore(
    private val dbPath: String,
    private val wipeOnInit: Boolean = false
) : Closeable {

    private val connection: Connection by lazy {
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()

        if (wipeOnInit && dbFile.exists()) {
            Logger.i("VectorStore") { "Deleting existing database: $dbPath" }
            dbFile.delete()
        }

        val config = SQLiteConfig()
        config.enableLoadExtension(true)

        DriverManager.getConnection("jdbc:sqlite:$dbPath", config.toProperties()).also { conn ->
            loadSqliteVecExtension(conn)
            initDatabase(conn)
        }
    }

    private fun loadSqliteVecExtension(conn: Connection) {
        val possiblePaths = listOf(
            "vec0",
            "/opt/homebrew/lib/sqlite-vec/vec0",
            "/usr/local/lib/sqlite-vec/vec0",
            "/usr/lib/sqlite-vec/vec0",
        )

        var loaded = false
        for (path in possiblePaths) {
            try {
                conn.createStatement().use { stmt ->
                    stmt.execute("SELECT load_extension('$path')")
                }
                Logger.i("VectorStore") { "Loaded sqlite-vec extension from: $path" }
                loaded = true
                break
            } catch (e: Exception) {
                Logger.d("VectorStore") { "Failed to load from $path: ${e.message}" }
            }
        }

        if (!loaded) {
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
            Logger.i("VectorStore") { "Loaded sqlite-vec extension from resources" }
        } else {
            throw RuntimeException(
                "sqlite-vec extension not found. Install it via: brew install asg017/sqlite-vec/sqlite-vec"
            )
        }
    }

    private fun initDatabase(conn: Connection) {
        conn.createStatement().use { stmt ->
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

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_code_file ON code_chunks(file_path)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_code_name ON code_chunks(name)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_code_type ON code_chunks(chunk_type)")

            stmt.execute(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS code_chunks_vec USING vec0(
                    chunk_id INTEGER PRIMARY KEY,
                    embedding FLOAT[1024]
                )
                """.trimIndent()
            )
        }

        Logger.i("VectorStore") { "Database initialized at: $dbPath" }
    }

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
            Logger.i("VectorStore") { "Inserted ${chunksWithEmbeddings.size} code chunks with embeddings" }
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    fun getChunkCount(): Int {
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT COUNT(*) FROM code_chunks")
            rs.next()
            return rs.getInt(1)
        }
    }

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

        Logger.d("VectorStore") { "Found ${results.size} similar code chunks" }
        return results
    }

    private fun embeddingToBlob(embedding: List<Float>): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)

        for (value in embedding) {
            buffer.putFloat(value)
        }

        return buffer.array()
    }

    override fun close() {
        if (!connection.isClosed) {
            connection.close()
            Logger.i("VectorStore") { "Database connection closed" }
        }
    }

    companion object {
        fun deleteDatabase(dbPath: String) {
            val dbFile = File(dbPath)
            if (dbFile.exists()) {
                dbFile.delete()
                Logger.i("VectorStore") { "Deleted database: $dbPath" }
            }
        }
    }
}
