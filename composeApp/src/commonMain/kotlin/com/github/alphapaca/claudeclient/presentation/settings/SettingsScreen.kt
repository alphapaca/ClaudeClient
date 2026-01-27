package com.github.alphapaca.claudeclient.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.alphapaca.claudeclient.data.mcp.MCPServerConfig
import com.github.alphapaca.claudeclient.data.mcp.MCPTool
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val developerProfile by viewModel.developerProfile.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val maxTokens by viewModel.maxTokens.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val ollamaBaseUrl by viewModel.ollamaBaseUrl.collectAsState()
    val ollamaTestState by viewModel.ollamaTestState.collectAsState()
    val hasChanges by viewModel.hasChanges.collectAsState()

    // MCP multi-server state
    val mcpServers by viewModel.mcpServers.collectAsState()
    val connectedServers by viewModel.connectedServers.collectAsState()
    val connectingServers by viewModel.connectingServers.collectAsState()
    val serverErrors by viewModel.serverErrors.collectAsState()
    val mcpToolsByServer by viewModel.mcpToolsByServer.collectAsState()
    val showAddServerDialog by viewModel.showAddServerDialog.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Main", "MCP")

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> MainSettingsTab(
                    systemPrompt = systemPrompt,
                    onSystemPromptChange = viewModel::onSystemPromptChange,
                    developerProfile = developerProfile,
                    onDeveloperProfileChange = viewModel::onDeveloperProfileChange,
                    temperature = temperature,
                    onTemperatureChange = viewModel::onTemperatureChange,
                    maxTokens = maxTokens,
                    onMaxTokensChange = viewModel::onMaxTokensChange,
                    selectedModel = selectedModel,
                    onModelChange = viewModel::onModelChange,
                    ollamaBaseUrl = ollamaBaseUrl,
                    onOllamaBaseUrlChange = viewModel::onOllamaBaseUrlChange,
                    ollamaTestState = ollamaTestState,
                    onTestOllamaConnection = viewModel::testOllamaConnection,
                    hasChanges = hasChanges,
                    onSave = viewModel::saveSettings,
                )
                1 -> McpSettingsTab(
                    servers = mcpServers,
                    connectedServers = connectedServers,
                    connectingServers = connectingServers,
                    serverErrors = serverErrors,
                    toolsByServer = mcpToolsByServer,
                    onConnectServer = viewModel::connectServer,
                    onDisconnectServer = viewModel::disconnectServer,
                    onRemoveServer = viewModel::removeServer,
                    onShowAddDialog = viewModel::showAddServerDialog,
                )
            }
        }
    }

    if (showAddServerDialog) {
        AddMcpServerDialog(
            onDismiss = viewModel::hideAddServerDialog,
            onConfirm = { name, command ->
                viewModel.addServer(name, command)
                viewModel.hideAddServerDialog()
            }
        )
    }
}

@Composable
private fun MainSettingsTab(
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit,
    developerProfile: String,
    onDeveloperProfileChange: (String) -> Unit,
    temperature: String,
    onTemperatureChange: (String) -> Unit,
    maxTokens: String,
    onMaxTokensChange: (String) -> Unit,
    selectedModel: LLMModel,
    onModelChange: (LLMModel) -> Unit,
    ollamaBaseUrl: String,
    onOllamaBaseUrlChange: (String) -> Unit,
    ollamaTestState: OllamaTestState,
    onTestOllamaConnection: () -> Unit,
    hasChanges: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible = hasChanges,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                FloatingActionButton(onClick = onSave) {
                    Icon(Icons.Default.Check, contentDescription = "Save")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = onSystemPromptChange,
                label = { Text("System Prompt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = developerProfile,
                onValueChange = onDeveloperProfileChange,
                label = { Text("Developer Profile") },
                placeholder = { Text("Describe your preferred technologies, frameworks, and coding style...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = temperature,
                onValueChange = onTemperatureChange,
                label = { Text("Temperature [0-1]") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = maxTokens,
                onValueChange = onMaxTokensChange,
                label = { Text("Max Tokens [1-8192]") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Model",
                style = MaterialTheme.typography.labelMedium,
            )
            Column(modifier = Modifier.selectableGroup()) {
                LLMModel.entries.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = selectedModel == model,
                                onClick = { onModelChange(model) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedModel == model,
                            onClick = null,
                        )
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            if (selectedModel == LLMModel.LLAMA_3_2) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = ollamaBaseUrl,
                    onValueChange = onOllamaBaseUrlChange,
                    label = { Text("Ollama Server URL") },
                    placeholder = { Text("http://localhost:11434/") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onTestOllamaConnection,
                        enabled = ollamaTestState !is OllamaTestState.Testing,
                    ) {
                        if (ollamaTestState is OllamaTestState.Testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing...")
                        } else {
                            Text("Test Connection")
                        }
                    }
                    when (ollamaTestState) {
                        is OllamaTestState.Success -> {
                            Text(
                                text = ollamaTestState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        is OllamaTestState.Error -> {
                            Text(
                                text = ollamaTestState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun McpSettingsTab(
    servers: List<MCPServerConfig>,
    connectedServers: List<String>,
    connectingServers: Set<String>,
    serverErrors: Map<String, String>,
    toolsByServer: Map<String, List<MCPTool>>,
    onConnectServer: (String) -> Unit,
    onDisconnectServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onShowAddDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onShowAddDialog) {
                Icon(Icons.Default.Add, contentDescription = "Add Server")
            }
        }
    ) { padding ->
        if (servers.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No MCP servers configured.\nTap + to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(servers, key = { it.name }) { server ->
                    McpServerCard(
                        config = server,
                        isConnected = server.name in connectedServers,
                        isConnecting = server.name in connectingServers,
                        error = serverErrors[server.name],
                        tools = toolsByServer[server.name].orEmpty(),
                        onConnect = { onConnectServer(server.name) },
                        onDisconnect = { onDisconnectServer(server.name) },
                        onRemove = { onRemoveServer(server.name) },
                    )
                }
                item { Spacer(modifier = Modifier.height(72.dp)) } // Space for FAB
            }
        }
    }
}

@Composable
private fun McpServerCard(
    config: MCPServerConfig,
    isConnected: Boolean,
    isConnecting: Boolean,
    error: String?,
    tools: List<MCPTool>,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: Server name + delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onRemove,
                    enabled = !isConnected && !isConnecting,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = if (!isConnected && !isConnecting) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }

            // Command display
            val commandText = buildString {
                append(config.command)
                if (config.args.isNotEmpty()) {
                    append(" ")
                    append(config.args.joinToString(" "))
                }
            }
            Text(
                text = commandText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Connect/Disconnect button
            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting,
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting...")
                    } else {
                        Text("Connect")
                    }
                }
            }

            // Tools list (when connected)
            if (isConnected && tools.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${tools.size} tool(s) available:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        tools.forEach { tool ->
                            Text(
                                text = "\u2022 ${tool.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            // Error display
            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, command: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add MCP Server") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Command") },
                    placeholder = { Text("e.g., npx -y @modelcontextprotocol/server-weather") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), command.trim()) },
                enabled = name.isNotBlank() && command.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
