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
        val command = settingsRepository.getMcpServerCommand().ifBlank {
            // Use built-in hn-mcp server as default
            DEFAULT_MCP_COMMAND
        }

        val parts = command.split(" ").filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            Logger.w(TAG) { "Invalid MCP server command: $command" }
            return
        }

        val config = MCPServerConfig(
            name = MCP_SERVER_NAME,
            command = parts.first(),
            args = parts.drop(1),
        )

        Logger.i(TAG) { "Auto-connecting to MCP server: $command" }

        mcpClientManager.connectServer(config)
            .onSuccess {
                Logger.i(TAG) { "Auto-connected to MCP server successfully" }
            }
            .onFailure { error ->
                Logger.e(TAG, error) { "Failed to auto-connect to MCP server" }
            }
    }

    companion object {
        private const val TAG = "AutoConnectMCP"
        private const val MCP_SERVER_NAME = "mcp-server"
        private val DEFAULT_MCP_COMMAND = "java -jar ${BuildConfig.HN_MCP_JAR_PATH}"
    }
}
