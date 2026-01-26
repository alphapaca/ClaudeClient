package com.github.alphapaca.systemanalysis

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Service that provides error log data.
 * Currently returns fake data for demonstration purposes.
 */
class ErrorLogService {

    /**
     * Search error logs with optional filters.
     */
    fun searchErrors(
        query: String?,
        severity: String?,
        limit: Int,
        timeRange: String
    ): List<ErrorLogEntry> {
        val allErrors = generateFakeErrorLogs()

        return allErrors
            .filter { error ->
                val matchesQuery = query.isNullOrBlank() ||
                    error.message.contains(query, ignoreCase = true) ||
                    error.stacktrace.contains(query, ignoreCase = true) ||
                    error.errorType.contains(query, ignoreCase = true)

                val matchesSeverity = severity.isNullOrBlank() ||
                    error.severity.equals(severity, ignoreCase = true)

                val matchesTimeRange = isWithinTimeRange(error.timestamp, timeRange)

                matchesQuery && matchesSeverity && matchesTimeRange
            }
            .take(limit)
    }

    /**
     * Analyze error trends over time.
     */
    fun analyzeErrorTrends(timeRange: String, groupBy: String): ErrorTrendAnalysis {
        val errors = generateFakeErrorLogs()

        val grouped = when (groupBy) {
            "type" -> errors.groupBy { it.errorType }
            "service" -> errors.groupBy { it.service }
            "severity" -> errors.groupBy { it.severity }
            else -> errors.groupBy { it.errorType }
        }

        val groupedStats = grouped.map { (key, entries) ->
            ErrorGroupStats(
                groupName = key,
                totalCount = entries.sumOf { it.occurrenceCount },
                uniqueErrors = entries.size,
                averageOccurrences = entries.map { it.occurrenceCount }.average(),
                trend = listOf("increasing", "decreasing", "stable").random()
            )
        }.sortedByDescending { it.totalCount }

        return ErrorTrendAnalysis(
            timeRange = timeRange,
            groupBy = groupBy,
            totalErrors = errors.sumOf { it.occurrenceCount },
            uniqueErrorTypes = errors.map { it.errorType }.distinct().size,
            groups = groupedStats,
            summary = "Error rate ${listOf("increased by 15%", "decreased by 8%", "remained stable").random()} compared to the previous period."
        )
    }

    private fun isWithinTimeRange(timestamp: String, timeRange: String): Boolean {
        // For fake data, always return true
        return true
    }

