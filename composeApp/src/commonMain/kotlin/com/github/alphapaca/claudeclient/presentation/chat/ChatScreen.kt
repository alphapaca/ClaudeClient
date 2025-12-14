package com.github.alphapaca.claudeclient.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.alphapaca.claudeclient.domain.model.ConversationInfo
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.domain.model.StopReason
import com.github.alphapaca.claudeclient.presentation.widgets.BikeRecommendationCard
import com.github.alphapaca.claudeclient.presentation.widgets.FancyWeatherWidget
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {},
) {
    val viewModel = koinViewModel<ChatViewModel>()
    val chatItems by viewModel.chatItems.collectAsState(emptyList())
    val tokensUsed by viewModel.tokensUsed.collectAsState(0)
    val totalCost by viewModel.totalCost.collectAsState(0.0)
    val temperature by viewModel.temperature.collectAsState(null)
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val conversations by viewModel.conversations.collectAsState(emptyList())
    val currentConversationId by viewModel.currentConversationId.collectAsState(-1L)

    var inputText by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawerContent(
                conversations = conversations,
                currentConversationId = currentConversationId,
                onNewConversation = {
                    viewModel.createNewConversation()
                    scope.launch { drawerState.close() }
                },
                onConversationSelected = { conversationId ->
                    viewModel.switchConversation(conversationId)
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        ChatScreenContent(
            modifier = modifier,
            chatItems = chatItems,
            tokensUsed = tokensUsed,
            totalCost = totalCost,
            temperature = temperature,
            isLoading = isLoading,
            error = error,
            inputText = inputText,
            onInputTextChange = { inputText = it },
            onSendMessage = { message ->
                viewModel.sendMessage(message)
                inputText = ""
            },
            onSuggestClick = viewModel::onSuggestClick,
            onCompactClick = viewModel::compactConversation,
            onDeleteClick = viewModel::deleteCurrentConversation,
            onSettingsClick = onSettingsClick,
            onMenuClick = { scope.launch { drawerState.open() } },
        )
    }
}

@Composable
private fun ConversationDrawerContent(
    conversations: List<ConversationInfo>,
    currentConversationId: Long,
    onNewConversation: () -> Unit,
    onConversationSelected: (Long) -> Unit,
) {
    ModalDrawerSheet {
        Text(
            text = "Conversations",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text("New conversation") },
            selected = false,
            onClick = onNewConversation,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn {
            items(conversations, key = { it.id }) { conversation ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = conversation.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    selected = conversation.id == currentConversationId,
                    onClick = { onConversationSelected(conversation.id) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreenContent(
    modifier: Modifier,
    chatItems: List<ChatItem>,
    tokensUsed: Int,
    totalCost: Double,
    temperature: Double?,
    isLoading: Boolean,
    error: String?,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onSuggestClick: (ChatItem.Suggest) -> Unit,
    onCompactClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Claude Chat")
                    val temperatureText = temperature?.let { "%.1f".format(it) } ?: "default"
                    val costText = "%.4f".format(totalCost)
                    Text(
                        text = "Tokens: $tokensUsed | \$$costText | Temp: $temperatureText",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            },
            actions = {
                IconButton(onClick = onCompactClick) {
                    Icon(Icons.Default.Compress, contentDescription = "Compact conversation")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete conversation")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        )
        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            items(chatItems) { chatItem ->
                when (chatItem) {
                    is ChatItem.Conversation -> ConversationItemWidget(chatItem.item)
                    is ChatItem.SuggestGroup -> FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chatItem.suggests.forEach { suggest ->
                            SuggestionChip(
                                onClick = { onSuggestClick(suggest) },
                                label = { Text(suggest.label) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isLoading) {
                item {
                    CircularProgressIndicator()
                }
            }
        }

        // Error display
        error?.let {
            Text(
                text = "Error: $it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        }

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier.weight(1f)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Enter && inputText.isNotBlank()) {
                            onSendMessage(inputText.trim())
                            true
                        } else {
                            false
                        }
                    },
                placeholder = { Text("Type a message...") }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                    }
                },
                enabled = !isLoading && inputText.isNotBlank()
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun ConversationItemWidget(item: ConversationItem) {
    when (item) {
        is ConversationItem.User -> UserBubble(item)
        is ConversationItem.Assistant -> AssistantBubble(item)
        is ConversationItem.Summary -> SummaryCard(item)
    }
}

@Composable
private fun SummaryCard(summary: ConversationItem.Summary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "${summary.compactedMessageCount} messages compacted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    text = summary.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(message: ConversationItem.User) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "User",
                style = MaterialTheme.typography.labelSmall
            )
            SelectionContainer {
                Text(text = message.content)
            }
        }
    }
}

@Composable
private fun AssistantBubble(message: ConversationItem.Assistant) {
    val stopReasonInfo = message.stopReason.toDisplayInfo()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = message.model.displayName,
                    style = MaterialTheme.typography.labelSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stopReasonInfo?.let { (label, isWarning) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isWarning) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = formatInferenceTime(message.inferenceTimeMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            message.content.forEach { block ->
                ContentBlockWidget(block)
            }
        }
    }
}

@Composable
private fun ContentBlockWidget(block: ConversationItem.ContentBlock) {
    when (block) {
        is ConversationItem.ContentBlock.Text -> SelectionContainer {
            Markdown(content = block.text)
        }
        is ConversationItem.ContentBlock.WeatherData -> FancyWeatherWidget(block)
        is ConversationItem.ContentBlock.BikeData -> BikeRecommendationCard(block)
    }
}

private fun StopReason.toDisplayInfo(): Pair<String, Boolean>? = when (this) {
    StopReason.END_TURN -> null
    StopReason.MAX_TOKENS -> "truncated" to true
    StopReason.STOP_SEQUENCE -> "stop seq" to false
    StopReason.TOOL_USE -> "tool use" to false
    StopReason.CONTENT_FILTER -> "filtered" to true
    StopReason.UNKNOWN -> "unknown" to false
}

private fun formatInferenceTime(timeMs: Long): String {
    return when {
        timeMs < 1000 -> "${timeMs}ms"
        else -> "%.1fs".format(timeMs / 1000.0)
    }
}

private val ChatItem.Suggest.label: String
    get() = when (this) {
        ChatItem.Suggest.GetWeather -> "Get weather"
        ChatItem.Suggest.GetABike -> "Get a bike"
        ChatItem.Suggest.GetABikeSystemPrompt -> "Get a bike with system prompt"
    }