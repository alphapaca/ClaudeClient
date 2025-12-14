package com.github.alphapaca.claudeclient.data.parser

import com.github.alphapaca.claudeclient.domain.model.ConversationItem.ContentBlock
import kotlinx.serialization.json.Json

class ContentBlockParser(
    private val json: Json,
) {
    fun parse(content: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val jsonObjects = findJsonObjects(content)

        var lastEnd = 0
        jsonObjects.forEach { (jsonString, range) ->
            // Add text before this JSON block
            if (range.first > lastEnd) {
                val textBefore = content.substring(lastEnd, range.first).trim()
                if (textBefore.isNotEmpty()) {
                    blocks.add(ContentBlock.Text(textBefore))
                }
            }

            // Try to parse the JSON as a widget
            val widget = tryParseWidget(jsonString)
            if (widget != null) {
                blocks.add(widget)
            } else {
                // If parsing failed, keep it as text
                blocks.add(ContentBlock.Text(jsonString))
            }

            lastEnd = range.last + 1
        }

        // Add remaining text after last JSON block
        if (lastEnd < content.length) {
            val textAfter = content.substring(lastEnd).trim()
            if (textAfter.isNotEmpty()) {
                blocks.add(ContentBlock.Text(textAfter))
            }
        }

        // If no blocks were added (no JSON found), return the entire content as text
        return blocks.ifEmpty { listOf(ContentBlock.Text(content)) }
    }

    private fun findJsonObjects(content: String): List<Pair<String, IntRange>> {
        val results = mutableListOf<Pair<String, IntRange>>()
        var i = 0

        while (i < content.length) {
            if (content[i] == '{') {
                val start = i
                var braceCount = 1
                var inString = false
                var escape = false
                i++

                while (i < content.length && braceCount > 0) {
                    val c = content[i]
                    when {
                        escape -> escape = false
                        c == '\\' && inString -> escape = true
                        c == '"' -> inString = !inString
                        !inString && c == '{' -> braceCount++
                        !inString && c == '}' -> braceCount--
                    }
                    i++
                }

                if (braceCount == 0) {
                    val jsonString = content.substring(start, i)
                    // Only include if it looks like a widget JSON (has "type": "weather" or "type": "bike")
                    if (jsonString.contains(""""type"""") &&
                        (jsonString.contains(""""weather"""") || jsonString.contains(""""bike""""))) {
                        results.add(jsonString to (start until i))
                    }
                }
            } else {
                i++
            }
        }

        return results
    }

    private fun tryParseWidget(jsonString: String): ContentBlock.Widget? {
        return try {
            json.decodeFromString<ContentBlock.Widget>(jsonString)
        } catch (_: Exception) {
            null
        }
    }
}
