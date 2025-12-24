package com.github.alphapaca.claudeclient.domain.usecase

import ClaudeClient.composeApp.BuildConfig
import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager
import com.github.alphapaca.claudeclient.data.mcp.MCPServerConfig
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository

class AutoConnectMCPServerUseCase(
    private val settingsRepository: SettingsRepository,
    private val mcpClientManager: MCPClientManager,
) {
    suspend operator fun invoke() {
        // Initialize default servers on first launch
        if (!settingsRepository.isMcpServersInitialized()) {
            Logger.i(TAG) { "First launch - initializing default MCP servers" }
            initializeDefaultServers()
            settingsRepository.setMcpServersInitialized()
        }

        val servers = settingsRepository.getMcpServers()

        if (servers.isEmpty()) {
            Logger.i(TAG) { "No MCP servers configured for auto-connect" }
            return
        }

        Logger.i(TAG) { "Auto-connecting to ${servers.size} MCP server(s)" }

        for (config in servers) {
            Logger.i(TAG) { "Auto-connecting to MCP server: ${config.name}" }

            mcpClientManager.connectServer(config)
                .onSuccess {
                    Logger.i(TAG) { "Auto-connected to ${config.name} successfully" }
                }
                .onFailure { error ->
                    Logger.e(TAG, error) { "Failed to auto-connect to ${config.name}" }
                }
        }
    }

    private suspend fun initializeDefaultServers() {
        val defaultServers = listOf(
            MCPServerConfig(
                name = "mcp-server",
                command = "java",
                args = listOf("-jar", BuildConfig.MCP_SERVER_JAR_PATH),
            ),
            MCPServerConfig(
                name = "recipe-book",
                command = "java",
                args = listOf("-jar", BuildConfig.RECIPE_BOOK_MCP_JAR_PATH),
            ),
            MCPServerConfig(
                name = "foundation-search",
                command = "java",
                args = listOf("-jar", BuildConfig.FOUNDATION_SEARCH_MCP_JAR_PATH),
                env = mapOf("VOYAGEAI_API_KEY" to BuildConfig.VOYAGEAI_API_KEY),
            ),
        )

        for (server in defaultServers) {
            settingsRepository.addMcpServer(server)
            Logger.i(TAG) { "Added default MCP server: ${server.name}" }
        }
    }

    companion object {
        private const val TAG = "AutoConnectMCP"
    }
}
