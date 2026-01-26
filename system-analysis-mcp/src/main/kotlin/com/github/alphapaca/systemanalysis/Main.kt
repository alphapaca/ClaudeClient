package com.github.alphapaca.systemanalysis

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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

fun main(): Unit = runBlocking {
    val errorLogService = ErrorLogService()
    val systemMetricsService = SystemMetricsService()
    val json = Json { prettyPrint = true }

    val server = Server(
        serverInfo = Implementation(
            name = "system-analysis-mcp",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            )
        )
    )

    // Tool: Search Error Logs
    server.addTool(
        name = "search_error_logs",
        description = """Search and analyze error logs from the system.
            |Returns error entries with stacktrace, message, timestamp, severity, and occurrence count.
            |Use this to diagnose issues, find error patterns, and investigate system problems.""".trimMargin(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Search query to filter errors (e.g., 'NullPointer', 'timeout', 'database')"))
                })
                put("severity", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Filter by severity level: ERROR, WARN, FATAL (default: all)"))
                    put("enum", buildJsonObject {
                        // Using array-like structure for enum values
                    })
                })
                put("limit", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("Maximum number of error entries to return (default: 10, max: 50)"))
                })
                put("time_range", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Time range to search: 'last_hour', 'last_day', 'last_week' (default: last_day)"))
                })
            },
            required = emptyList()
        )
    ) { request ->
        try {
            val query = request.arguments?.get("query")?.jsonPrimitive?.content
            val severity = request.arguments?.get("severity")?.jsonPrimitive?.content
            val limit = request.arguments?.get("limit")?.let { (it as? JsonPrimitive)?.int }?.coerceIn(1, 50) ?: 10
            val timeRange = request.arguments?.get("time_range")?.jsonPrimitive?.content ?: "last_day"

            val errors = errorLogService.searchErrors(
                query = query,
                severity = severity,
                limit = limit,
                timeRange = timeRange
            )

            if (errors.isEmpty()) {
                CallToolResult(
                    content = listOf(TextContent(text = "No error logs found matching the criteria."))
                )
            } else {
                val resultJson = json.encodeToString(errors)
                CallToolResult(
                    content = listOf(
                        TextContent(text = "Found ${errors.size} error entries:\n\n$resultJson")
                    )
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error searching logs: ${e.message}")),
                isError = true
            )
        }
    }

    // Tool: Get System Metrics
    server.addTool(
        name = "get_system_metrics",
        description = """Retrieve current system performance metrics.
            |Returns CPU usage, memory usage, disk I/O, active connections, and request rates.
            |Use this to monitor system health and identify performance bottlenecks.""".trimMargin(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("metric_type", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Type of metrics: 'all', 'cpu', 'memory', 'disk', 'network' (default: all)"))
                })
            },
            required = emptyList()
        )
    ) { request ->
        try {
            val metricType = request.arguments?.get("metric_type")?.jsonPrimitive?.content ?: "all"
            val metrics = systemMetricsService.getMetrics(metricType)
            val resultJson = json.encodeToString(metrics)
            CallToolResult(
                content = listOf(TextContent(text = "System Metrics:\n\n$resultJson"))
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error fetching metrics: ${e.message}")),
                isError = true
            )
        }
    }

    // Tool: Analyze Error Trends
    server.addTool(
        name = "analyze_error_trends",
        description = """Analyze error occurrence trends over time.
            |Returns aggregated statistics about error frequency, most common errors, and trend direction.
            |Use this to identify recurring issues and monitor error rate changes.""".trimMargin(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("time_range", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Time range for analysis: 'last_hour', 'last_day', 'last_week' (default: last_day)"))
                })
                put("group_by", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Group errors by: 'type', 'service', 'severity' (default: type)"))
                })
            },
            required = emptyList()
        )
    ) { request ->
        try {
            val timeRange = request.arguments?.get("time_range")?.jsonPrimitive?.content ?: "last_day"
            val groupBy = request.arguments?.get("group_by")?.jsonPrimitive?.content ?: "type"

            val trends = errorLogService.analyzeErrorTrends(timeRange, groupBy)
            val resultJson = json.encodeToString(trends)
            CallToolResult(
                content = listOf(TextContent(text = "Error Trend Analysis:\n\n$resultJson"))
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error analyzing trends: ${e.message}")),
                isError = true
            )
        }
    }

    // Tool: Get Service Health
    server.addTool(
        name = "get_service_health",
        description = """Check the health status of system services and dependencies.
            |Returns health status, response times, and availability metrics for each service.
            |Use this to quickly identify which services are experiencing issues.""".trimMargin(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("service_name", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Specific service to check, or 'all' for all services (default: all)"))
                })
            },
            required = emptyList()
        )
    ) { request ->
        try {
            val serviceName = request.arguments?.get("service_name")?.jsonPrimitive?.content ?: "all"
            val health = systemMetricsService.getServiceHealth(serviceName)
            val resultJson = json.encodeToString(health)
            CallToolResult(
                content = listOf(TextContent(text = "Service Health Status:\n\n$resultJson"))
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error checking service health: ${e.message}")),
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
        done.complete()
    }

    server.connect(transport)
    done.join()
}
