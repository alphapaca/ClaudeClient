package com.github.alphapaca.claudeclient.presentation.systemanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisMessage
import com.github.alphapaca.claudeclient.domain.model.SystemAnalysisSessionInfo
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemAnalysisScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
) {
    val viewModel = koinViewModel<SystemAnalysisViewModel>()
    val sessions by viewModel.sessions.collectAsState(emptyList())
    val currentSession by viewModel.currentSession.collectAsState(null)
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val messages by viewModel.messages.collectAsState(emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showCreateDialog by viewModel.showCreateDialog.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    if (showCreateDialog) {
        CreateSystemAnalysisSessionDialog(
            onDismiss = viewModel::dismissCreateDialog,
            onCreate = viewModel::createSession,
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SystemAnalysisDrawerContent(
                sessions = sessions,
                currentSessionId = currentSessionId,
                onNewSession = {
                    viewModel.openCreateDialog()
                    scope.launch { drawerState.close() }
                },
                onSessionSelected = { sessionId ->
                    viewModel.selectSession(sessionId)
                    scope.launch { drawerState.close() }
                },
                onBackClick = {
                    scope.launch { drawerState.close() }
                    onBackClick()
                },
            )
        },
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(currentSession?.name ?: "System Analysis")
                        currentSession?.projectName?.let {
                            Text(
                                text = "Project: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    if (currentSessionId != null) {
                        IconButton(onClick = viewModel::deleteCurrentSession) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )

            // Error banner
            error?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // Messages
            val listState = rememberLazyListState()

            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                if (currentSession == null) {
                    item {
                        EmptySessionPlaceholder(onCreateSession = viewModel::openCreateDialog)
                    }
                } else if (messages.isEmpty()) {
                    item {
                        ReadyToAnalyze(projectName = currentSession?.projectName ?: "")
                    }
                }

                items(messages) { message ->
                    MessageBubble(message)
                }

                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // Input field
            if (currentSession != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .onKeyEvent { event ->
                                if (event.key == Key.Enter && inputText.isNotBlank() && !isLoading) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                    true
                                } else false
                            },
                        placeholder = { Text("Ask about system health, errors, metrics...") },
                        enabled = !isLoading,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemAnalysisDrawerContent(
    sessions: List<SystemAnalysisSessionInfo>,
    currentSessionId: String?,
    onNewSession: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    ModalDrawerSheet {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Chat")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "System Analysis",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text("New analysis session") },
            selected = false,
            onClick = onNewSession,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn {
            items(sessions, key = { it.id }) { session ->
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    label = {
                        Column {
                            Text(
                                text = session.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = session.projectName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    selected = session.id == currentSessionId,
                    onClick = { onSessionSelected(session.id) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: SystemAnalysisMessage) {
    val (containerColor, labelText, labelColor) = when (message) {
        is SystemAnalysisMessage.User -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            "You",
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        is SystemAnalysisMessage.Assistant -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            "Assistant (Local LLM)",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        is SystemAnalysisMessage.ToolResult -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            "Tool: ${message.toolName}",
            MaterialTheme.colorScheme.onTertiaryContainer
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message is SystemAnalysisMessage.ToolResult) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.height(16.dp).width(16.dp),
                        tint = labelColor,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            SelectionContainer {
                Markdown(
                    content = when (message) {
                        is SystemAnalysisMessage.User -> message.content
                        is SystemAnalysisMessage.Assistant -> message.content
                        is SystemAnalysisMessage.ToolResult -> "```json\n${message.result}\n```"
                    }
                )
            }

            if (message is SystemAnalysisMessage.Assistant) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${message.model.displayName} | ${message.inputTokens + message.outputTokens} tokens | ${message.inferenceTimeMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptySessionPlaceholder(onCreateSession: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Analytics,
                contentDescription = null,
                modifier = Modifier.height(64.dp).width(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Analysis Session Selected",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create a new session to start analyzing system data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadyToAnalyze(projectName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Ready to analyze!",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ask questions about your system's health, errors, and performance. The assistant uses a local LLM for privacy-focused analysis.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Try asking things like:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "• What errors are happening most frequently?\n• Is there a memory leak in the system?\n• Which services are experiencing issues?\n• Analyze the error trends from the last day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreateSystemAnalysisSessionDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var projectName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Analysis Session") },
        text = {
            Column {
                Text(
                    text = "Enter the project name for this analysis session.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("e.g., Production API, User Service") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(projectName) },
                enabled = projectName.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
