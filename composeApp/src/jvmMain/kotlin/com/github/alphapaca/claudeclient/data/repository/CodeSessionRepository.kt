package com.github.alphapaca.claudeclient.data.repository

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.indexer.VectorStore
import com.github.alphapaca.claudeclient.data.indexer.VoyageAIService
import com.github.alphapaca.claudeclient.data.local.CodeSessionLocalDataSource
import com.github.alphapaca.claudeclient.data.service.CodeSessionService
import com.github.alphapaca.claudeclient.data.tool.CodeSearchTool
import com.github.alphapaca.claudeclient.domain.model.CodeSession
import com.github.alphapaca.claudeclient.domain.model.CodeSessionInfo
import com.github.alphapaca.claudeclient.domain.model.CodeSessionMessage
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

class CodeSessionRepository(
    private val localDataSource: CodeSessionLocalDataSource,
    private val voyageAIService: VoyageAIService?,
    private val codeSessionService: CodeSessionService,
    private val settingsRepository: SettingsRepository,
) {
    val isConfigured: Boolean get() = voyageAIService != null
    fun getAllSessions(): Flow<List<CodeSessionInfo>> {
        return localDataSource.getAllCodeSessionsFlow()
    }

    fun getSession(sessionId: String): Flow<CodeSession?> {
        return localDataSource.getCodeSessionFlow(sessionId)
    }

    suspend fun createSession(repoPath: String): String {
        // Generate a name from the repo folder
        val repoDir = File(repoPath)
        val name = repoDir.name.ifBlank { "Code Session" }

        // Place the vector DB inside the repo's .code-index folder
        val dbPath = File(repoDir, ".code-index/vectors.db").absolutePath

        return localDataSource.createCodeSession(
            name = name,
            repoPath = repoPath,
            dbPath = dbPath,
        )
    }

    suspend fun deleteSession(sessionId: String) {
        // Get the db path before deleting
        val dbPath = localDataSource.getCodeSessionDbPath(sessionId)

        // Delete from local database
        localDataSource.deleteCodeSession(sessionId)

        // Delete the vector database file
        dbPath?.let { VectorStore.deleteDatabase(it) }
    }

    suspend fun sendMessage(
        sessionId: String,
        userMessage: String,
    ): Result<Unit> {
        val dbPath = localDataSource.getCodeSessionDbPath(sessionId)
            ?: return Result.failure(IllegalStateException("Session not found"))

        val repoPath = localDataSource.getCodeSessionRepoPath(sessionId)
            ?: return Result.failure(IllegalStateException("Session not found"))

        // Save user message
        localDataSource.saveMessage(sessionId, CodeSessionMessage.User(userMessage))

        // Get current messages for context
        val session = localDataSource.getCodeSessionFlow(sessionId).first()
        val messages = session?.messages ?: emptyList()

        // Check if VoyageAI is configured
        val voyageService = voyageAIService
            ?: return Result.failure(IllegalStateException("VoyageAI API key not configured"))

        // Create the code search tool with the session's vector store
        val vectorStore = VectorStore(dbPath, wipeOnInit = false)

        return try {
            val codeSearchTool = CodeSearchTool(vectorStore, voyageService)

            // Get settings
            val model = settingsRepository.getModel()
            val maxTokens = settingsRepository.getMaxTokens()

            // Build system prompt for code assistance
            val systemPrompt = buildCodeSessionSystemPrompt(repoPath)

            // Measure inference time
            val startTime = System.currentTimeMillis()

            val response = codeSessionService.sendMessage(
                messages = messages,
                model = model,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens,
                codeSearchTool = codeSearchTool,
            )

            val inferenceTime = System.currentTimeMillis() - startTime

            // Save assistant response
            localDataSource.saveMessage(
                sessionId = sessionId,
                message = CodeSessionMessage.Assistant(
                    content = response.content,
                    model = model,
                    inputTokens = response.inputTokens,
                    outputTokens = response.outputTokens,
                    inferenceTimeMs = inferenceTime,
                    stopReason = response.stopReason,
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("CodeSessionRepository") { "Failed to send message: ${e.message}" }
            Result.failure(e)
        } finally {
            vectorStore.close()
        }
    }

    private fun buildCodeSessionSystemPrompt(repoPath: String): String {
        return """You are a helpful coding assistant with access to an indexed codebase.

Repository: $repoPath

You have access to a search_code tool that performs semantic search over the codebase.
Use this tool whenever you need to:
- Find specific implementations or functions
- Understand how a feature works
- Locate relevant code for a user's question
- Explore the codebase structure

When answering questions about the code:
1. First use search_code to find relevant code snippets
2. Analyze the code you find
3. Provide clear, helpful explanations
4. Reference specific files and line numbers when relevant

Be concise but thorough. If you can't find relevant code, say so and suggest what the user might look for."""
    }
}
