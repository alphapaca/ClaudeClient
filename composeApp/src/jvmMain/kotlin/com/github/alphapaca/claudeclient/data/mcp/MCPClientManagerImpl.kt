package com.github.alphapaca.claudeclient.data.mcp

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.utils.runCatchingSuspend
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

class MCPClientManagerImpl : MCPClientManager {

    private data class ServerConnection(
        val config: MCPServerConfig,
        val client: Client,
        val process: Process,
        val transport: StdioClientTransport,
    )

    private val connections = mutableMapOf<String, ServerConnection>()

    private val _connectedServers = MutableStateFlow<List<String>>(emptyList())
    override val connectedServers: StateFlow<List<String>> = _connectedServers.asStateFlow()

    private val _availableTools = MutableStateFlow<List<MCPTool>>(emptyList())
    override val availableTools: StateFlow<List<MCPTool>> = _availableTools.asStateFlow()

    override suspend fun connectServer(config: MCPServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingSuspend {
            if (connections.containsKey(config.name)) {
                throw IllegalStateException("Server '${config.name}' is already connected")
            }

            Logger.d(TAG) { "Starting MCP server: ${config.name} (${config.command} ${config.args.joinToString(" ")})" }

            val processBuilder = ProcessBuilder(listOf(config.command) + config.args).apply {
                environment().putAll(config.env)
                redirectErrorStream(false)
            }

            val process = processBuilder.start()

            val client = Client(
                clientInfo = Implementation(
                    name = "ClaudeClient",
                    version = "1.0.0"
                )
            )

            val transport = StdioClientTransport(
                input = process.inputStream.asSource().buffered(),
                output = process.outputStream.asSink().buffered(),
                error = process.errorStream.asSource().buffered(),
            )

            client.connect(transport)

            connections[config.name] = ServerConnection(config, client, process, transport)
            _connectedServers.update { it + config.name }

            Logger.i(TAG) { "Connected to MCP server: ${config.name}" }

            refreshTools()
        }
    }

    override suspend fun disconnectServer(serverName: String): Unit = withContext(Dispatchers.IO) {
        connections.remove(serverName)?.let { connection ->
            runCatching {
                connection.transport.close()
                connection.process.destroy()
            }
            _connectedServers.update { it - serverName }
            refreshTools()
            Logger.i(TAG) { "Disconnected from MCP server: $serverName" }
        }
        Unit
    }

    override suspend fun disconnectAll() {
        connections.keys.toList().forEach { disconnectServer(it) }
    }

    override suspend fun listTools(): List<MCPTool> {
        val allTools = mutableListOf<MCPTool>()

        for ((serverName, connection) in connections) {
            runCatching {
                val toolsResult = connection.client.listTools()
                toolsResult.tools.forEach { tool ->
                    allTools.add(
                        MCPTool(
                            serverName = serverName,
                            name = tool.name,
                            description = tool.description,
                            inputSchema = tool.inputSchema.properties,
                        )
                    )
                }
            }.onFailure { e ->
                Logger.e(TAG, e) { "Failed to list tools from server: $serverName" }
            }
        }

        return allTools
    }

    override suspend fun callTool(
        serverName: String,
        toolName: String,
        arguments: Map<String, Any?>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = connections[serverName]
                ?: throw IllegalStateException("Server '$serverName' is not connected")

            Logger.d(TAG) { "Calling tool $toolName on server $serverName with args: $arguments" }

            val result = connection.client.callTool(
                name = toolName,
                arguments = arguments,
            )

            val content = result.content.joinToString("\n") { contentItem ->
                contentItem.toString()
            }

            Logger.d(TAG) { "Tool result from $toolName: $content" }
            content
        }
    }

    private suspend fun refreshTools() {
        _availableTools.value = listTools()
    }

    private companion object {
        const val TAG = "MCPClientManager"
    }
}
