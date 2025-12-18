package com.github.alphapaca

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

fun main(): Unit = runBlocking {
    val hackerNewsService = HackerNewsService()
    val reminderService = ReminderService()
    val json = Json { prettyPrint = true }

    val server = Server(
        serverInfo = Implementation(
            name = "mcp-server",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
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

    // Reminder tools
    server.addTool(
        name = "remind",
        description = """Create a reminder that will notify the user at a specified time.
            |Supported time formats:
            |- "in X minutes/hours/days" (e.g., "in 30 minutes", "in 2 hours")
            |- "at HH:mm" (e.g., "at 14:30" - today or tomorrow if already past)
            |- "YYYY-MM-DD HH:mm" (e.g., "2024-12-25 10:00")""".trimMargin(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("message", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("The reminder message to display when triggered"))
                })
                put("time", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("When to trigger the reminder (e.g., 'in 30 minutes', 'at 14:30', '2024-12-25 10:00')"))
                })
                put("conversation_id", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Optional: conversation ID to inject the reminder message into"))
                })
            },
            required = listOf("message", "time")
        )
    ) { request ->
        try {
            val message = request.arguments?.get("message")?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: 'message' is required")),
                    isError = true
                )
            val time = request.arguments?.get("time")?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: 'time' is required")),
                    isError = true
                )
            val conversationId = request.arguments?.get("conversation_id")?.jsonPrimitive?.content

            val reminder = reminderService.createReminder(message, time, conversationId)

            val triggerTime = java.time.Instant.ofEpochMilli(reminder.triggerAt)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            CallToolResult(
                content = listOf(
                    TextContent(text = "Reminder created successfully!\nID: ${reminder.id}\nMessage: ${reminder.message}\nWill trigger at: $triggerTime")
                )
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error creating reminder: ${e.message}")),
                isError = true
            )
        }
    }

    server.addTool(
        name = "list_reminders",
        description = "List all active (non-delivered) reminders",
        inputSchema = ToolSchema(
            properties = buildJsonObject {},
            required = emptyList()
        )
    ) { _ ->
        try {
            val reminders = reminderService.getActiveReminders()
            if (reminders.isEmpty()) {
                CallToolResult(content = listOf(TextContent(text = "No active reminders.")))
            } else {
                val text = reminders.mapIndexed { index, r ->
                    val triggerTime = java.time.Instant.ofEpochMilli(r.triggerAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    "${index + 1}. [ID: ${r.id}] \"${r.message}\" - triggers at $triggerTime"
                }.joinToString("\n")
                CallToolResult(content = listOf(TextContent(text = "Active reminders:\n$text")))
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error listing reminders: ${e.message}")),
                isError = true
            )
        }
    }

    server.addTool(
        name = "delete_reminder",
        description = "Delete a reminder by its ID",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("The reminder ID to delete"))
                })
            },
            required = listOf("id")
        )
    ) { request ->
        try {
            val id = request.arguments?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: 'id' is required and must be a number")),
                    isError = true
                )

            val deleted = reminderService.deleteReminder(id)
            if (deleted) {
                CallToolResult(content = listOf(TextContent(text = "Reminder $id deleted successfully.")))
            } else {
                CallToolResult(content = listOf(TextContent(text = "Reminder $id not found.")))
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error deleting reminder: ${e.message}")),
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
        reminderService.close()
        done.complete()
    }

    // Connect first, then start the reminder scheduler
    val session = server.connect(transport)

    // Start the reminder scheduler and emit notifications when reminders are due
    reminderService.startScheduler()
    launch {
        reminderService.dueReminders.collect { reminder ->
            // URL-encode the message for safe transmission in URI
            val encodedMessage = java.net.URLEncoder.encode(reminder.message, "UTF-8")
            // Emit resource update notification to the connected session
            val notification = ResourceUpdatedNotification(
                ResourceUpdatedNotificationParams(uri = "reminder://${reminder.id}?message=$encodedMessage")
            )
            try {
                System.err.println("MCP: Sending reminder notification for ID ${reminder.id}: ${reminder.message}")
                session.sendResourceUpdated(notification)
                System.err.println("MCP: Sent reminder notification successfully")
            } catch (e: Exception) {
                System.err.println("MCP: Failed to send notification: ${e.message}")
                e.printStackTrace(System.err)
            }
        }
    }

    done.join()
}
