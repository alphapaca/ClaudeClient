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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.presentation.widgets.BikeRecommendationCard
import com.github.alphapaca.claudeclient.presentation.widgets.FancyWeatherWidget
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {},
) {
    val viewModel = koinViewModel<ChatViewModel>()
    val chatItems by viewModel.chatItems.collectAsState(emptyList())
    val tokensUsed by viewModel.tokensUsed.collectAsState(0)
    val temperature by viewModel.temperature.collectAsState(null)
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var inputText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Claude Chat")
                    val temperatureText = temperature?.let { "%.1f".format(it) } ?: "default"
                    Text(
                        text = "Tokens: $tokensUsed | Temp: $temperatureText",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = { viewModel.clearMessages() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear messages")
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
                                onClick = { viewModel.onSuggestClick(suggest) },
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
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Enter && inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
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
                        viewModel.sendMessage(inputText)
                        inputText = ""
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
        is ConversationItem.Text -> MessageBubble(item)
        is ConversationItem.WeatherData -> FancyWeatherWidget(item)
        is ConversationItem.BikeData -> BikeRecommendationCard(item)
        is ConversationItem.Composed -> item.parts.forEach { ConversationItemWidget(it) }
    }
}

@Composable
private fun MessageBubble(message: ConversationItem.Text) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.role == ConversationItem.Text.Role.USER)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message.role.name.lowercase().capitalize(Locale.ROOT),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private val ChatItem.Suggest.label: String
    get() = when (this) {
        ChatItem.Suggest.GetWeather -> "Get weather"
        ChatItem.Suggest.GetABike -> "Get a bike"
        ChatItem.Suggest.GetABikeSystemPrompt -> "Get a bike with system prompt"
    }