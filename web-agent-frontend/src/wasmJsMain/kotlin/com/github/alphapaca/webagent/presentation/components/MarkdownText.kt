package com.github.alphapaca.webagent.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Simple markdown renderer for Compose.
 * Supports: headers, bold, italic, code blocks, inline code, lists, links.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.CodeBlock -> CodeBlockView(
                        code = block.code,
                        language = block.language,
                    )
                    is MarkdownBlock.Paragraph -> ParagraphView(
                        content = block.content,
                        color = color,
                    )
                    is MarkdownBlock.Header -> HeaderView(
                        content = block.content,
                        level = block.level,
                    )
                    is MarkdownBlock.ListItem -> ListItemView(
                        content = block.content,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(
    code: String,
    language: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
    ) {
        if (!language.isNullOrBlank()) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF888888),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = code.trimEnd(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
                color = Color(0xFFD4D4D4),
            )
        }
    }
}

@Composable
private fun ParagraphView(
    content: String,
    color: Color,
) {
    val annotatedString = remember(content, color) { parseInlineMarkdown(content, color) }
    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun HeaderView(
    content: String,
    level: Int,
) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }
    Text(
        text = content,
        style = style,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ListItemView(
    content: String,
    color: Color,
) {
    val annotatedString = remember(content, color) { parseInlineMarkdown(content, color) }
    Row(modifier = Modifier.padding(start = 8.dp)) {
        Text(
            text = "•  ",
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// Markdown block types
private sealed class MarkdownBlock {
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock()
    data class Paragraph(val content: String) : MarkdownBlock()
    data class Header(val content: String, val level: Int) : MarkdownBlock()
    data class ListItem(val content: String) : MarkdownBlock()
}

// Parse markdown into blocks
private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code block
        if (line.trimStart().startsWith("```")) {
            val language = line.trimStart().removePrefix("```").trim().takeIf { it.isNotEmpty() }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language))
            i++ // Skip closing ```
            continue
        }

        // Header
        val headerMatch = Regex("^(#{1,4})\\s+(.+)$").find(line.trim())
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val content = headerMatch.groupValues[2]
            blocks.add(MarkdownBlock.Header(content, level))
            i++
            continue
        }

        // List item
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            val content = line.trimStart().drop(2)
            blocks.add(MarkdownBlock.ListItem(content))
            i++
            continue
        }

        // Numbered list
        val numberedMatch = Regex("^\\d+\\.\\s+(.+)$").find(line.trimStart())
        if (numberedMatch != null) {
            blocks.add(MarkdownBlock.ListItem(numberedMatch.groupValues[1]))
            i++
            continue
        }

        // Empty line - skip
        if (line.isBlank()) {
            i++
            continue
        }

        // Regular paragraph - collect consecutive non-empty, non-special lines
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            val currentLine = lines[i]
            if (currentLine.isBlank() ||
                currentLine.trimStart().startsWith("```") ||
                currentLine.trim().startsWith("#") ||
                currentLine.trimStart().startsWith("- ") ||
                currentLine.trimStart().startsWith("* ") ||
                Regex("^\\d+\\.\\s+").containsMatchIn(currentLine.trimStart())
            ) {
                break
            }
            paragraphLines.add(currentLine)
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString(" ")))
        }
    }

    return blocks
}

// Parse inline markdown (bold, italic, code, links)
private fun parseInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val codeColor = Color(0xFFE91E63)
        val codeBackground = Color(0xFFF5F5F5)

        while (i < text.length) {
            // Inline code
            if (text[i] == '`' && i + 1 < text.length) {
                val endIndex = text.indexOf('`', i + 1)
                if (endIndex != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor,
                            background = codeBackground,
                            fontSize = 14.sp,
                        )
                    ) {
                        append(" ${text.substring(i + 1, endIndex)} ")
                    }
                    i = endIndex + 1
                    continue
                }
            }

            // Bold **text**
            if (i + 1 < text.length && text.substring(i, i + 2) == "**") {
                val endIndex = text.indexOf("**", i + 2)
                if (endIndex != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                        append(text.substring(i + 2, endIndex))
                    }
                    i = endIndex + 2
                    continue
                }
            }

            // Italic *text* (but not **)
            if (text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*')) {
                val endIndex = text.indexOf('*', i + 1)
                if (endIndex != -1 && (endIndex + 1 >= text.length || text[endIndex + 1] != '*')) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) {
                        append(text.substring(i + 1, endIndex))
                    }
                    i = endIndex + 1
                    continue
                }
            }

            // Link [text](url)
            if (text[i] == '[') {
                val closeTextIndex = text.indexOf(']', i + 1)
                if (closeTextIndex != -1 && closeTextIndex + 1 < text.length && text[closeTextIndex + 1] == '(') {
                    val closeUrlIndex = text.indexOf(')', closeTextIndex + 2)
                    if (closeUrlIndex != -1) {
                        val linkText = text.substring(i + 1, closeTextIndex)
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF1976D2),
                                fontWeight = FontWeight.Medium,
                            )
                        ) {
                            append(linkText)
                        }
                        i = closeUrlIndex + 1
                        continue
                    }
                }
            }

            // Regular character
            withStyle(SpanStyle(color = defaultColor)) {
                append(text[i])
            }
            i++
        }
    }
}
