package com.github.alphapaca.systemanalysis

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Service that provides system metrics data.
 * Currently returns fake data for demonstration purposes.
 */
class SystemMetricsService {

    /**
     * Get system metrics based on the specified type.
     */
    fun getMetrics(metricType: String): SystemMetrics {
        return when (metricType.lowercase()) {
            "cpu" -> SystemMetrics(
                timestamp = "2024-01-26T14:30:00Z",
                cpu = generateCpuMetrics(),
                memory = null,
                disk = null,
                network = null
            )
            "memory" -> SystemMetrics(
                timestamp = "2024-01-26T14:30:00Z",
                cpu = null,
                memory = generateMemoryMetrics(),
                disk = null,
                network = null
            )
            "disk" -> SystemMetrics(
                timestamp = "2024-01-26T14:30:00Z",
                cpu = null,
                memory = null,
                disk = generateDiskMetrics(),
                network = null
            )
            "network" -> SystemMetrics(
                timestamp = "2024-01-26T14:30:00Z",
                cpu = null,
                memory = null,
                disk = null,
                network = generateNetworkMetrics()
            )
            else -> SystemMetrics(
                timestamp = "2024-01-26T14:30:00Z",
                cpu = generateCpuMetrics(),
                memory = generateMemoryMetrics(),
                disk = generateDiskMetrics(),
                network = generateNetworkMetrics()
            )
        }
    }

    /**
     * Get health status of services.
     */
    fun getServiceHealth(serviceName: String): ServiceHealthReport {
        val allServices = generateFakeServiceHealth()

        val filteredServices = if (serviceName.lowercase() == "all") {
            allServices
        } else {
            allServices.filter { it.name.equals(serviceName, ignoreCase = true) }
        }

        val healthyCount = filteredServices.count { it.status == "healthy" }
        val degradedCount = filteredServices.count { it.status == "degraded" }
        val unhealthyCount = filteredServices.count { it.status == "unhealthy" }

        val overallStatus = when {
            unhealthyCount > 0 -> "critical"
            degradedCount > 0 -> "warning"
            else -> "healthy"
        }

        return ServiceHealthReport(
            timestamp = "2024-01-26T14:30:00Z",
            overallStatus = overallStatus,
            healthyServices = healthyCount,
            degradedServices = degradedCount,
            unhealthyServices = unhealthyCount,
            services = filteredServices
        )
    }

    private fun generateCpuMetrics(): CpuMetrics {
        return CpuMetrics(
            usagePercent = 67.5,
            loadAverage1m = 2.45,
            loadAverage5m = 2.12,
            loadAverage15m = 1.89,
            coreCount = 8,
            perCoreUsage = listOf(72.3, 65.1, 58.9, 71.2, 69.4, 63.8, 70.1, 68.2),
            processCount = 342,
            threadCount = 1847
        )
    }

    private fun generateMemoryMetrics(): MemoryMetrics {
        return MemoryMetrics(
            totalBytes = 17179869184, // 16 GB
            usedBytes = 12884901888,  // 12 GB
            freeBytes = 4294967296,   // 4 GB
            usagePercent = 75.0,
            swapTotalBytes = 8589934592, // 8 GB
            swapUsedBytes = 1073741824,  // 1 GB
            swapUsagePercent = 12.5,
            buffersCacheBytes = 3221225472 // 3 GB
        )
    }

    private fun generateDiskMetrics(): DiskMetrics {
        return DiskMetrics(
            volumes = listOf(
                VolumeMetrics(
                    mountPoint = "/",
                    totalBytes = 536870912000,  // 500 GB
                    usedBytes = 322122547200,   // 300 GB
                    freeBytes = 214748364800,   // 200 GB
                    usagePercent = 60.0,
                    readBytesPerSec = 15728640,  // 15 MB/s
                    writeBytesPerSec = 8388608,  // 8 MB/s
                    iopsRead = 450,
                    iopsWrite = 280
                ),
                VolumeMetrics(
                    mountPoint = "/data",
                    totalBytes = 2199023255552,  // 2 TB
                    usedBytes = 1759218604441,   // 1.6 TB
                    freeBytes = 439804651110,    // 400 GB
                    usagePercent = 80.0,
                    readBytesPerSec = 52428800,   // 50 MB/s
                    writeBytesPerSec = 31457280,  // 30 MB/s
                    iopsRead = 1200,
                    iopsWrite = 850
                )
            )
        )
    }

    private fun generateNetworkMetrics(): NetworkMetrics {
        return NetworkMetrics(
            interfaces = listOf(
                InterfaceMetrics(
                    name = "eth0",
                    receiveBytesPerSec = 125829120,  // 120 MB/s
                    transmitBytesPerSec = 83886080,  // 80 MB/s
                    packetsReceivedPerSec = 85000,
                    packetsSentPerSec = 62000,
                    errorsIn = 12,
                    errorsOut = 3,
                    dropsIn = 45,
                    dropsOut = 8
                )
            ),
            activeConnections = 2847,
            connectionsByState = mapOf(
                "ESTABLISHED" to 2456,
                "TIME_WAIT" to 234,
                "CLOSE_WAIT" to 89,
                "LISTEN" to 68
            ),
            requestsPerSecond = 4523.5
        )
    }

