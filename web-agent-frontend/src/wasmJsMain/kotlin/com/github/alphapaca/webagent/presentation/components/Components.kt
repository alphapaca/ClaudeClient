package com.github.alphapaca.webagent.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.alphapaca.webagent.data.api.SourceReference
import com.github.alphapaca.webagent.data.api.SourceType

@Composable
fun QuestionBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 600.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
fun AnswerCard(
    content: String,
    sources: List<SourceReference>,
    processingTimeMs: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 800.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Answer content with markdown support
                MarkdownText(
                    text = content,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Sources",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sources.forEach { source ->
                            SourceChip(source = source)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Answered in ${processingTimeMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
fun SourceChip(
    source: SourceReference,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor, icon) = when (source.type) {
        SourceType.CODE -> Triple(
            Color(0xFF1E88E5).copy(alpha = 0.1f),
            Color(0xFF1E88E5),
            "{ }"
        )
        SourceType.DOCUMENTATION -> Triple(
            Color(0xFF43A047).copy(alpha = 0.1f),
            Color(0xFF43A047),
            "#"
        )
        SourceType.GITHUB_ISSUE -> Triple(
            Color(0xFF8E24AA).copy(alpha = 0.1f),
            Color(0xFF8E24AA),
            "!"
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            Text(
                text = source.title,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
            )
        }
    }
}

@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.widthIn(max = 600.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Error: $message",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun QuestionInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Ask a question about the codebase...") },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )

            Button(
                onClick = onSubmit,
                enabled = enabled && text.isNotBlank(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text("Ask")
            }
        }
    }
}

@Composable
fun StatusBadge(
    connected: Boolean,
    codeChunksIndexed: Int?,
    modifier: Modifier = Modifier,
) {
    val (color, text) = if (connected) {
        Color(0xFF43A047) to "Connected (${codeChunksIndexed ?: 0} chunks indexed)"
    } else {
        Color(0xFFE53935) to "Disconnected"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}