    private fun generateFakeErrorLogs(): List<ErrorLogEntry> {
        return listOf(
            ErrorLogEntry(
                id = "err-001",
                timestamp = "2024-01-26T10:15:23.456Z",
                severity = "ERROR",
                errorType = "NullPointerException",
                message = "Cannot invoke method getName() on null object reference",
                stacktrace = """java.lang.NullPointerException: Cannot invoke method getName() on null object reference
    at com.example.app.UserService.processUser(UserService.kt:45)
    at com.example.app.ApiController.handleRequest(ApiController.kt:123)
    at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:897)
    at javax.servlet.http.HttpServlet.service(HttpServlet.java:750)""",
                service = "user-service",
                occurrenceCount = 47,
                firstSeen = "2024-01-25T08:00:00Z",
                lastSeen = "2024-01-26T10:15:23Z",
                affectedEndpoint = "/api/v1/users/{id}",
                additionalContext = mapOf(
                    "request_id" to "req-abc123",
                    "user_id" to "null",
                    "environment" to "production"
                )
            ),
            ErrorLogEntry(
                id = "err-002",
                timestamp = "2024-01-26T09:45:12.789Z",
                severity = "ERROR",
                errorType = "ConnectionTimeoutException",
                message = "Connection to database timed out after 30000ms",
                stacktrace = """com.zaxxer.hikari.pool.HikariPool.createTimeoutException: Connection is not available, request timed out after 30000ms
    at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:195)
    at com.example.app.repository.UserRepository.findById(UserRepository.kt:32)
    at com.example.app.service.UserService.getUser(UserService.kt:78)
    at com.example.app.controller.UserController.get(UserController.kt:45)""",
                service = "user-service",
                occurrenceCount = 156,
                firstSeen = "2024-01-26T06:00:00Z",
                lastSeen = "2024-01-26T09:45:12Z",
                affectedEndpoint = "/api/v1/users",
                additionalContext = mapOf(
                    "database" to "postgres-primary",
                    "pool_size" to "10",
                    "active_connections" to "10"
                )
            ),
            ErrorLogEntry(
                id = "err-003",
                timestamp = "2024-01-26T11:23:45.123Z",
                severity = "FATAL",
                errorType = "OutOfMemoryError",
                message = "Java heap space exceeded - unable to allocate 512MB",
                stacktrace = """java.lang.OutOfMemoryError: Java heap space
    at java.base/java.util.Arrays.copyOf(Arrays.java:3512)
    at java.base/java.util.ArrayList.grow(ArrayList.java:237)
    at com.example.app.batch.DataProcessor.processLargeDataset(DataProcessor.kt:89)
    at com.example.app.scheduler.BatchJob.execute(BatchJob.kt:34)""",
                service = "batch-processor",
                occurrenceCount = 3,
                firstSeen = "2024-01-26T11:20:00Z",
                lastSeen = "2024-01-26T11:23:45Z",
                affectedEndpoint = "batch://data-import",
                additionalContext = mapOf(
                    "heap_max" to "2048MB",
                    "heap_used" to "2045MB",
                    "gc_overhead" to "95%"
                )
            ),
            ErrorLogEntry(
                id = "err-004",
                timestamp = "2024-01-26T08:30:00.000Z",
                severity = "WARN",
                errorType = "RateLimitExceededException",
                message = "API rate limit exceeded for client: mobile-app-v2",
                stacktrace = """com.example.app.exception.RateLimitExceededException: Rate limit of 1000 requests/minute exceeded
    at com.example.app.filter.RateLimitFilter.doFilter(RateLimitFilter.kt:56)
    at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:107)
    at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:178)""",
                service = "api-gateway",
                occurrenceCount = 234,
                firstSeen = "2024-01-26T07:00:00Z",
                lastSeen = "2024-01-26T08:30:00Z",
                affectedEndpoint = "/api/v1/*",
                additionalContext = mapOf(
                    "client_id" to "mobile-app-v2",
                    "current_rate" to "1523/min",
                    "limit" to "1000/min"
                )
            ),
            ErrorLogEntry(
                id = "err-005",
                timestamp = "2024-01-26T12:00:15.678Z",
                severity = "ERROR",
                errorType = "JsonParseException",
                message = "Unexpected character ('<' (code 60)): expected a valid value at line 1, column 1",
                stacktrace = """com.fasterxml.jackson.core.JsonParseException: Unexpected character ('<' (code 60))
    at com.fasterxml.jackson.core.JsonParser._constructError(JsonParser.java:1851)
    at com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3713)
    at com.example.app.client.ExternalApiClient.parseResponse(ExternalApiClient.kt:67)
    at com.example.app.service.IntegrationService.fetchData(IntegrationService.kt:112)""",
                service = "integration-service",
                occurrenceCount = 89,
                firstSeen = "2024-01-26T10:00:00Z",
                lastSeen = "2024-01-26T12:00:15Z",
                affectedEndpoint = "/api/v1/integrations/sync",
                additionalContext = mapOf(
                    "external_api" to "partner-service",
                    "response_content_type" to "text/html",
                    "expected_content_type" to "application/json"
                )
            ),
            ErrorLogEntry(
                id = "err-006",
                timestamp = "2024-01-26T07:15:30.000Z",
                severity = "ERROR",
                errorType = "SSLHandshakeException",
                message = "Remote host terminated the handshake - certificate expired",
                stacktrace = """javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake
    at sun.security.ssl.SSLSocketImpl.handleException(SSLSocketImpl.java:1656)
    at com.example.app.client.SecureClient.connect(SecureClient.kt:45)
    at com.example.app.service.PaymentService.processPayment(PaymentService.kt:78)""",
                service = "payment-service",
                occurrenceCount = 12,
                firstSeen = "2024-01-26T07:00:00Z",
                lastSeen = "2024-01-26T07:15:30Z",
                affectedEndpoint = "/api/v1/payments",
                additionalContext = mapOf(
                    "remote_host" to "payment-gateway.example.com",
                    "certificate_expiry" to "2024-01-25",
                    "tls_version" to "TLSv1.3"
                )
            ),
            ErrorLogEntry(
                id = "err-007",
                timestamp = "2024-01-26T13:45:00.000Z",
                severity = "WARN",
                errorType = "CacheEvictionWarning",
                message = "Cache eviction rate exceeded threshold - possible cache thrashing",
                stacktrace = """com.example.app.cache.CacheMonitor.checkEvictionRate(CacheMonitor.kt:34)
    at com.example.app.cache.RedisCacheManager.onEviction(RedisCacheManager.kt:89)
    at io.lettuce.core.protocol.CommandHandler.channelRead(CommandHandler.java:565)""",
                service = "cache-service",
                occurrenceCount = 567,
                firstSeen = "2024-01-26T12:00:00Z",
                lastSeen = "2024-01-26T13:45:00Z",
                affectedEndpoint = "cache://redis-cluster",
                additionalContext = mapOf(
                    "eviction_rate" to "45/sec",
                    "threshold" to "20/sec",
                    "cache_hit_ratio" to "0.32"
                )
            ),
            ErrorLogEntry(
                id = "err-008",
                timestamp = "2024-01-26T14:20:00.000Z",
                severity = "ERROR",
                errorType = "DeadlockException",
                message = "Database deadlock detected on table 'orders'",
                stacktrace = """org.hibernate.exception.LockAcquisitionException: could not execute statement
    at org.hibernate.exception.internal.SQLExceptionTypeDelegate.convert(SQLExceptionTypeDelegate.java:64)
    at com.example.app.repository.OrderRepository.save(OrderRepository.kt:56)
    at com.example.app.service.OrderService.createOrder(OrderService.kt:123)
    at com.example.app.controller.OrderController.create(OrderController.kt:67)""",
                service = "order-service",
                occurrenceCount = 8,
                firstSeen = "2024-01-26T14:15:00Z",
                lastSeen = "2024-01-26T14:20:00Z",
                affectedEndpoint = "/api/v1/orders",
                additionalContext = mapOf(
                    "table" to "orders",
                    "transaction_id" to "tx-789xyz",
                    "lock_wait_timeout" to "50s"
                )
            )
        )
    }
}

@Serializable
data class ErrorLogEntry(
    val id: String,
    val timestamp: String,
    val severity: String,
    val errorType: String,
    val message: String,
    val stacktrace: String,
    val service: String,
    val occurrenceCount: Int,
    val firstSeen: String,
    val lastSeen: String,
    val affectedEndpoint: String,
    val additionalContext: Map<String, String>
)

@Serializable
data class ErrorTrendAnalysis(
    val timeRange: String,
    val groupBy: String,
    val totalErrors: Int,
    val uniqueErrorTypes: Int,
    val groups: List<ErrorGroupStats>,
    val summary: String
)

@Serializable
data class ErrorGroupStats(
    val groupName: String,
    val totalCount: Int,
    val uniqueErrors: Int,
    val averageOccurrences: Double,
    val trend: String
)
