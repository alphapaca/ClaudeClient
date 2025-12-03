package com.github.alphapaca.claudeclient.presentation.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.alphapaca.claudeclient.data.api.Message
import com.github.alphapaca.claudeclient.presentation.weather.FancyWeatherWidget
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun ChatScreen(modifier: Modifier) {
    val viewModel = koinViewModel<ChatViewModel>()
    val chatItems by viewModel.chatItems.collectAsState()
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
                    is ChatItem.Text -> MessageBubble(chatItem.message)
                    is ChatItem.Weather -> FancyWeatherWidget(chatItem.weatherData)
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
                        viewModel.sendUserMessage(inputText)
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
private fun MessageBubble(message: Message) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.role == "user")
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message.role.capitalize(Locale.ROOT),
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
        ChatItem.Suggest.ShowWeatherInRandomCity -> "Weather in random city"
    }