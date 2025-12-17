package com.github.alphapaca

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int

fun main(): Unit = runBlocking {
    val hackerNewsService = HackerNewsService()

    val server = Server(
        serverInfo = Implementation(
            name = "hackernews-mcp-server",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true)
            )
        )
    )

    server.addTool(
        name = "get_top_stories",
        description = "Get the top stories from Hacker News front page. Returns title, URL, author, score, and comment count for each story.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("limit", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("Number of stories to return (default: 10, max: 30)"))
                })
            },
            required = emptyList()
        )
    ) { request ->
        val limit = request.arguments?.get("limit")?.let { (it as? JsonPrimitive)?.int }?.coerceIn(1, 30) ?: 10

        try {
            val stories = hackerNewsService.getTopStories(limit)
            val content = stories.mapIndexed { index, story ->
                val url = story.url ?: "https://news.ycombinator.com/item?id=${story.id}"
                val comments = story.descendants ?: 0
                TextContent(
                    text = """
                    |${index + 1}. ${story.title}
                    |   URL: $url
                    |   Author: ${story.by} | Score: ${story.score} | Comments: $comments
                    |   HN Link: https://news.ycombinator.com/item?id=${story.id}
                    """.trimMargin()
                )
            }
            CallToolResult(content = content)
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error fetching stories: ${e.message}")),
                isError = true
            )
        }
    }

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )

    val done = Job()
    server.onClose {
        hackerNewsService.close()
        done.complete()
    }
    server.connect(transport)
    done.join()
}
