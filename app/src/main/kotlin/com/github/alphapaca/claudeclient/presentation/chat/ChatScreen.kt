package com.github.alphapaca.claudeclient.presentation.chat

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.alphapaca.claudeclient.domain.model.ConversationItem
import com.github.alphapaca.claudeclient.presentation.weather.FancyWeatherWidget
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun ChatScreen(modifier: Modifier) {
    val viewModel = koinViewModel<ChatViewModel>()
    val chatItems by viewModel.chatItems.collectAsState(emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var inputText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            items(chatItems) { chatItem ->
                when (chatItem) {
                    is ChatItem.Conversation -> when(chatItem.item) {
                        is ConversationItem.Text -> MessageBubble(chatItem.item)
                        is ConversationItem.WeatherData -> FancyWeatherWidget(chatItem.item)
                    }
                    is ChatItem.Suggest -> SuggestionChip(
                        onClick = { viewModel.onSuggestClick(chatItem) },
                        label = { Text(chatItem.label) }
                    )
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
                modifier = Modifier.weight(1f),
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
    }