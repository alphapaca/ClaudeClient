package com.github.alphapaca.webagent.presentation.qa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.alphapaca.webagent.presentation.components.*

@Composable
fun QAScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel = remember { QAViewModel() }
    val qaItems by viewModel.qaItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new items are added
    LaunchedEffect(qaItems.size) {
        if (qaItems.isNotEmpty()) {
            listState.animateScrollToItem(qaItems.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "ClaudeClient Q&A",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Ask questions about the codebase",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    when (val status = serverStatus) {
                        is ServerStatus.Connected -> StatusBadge(
                            connected = true,
                            codeChunksIndexed = status.codeChunksIndexed,
                        )
                        ServerStatus.Disconnected -> StatusBadge(
                            connected = false,
                            codeChunksIndexed = null,
                        )
                        ServerStatus.Unknown -> {}
                    }
                }

                if (qaItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.clearHistory() },
                    ) {
                        Text("Clear history")
                    }
                }
            }
        }

        // Q&A conversation
        if (qaItems.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Welcome!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Ask me anything about the ClaudeClient codebase.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Example questions:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExampleQuestionChip(
                            text = "How does the ChatViewModel work?",
                            onClick = {
                                inputText = "How does the ChatViewModel work?"
                            }
                        )
                        ExampleQuestionChip(
                            text = "What database is used in this project?",
                            onClick = {
                                inputText = "What database is used in this project?"
                            }
                        )
                        ExampleQuestionChip(
                            text = "How are MCP tools integrated?",
                            onClick = {
                                inputText = "How are MCP tools integrated?"
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(qaItems) { item ->
                    when (item) {
                        is QAItem.Question -> QuestionBubble(text = item.text)
                        is QAItem.Answer -> AnswerCard(
                            content = item.content,
                            sources = item.sources,
                            processingTimeMs = item.processingTimeMs,
                        )
                        is QAItem.Error -> ErrorCard(message = item.message)
                        QAItem.Loading -> LoadingIndicator()
                    }
                }
            }
        }

        // Input area
        QuestionInput(
            text = inputText,
            onTextChange = { inputText = it },
            onSubmit = {
                viewModel.askQuestion(inputText)
                inputText = ""
            },
            enabled = !isLoading,
        )
    }
}

@Composable
private fun ExampleQuestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(text)
    }
}
