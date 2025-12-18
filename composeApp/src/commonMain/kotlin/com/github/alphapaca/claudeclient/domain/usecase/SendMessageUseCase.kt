package com.github.alphapaca.claudeclient.domain.usecase

import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager
import com.github.alphapaca.claudeclient.data.mcp.MCPTool
import com.github.alphapaca.claudeclient.data.repository.ConversationRepository
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class SendMessageUseCase(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val mcpClientManager: MCPClientManager,
) {
    /**
     * Sends a message to the conversation.
     * If conversationId is null, creates a new conversation.
     * @return the actual conversationId used (may be newly created)
     */
    suspend operator fun invoke(conversationId: String?, message: String): String {
        val model = settingsRepository.getModel()
        val userSystemPrompt = settingsRepository.getSystemPrompt()
        val temperature = settingsRepository.getTemperature()
        val maxTokens = settingsRepository.getMaxTokens()
        val tools = mcpClientManager.availableTools.value

        // Build effective system prompt with tool guidance
        val effectiveSystemPrompt = buildSystemPrompt(userSystemPrompt, tools)

        return conversationRepository.sendMessage(
            conversationId = conversationId,
            message = message,
            model = model,
            systemPrompt = effectiveSystemPrompt,
            temperature = temperature,
            maxTokens = maxTokens,
            tools = tools,
        )
    }

    private fun buildSystemPrompt(userPrompt: String, tools: List<MCPTool>): String {
        if (tools.isEmpty()) {
            return userPrompt
        }

        val toolGuidance = buildString {
            appendLine("You have access to the following tools that you MUST use when appropriate:")
            appendLine()
            tools.forEach { tool ->
                appendLine("- ${tool.name}: ${tool.description ?: "No description"}")
            }
            appendLine()
            appendLine("When the user asks you to do something that a tool can accomplish (like setting reminders, fetching news), you MUST call the appropriate tool instead of just responding with text.")
        }

        return if (userPrompt.isBlank()) {
            toolGuidance
        } else {
            "$userPrompt\n\n$toolGuidance"
        }
    }
}