    private fun generateFakeServiceHealth(): List<ServiceHealth> {
        return listOf(
            ServiceHealth(
                name = "user-service",
                status = "healthy",
                uptime = "15d 7h 23m",
                responseTimeMs = 45,
                requestsPerMinute = 12500,
                errorRate = 0.02,
                lastHealthCheck = "2024-01-26T14:29:55Z",
                instances = 3,
                healthyInstances = 3,
                endpoints = listOf(
                    EndpointHealth("/api/v1/users", "healthy", 42),
                    EndpointHealth("/api/v1/users/{id}", "healthy", 38),
                    EndpointHealth("/api/v1/users/search", "healthy", 67)
                )
            ),
            ServiceHealth(
                name = "order-service",
                status = "degraded",
                uptime = "15d 7h 23m",
                responseTimeMs = 450,
                requestsPerMinute = 8900,
                errorRate = 2.5,
                lastHealthCheck = "2024-01-26T14:29:55Z",
                instances = 3,
                healthyInstances = 2,
                endpoints = listOf(
                    EndpointHealth("/api/v1/orders", "degraded", 380),
                    EndpointHealth("/api/v1/orders/{id}", "healthy", 55),
                    EndpointHealth("/api/v1/orders/checkout", "degraded", 890)
                )
            ),
            ServiceHealth(
                name = "payment-service",
                status = "unhealthy",
                uptime = "0d 2h 15m",
                responseTimeMs = 2500,
                requestsPerMinute = 1200,
                errorRate = 15.3,
                lastHealthCheck = "2024-01-26T14:29:55Z",
                instances = 2,
                healthyInstances = 0,
                endpoints = listOf(
                    EndpointHealth("/api/v1/payments", "unhealthy", 2100),
                    EndpointHealth("/api/v1/payments/refund", "unhealthy", 3200),
                    EndpointHealth("/api/v1/payments/status", "degraded", 850)
                )
            ),
            ServiceHealth(
                name = "notification-service",
                status = "healthy",
                uptime = "30d 12h 45m",
                responseTimeMs = 23,
                requestsPerMinute = 45000,
                errorRate = 0.001,
                lastHealthCheck = "2024-01-26T14:29:55Z",
                instances = 5,
                healthyInstances = 5,
                endpoints = listOf(
                    EndpointHealth("/api/v1/notifications/send", "healthy", 18),
                    EndpointHealth("/api/v1/notifications/batch", "healthy", 45),
                    EndpointHealth("/api/v1/notifications/status", "healthy", 12)
                )
            ),
            ServiceHealth(
                name = "cache-service",
                status = "degraded",
                uptime = "45d 3h 12m",
                responseTimeMs = 15,
                requestsPerMinute = 125000,
                errorRate = 0.8,
                lastHealthCheck = "2024-01-26T14:29:55Z",
                instances = 3,
                healthyInstances = 2,
                endpoints = listOf(
                    EndpointHealth("redis://primary", "degraded", 25),
                    EndpointHealth("redis://replica-1", "healthy", 8),
                    EndpointHealth("redis://replica-2", "healthy", 9)
                )
            ),
            ServiceHealth(
                name = "database-primary",
                status = "healthy",
                uptime = "90d 15h 30m",
                responseTimeMs = 5,
                requestsPerMinute = 85000,
                errorRate = 0.0,
                lastHealthCheck = "2024-01-26T14:29:55Z",
                instances = 1,
                healthyInstances = 1,
                endpoints = listOf(
                    EndpointHealth("postgres://primary:5432", "healthy", 4)
                )
            )
        )
    }
}

@Serializable
data class SystemMetrics(
    val timestamp: String,
    val cpu: CpuMetrics?,
    val memory: MemoryMetrics?,
    val disk: DiskMetrics?,
    val network: NetworkMetrics?
)

@Serializable
data class CpuMetrics(
    val usagePercent: Double,
    val loadAverage1m: Double,
    val loadAverage5m: Double,
    val loadAverage15m: Double,
    val coreCount: Int,
    val perCoreUsage: List<Double>,
    val processCount: Int,
    val threadCount: Int
)

@Serializable
data class MemoryMetrics(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val usagePercent: Double,
    val swapTotalBytes: Long,
    val swapUsedBytes: Long,
    val swapUsagePercent: Double,
    val buffersCacheBytes: Long
)

@Serializable
data class DiskMetrics(
    val volumes: List<VolumeMetrics>
)

@Serializable
data class VolumeMetrics(
    val mountPoint: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val usagePercent: Double,
    val readBytesPerSec: Long,
    val writeBytesPerSec: Long,
    val iopsRead: Int,
    val iopsWrite: Int
)

@Serializable
data class NetworkMetrics(
    val interfaces: List<InterfaceMetrics>,
    val activeConnections: Int,
    val connectionsByState: Map<String, Int>,
    val requestsPerSecond: Double
)

@Serializable
data class InterfaceMetrics(
    val name: String,
    val receiveBytesPerSec: Long,
    val transmitBytesPerSec: Long,
    val packetsReceivedPerSec: Int,
    val packetsSentPerSec: Int,
    val errorsIn: Int,
    val errorsOut: Int,
    val dropsIn: Int,
    val dropsOut: Int
)

@Serializable
data class ServiceHealthReport(
    val timestamp: String,
    val overallStatus: String,
    val healthyServices: Int,
    val degradedServices: Int,
    val unhealthyServices: Int,
    val services: List<ServiceHealth>
)

@Serializable
data class ServiceHealth(
    val name: String,
    val status: String,
    val uptime: String,
    val responseTimeMs: Int,
    val requestsPerMinute: Int,
    val errorRate: Double,
    val lastHealthCheck: String,
    val instances: Int,
    val healthyInstances: Int,
    val endpoints: List<EndpointHealth>
)

@Serializable
data class EndpointHealth(
    val path: String,
    val status: String,
    val responseTimeMs: Int
)
