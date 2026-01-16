package com.github.alphapaca.webagent.agent

import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.llm.LLModel
import com.github.alphapaca.embeddingindexer.VectorStore
import com.github.alphapaca.embeddingindexer.VoyageAIService
import com.github.alphapaca.webagent.agent.tools.CodeSearchToolSet
import com.github.alphapaca.webagent.agent.tools.DocumentationToolSet
import com.github.alphapaca.webagent.config.AppConfig

object CodebaseQAAgent {

    private const val SYSTEM_PROMPT = """
You are a helpful assistant that answers questions about the ClaudeClient codebase.

You have access to the following tools:
1. searchCode - Search the codebase for relevant code using semantic similarity
2. getProjectDocumentation - Get the project's CLAUDE.md documentation
3. search_github_issues - Search GitHub issues for bugs, feature requests, and discussions
4. get_github_issue - Get details of a specific GitHub issue by number
5. list_github_issues - List recent issues in the repository
6. create_github_issue - Create a new GitHub issue (requires title, optional body/labels/assignees)

IMPORTANT - Token efficiency rules:
- Call each tool AT MOST ONCE per question to avoid rate limits
- Use getProjectDocumentation first for architecture questions
- Use searchCode ONCE with a good query - do NOT call it multiple times
- Use search_github_issues for questions about bugs, features, or project status
- After getting tool results, provide your answer immediately

Guidelines:
- Reference specific files and line numbers when discussing code
- Reference GitHub issue numbers and URLs when discussing issues
- If the tools don't provide enough information, say so clearly
- Use markdown formatting for code blocks and structure
- Be concise but thorough
"""

    fun resolveAnthropicModel(modelName: String): LLModel = when (modelName.lowercase()) {
        "claude-sonnet-4", "sonnet-4" -> AnthropicModels.Sonnet_4
        "claude-sonnet-4.5", "sonnet-4.5" -> AnthropicModels.Sonnet_4_5
        "claude-opus-4", "opus-4" -> AnthropicModels.Opus_4
        "claude-opus-4.1", "opus-4.1" -> AnthropicModels.Opus_4_1
        "claude-opus-4.5", "opus-4.5" -> AnthropicModels.Opus_4_5
        "claude-haiku-3.5", "haiku-3.5" -> AnthropicModels.Haiku_3_5
        "claude-haiku-4.5", "haiku-4.5" -> AnthropicModels.Haiku_4_5
        else -> AnthropicModels.Sonnet_4 // Default fallback
    }

    fun create(
        config: AppConfig,
        vectorStore: VectorStore?,
        voyageService: VoyageAIService?,
        claudeMdContent: String,
        githubBaseUrl: String,
        mcpToolRegistry: ToolRegistry? = null,
    ): AIAgentService<String, String, *> {
        val llmModel = resolveAnthropicModel(config.llmModel)
        val promptExecutor = simpleAnthropicExecutor(config.anthropicApiKey)

        // Build tool registry with enabled tools
        val toolRegistry = ToolRegistry {
            if (config.enableCodeSearch && vectorStore != null && voyageService != null) {
                tools(CodeSearchToolSet(vectorStore, voyageService, githubBaseUrl).asTools())
            }
            // Always include documentation tool
            tools(DocumentationToolSet(claudeMdContent).asTools())

            // Add MCP tools if available
            mcpToolRegistry?.let { mcp ->
                tools(mcp.tools)
            }
        }

        return AIAgentService(
            promptExecutor = promptExecutor,
            llmModel = llmModel,
            systemPrompt = SYSTEM_PROMPT.trimIndent(),
            toolRegistry = toolRegistry,
            temperature = config.temperature,
        )
    }
}
