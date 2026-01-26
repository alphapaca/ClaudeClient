package com.github.alphapaca.claudeclient.data.repository

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.local.SystemAnalysisLocalDataSource
import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager
import com.github.alphapaca.claudeclient.data.mcp.MCPTool
import com.github.alphapaca.claudeclient.data.service.SystemAnalysisService
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisMessage
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisSession
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisSessionInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class SystemAnalysisRepository(
    private val localDataSource: SystemAnalysisLocalDataSource,
    private val systemAnalysisService: SystemAnalysisService,
    private val mcpClientManager: MCPClientManager,
    private val settingsRepository: SettingsRepository,
) {
    fun getAllSessions(): Flow<List<SystemAnalysisSessionInfo>> {
        return localDataSource.getAllSessionsFlow()
    }

    fun getSession(sessionId: String): Flow<SystemAnalysisSession?> {
        return localDataSource.getSessionFlow(sessionId)
    }

    suspend fun createSession(projectName: String): String {
        val name = "Analysis: $projectName"
        return localDataSource.createSession(name, projectName)
    }

    suspend fun deleteSession(sessionId: String) {
        localDataSource.deleteSession(sessionId)
    }

    suspend fun sendMessage(
        sessionId: String,
        userMessage: String,
    ): Result<Unit> {
        // Save user message
        localDataSource.saveMessage(sessionId, SystemAnalysisMessage.User(userMessage))

        // Get current messages for context
        val session = localDataSource.getSessionFlow(sessionId).first()
        val messages = session?.messages ?: emptyList()
        val projectName = session?.projectName ?: "Unknown Project"

        return try {
            // Build system prompt
            val systemPrompt = buildSystemPrompt(projectName)

            // Get settings
            val maxTokens = settingsRepository.getMaxTokens()

            // Measure inference time
            val startTime = System.currentTimeMillis()

            val response = systemAnalysisService.sendMessage(
                messages = messages,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens,
            )

            val inferenceTime = System.currentTimeMillis() - startTime

            // Save assistant response
            localDataSource.saveMessage(
                sessionId = sessionId,
                message = SystemAnalysisMessage.Assistant(
                    content = response.content,
                    model = LLMModel.LLAMA_3_2,
                    inputTokens = response.inputTokens,
                    outputTokens = response.outputTokens,
                    inferenceTimeMs = inferenceTime,
                    stopReason = response.stopReason,
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("SystemAnalysisRepository") { "Failed to send message: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Get available MCP tools for system analysis.
     */
    val availableTools: StateFlow<List<MCPTool>> = mcpClientManager.availableTools

    /**
     * Execute an MCP tool and save the result to the session.
     */
    suspend fun executeToolAndSaveResult(
        sessionId: String,
        tool: MCPTool,
        arguments: Map<String, Any?>,
    ): Result<String> {
        return try {
            val result = mcpClientManager.callTool(tool.serverName, tool.name, arguments)

            result.onSuccess { content ->
                // Save tool result as a message
                localDataSource.saveMessage(
                    sessionId = sessionId,
                    message = SystemAnalysisMessage.ToolResult(
                        toolName = tool.name,
                        result = content,
                    )
                )
            }

            result
        } catch (e: Exception) {
            Logger.e("SystemAnalysisRepository") { "Failed to execute tool ${tool.name}: ${e.message}" }
            Result.failure(e)
        }
    }

    private fun buildSystemPrompt(projectName: String): String {
        return """You are a System Analysis Assistant - an expert at analyzing system health, error logs, performance metrics, and diagnosing issues in software systems.

## Your Role
You help engineers and operators understand their system's behavior by:
- Analyzing error logs and identifying patterns
- Interpreting system metrics and performance data
- Diagnosing issues and suggesting root causes
- Recommending actionable fixes and optimizations
- Providing clear explanations of technical problems

## Current Context
Project: $projectName

## Available Data Sources
You have access to tools that provide:
1. **Error Logs** - Application errors with stacktraces, severity, occurrence counts
2. **System Metrics** - CPU, memory, disk, and network statistics
3. **Service Health** - Status of services, response times, error rates
4. **Error Trends** - Aggregated error statistics over time

## Analysis Guidelines

### When Analyzing Errors:
- Look for patterns in error types and frequencies
- Identify cascade failures (one error causing others)
- Check timestamps to understand error sequences
- Note which services/endpoints are most affected
- Consider the error severity distribution

### When Analyzing Metrics:
- Compare current values to typical baselines
- Look for resource exhaustion (memory, CPU, connections)
- Identify bottlenecks in the system
- Check for correlated issues across metrics

### When Diagnosing Issues:
- Start with the most severe or frequent problems
- Trace the error chain to find root causes
- Consider recent changes or deployments
- Look for environmental factors (traffic spikes, time-based issues)

## Response Format

Structure your analysis clearly:
1. **Summary** - Brief overview of findings
2. **Key Issues** - Most critical problems identified
3. **Root Cause Analysis** - Likely causes for each issue
4. **Recommendations** - Specific actions to resolve problems
5. **Priority** - Order of actions by urgency/impact

## Communication Style
- Be direct and actionable
- Use technical terms appropriately but explain when needed
- Provide specific evidence from the data
- Acknowledge uncertainty when data is insufficient
- Focus on what matters most for system reliability

Remember: Your goal is to help the user quickly understand what's wrong and what to do about it. Prioritize actionable insights over comprehensive reports."""
    }
}
