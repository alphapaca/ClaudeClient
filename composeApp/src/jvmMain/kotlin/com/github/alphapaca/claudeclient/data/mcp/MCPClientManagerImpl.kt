package com.github.alphapaca.claudeclient.data.mcp

import co.touchlab.kermit.Logger
import com.github.alphapaca.claudeclient.utils.runCatchingSuspend
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    init {
        // Register shutdown hook to clean up MCP server processes when app exits
        Runtime.getRuntime().addShutdownHook(Thread {
            Logger.i(TAG) { "Shutdown hook: cleaning up MCP server processes" }
            connections.values.forEach { connection ->
                try {
                    connection.process.destroyForcibly()
                    Logger.i(TAG) { "Killed MCP server process for: ${connection.config.name}" }
                } catch (e: Exception) {
                    Logger.e(TAG, e) { "Error killing MCP server process" }
                }
            }
        })
    }

    private val _connectedServers = MutableStateFlow<List<String>>(emptyList())
    override val connectedServers: StateFlow<List<String>> = _connectedServers.asStateFlow()

    private val _availableTools = MutableStateFlow<List<MCPTool>>(emptyList())
    override val availableTools: StateFlow<List<MCPTool>> = _availableTools.asStateFlow()

    private val _reminderNotifications = MutableSharedFlow<ReminderNotification>(extraBufferCapacity = 64)
    override val reminderNotifications: SharedFlow<ReminderNotification> = _reminderNotifications.asSharedFlow()

    override suspend fun connectServer(config: MCPServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingSuspend {
            if (connections.containsKey(config.name)) {
                throw IllegalStateException("Server '${config.name}' is already connected")
            }

            Logger.d(TAG) { "Starting MCP server: ${config.name} (${config.command} ${config.args.joinToString(" ")})" }

            // Strip surrounding quotes from args (users may copy-paste paths with quotes)
            val cleanedArgs = config.args.map { arg ->
                arg.trim().removeSurrounding("\"").removeSurrounding("'")
            }

            val processBuilder = ProcessBuilder(listOf(config.command) + cleanedArgs).apply {
                environment().putAll(config.env)
                redirectErrorStream(false)
            }

            val process = processBuilder.start()

            // Check if process exited immediately
            val exitedImmediately = process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (exitedImmediately) {
                val exitCode = process.exitValue()
                val errorOutput = process.errorStream.bufferedReader().readText()
                val stdOutput = process.inputStream.bufferedReader().readText()
                Logger.e(TAG) { "MCP server process exited immediately with code $exitCode" }
                if (errorOutput.isNotBlank()) {
                    Logger.e(TAG) { "stderr: $errorOutput" }
                }
                if (stdOutput.isNotBlank()) {
                    Logger.e(TAG) { "stdout: $stdOutput" }
                }
                throw IllegalStateException("MCP server process exited immediately with code $exitCode: $errorOutput")
            }

            val client = Client(
                clientInfo = Implementation(
                    name = "ClaudeClient",
                    version = "1.0.0"
                )
            )

            // Capture stderr separately before creating transport
            val errorStream = process.errorStream

            val transport = StdioClientTransport(
                input = process.inputStream.asSource().buffered(),
                output = process.outputStream.asSink().buffered(),
            )

            // Read stderr in background for debugging
            Thread {
                errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        Logger.w(TAG) { "MCP stderr: $line" }
                    }
                }
            }.start()

            // Set up notification handler BEFORE connecting
            client.setNotificationHandler<ResourceUpdatedNotification>(
                Method.Defined.NotificationsResourcesUpdated
            ) { notification ->
                val uri = notification.params.uri
                Logger.d(TAG) { "Received ResourceUpdatedNotification: $uri" }

                // Parse reminder:// URIs with format: reminder://{id}?message={encodedMessage}
                if (uri.startsWith("reminder://")) {
                    val uriParts = uri.removePrefix("reminder://")
                    val idPart = uriParts.substringBefore("?")
                    val reminderId = idPart.toLongOrNull()

                    // Extract and decode message from query parameter
                    val message = if (uriParts.contains("?message=")) {
                        val encodedMessage = uriParts.substringAfter("?message=")
                        try {
                            java.net.URLDecoder.decode(encodedMessage, "UTF-8")
                        } catch (e: Exception) {
                            Logger.w(TAG) { "Failed to decode message: $encodedMessage" }
                            null
                        }
                    } else null

                    _reminderNotifications.tryEmit(ReminderNotification(uri, reminderId, message))
                    Logger.i(TAG) { "Reminder notification emitted: $reminderId - $message" }
                }

                CompletableDeferred(Unit)
            }

            Logger.i(TAG) { "Connecting to MCP server..." }
            client.connect(transport)
            Logger.i(TAG) { "Connected to MCP server, notification handlers registered" }

            connections[config.name] = ServerConnection(config, client, process, transport)
            _connectedServers.update { it + config.name }

            Logger.i(TAG) { "Connected to MCP server: ${config.name}" }

            refreshTools()
        }
    }

    override suspend fun disconnectServer(serverName: String): Unit = withContext(Dispatchers.IO) {
        Logger.d(TAG) { "disconnectServer called for: $serverName" }
        connections.remove(serverName)?.let { connection ->
            runCatching {
                Logger.d(TAG) { "Force destroying process..." }
                connection.process.destroyForcibly()
                connection.process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                Logger.d(TAG) { "Process destroyed, closing transport..." }
                // Use withTimeout to prevent hanging on transport.close()
                kotlinx.coroutines.withTimeoutOrNull(2000) {
                    connection.transport.close()
                } ?: Logger.w(TAG) { "Transport close timed out" }
                Logger.d(TAG) { "Transport closed" }
            }.onFailure { e ->
                Logger.e(TAG, e) { "Error during disconnect" }
            }
            _connectedServers.update { it - serverName }
            refreshTools()
            Logger.i(TAG) { "Disconnected from MCP server: $serverName" }
        } ?: Logger.w(TAG) { "Server not found: $serverName" }
        Unit
    }

    override suspend fun disconnectAll() {
        connections.keys.toList().forEach { disconnectServer(it) }
    }

    override suspend fun listTools(): List<MCPTool> {
        val allTools = mutableListOf<MCPTool>()

        Logger.d(TAG) { "listTools called, connections: ${connections.keys}" }

        for ((serverName, connection) in connections) {
            runCatching {
                Logger.d(TAG) { "Fetching tools from server: $serverName" }
                val toolsResult = connection.client.listTools()
                Logger.d(TAG) { "Got ${toolsResult.tools.size} tools from $serverName: ${toolsResult.tools.map { it.name }}" }
                toolsResult.tools.forEach { tool ->
                    allTools.add(
                        MCPTool(
                            serverName = serverName,
                            name = tool.name,
                            description = tool.description,
                            inputSchemaProperties = tool.inputSchema.properties,
                            inputSchemaRequired = tool.inputSchema.required,
                        )
                    )
                }
            }.onFailure { e ->
                Logger.e(TAG, e) { "Failed to list tools from server: $serverName" }
            }
        }

        Logger.d(TAG) { "listTools returning ${allTools.size} tools: ${allTools.map { it.name }}" }
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
