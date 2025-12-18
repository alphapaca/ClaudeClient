package com.github.alphapaca

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Serializable
data class Reminder(
    val id: Long,
    val message: String,
    val triggerAt: Long, // epoch millis
    val conversationId: String?,
    val delivered: Boolean = false,
)

class ReminderService {
    private val logger = LoggerFactory.getLogger(ReminderService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var schedulerJob: Job? = null

    private val _dueReminders = MutableSharedFlow<Reminder>(extraBufferCapacity = 64)
    val dueReminders: SharedFlow<Reminder> = _dueReminders.asSharedFlow()

    private val connection: Connection by lazy {
        val dbPath = System.getProperty("user.home") + "/.mcp-server/reminders.db"
        java.io.File(dbPath).parentFile?.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:$dbPath").also {
            initDatabase(it)
        }
    }

    private fun initDatabase(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS reminders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    message TEXT NOT NULL,
                    trigger_at INTEGER NOT NULL,
                    conversation_id TEXT,
                    delivered INTEGER NOT NULL DEFAULT 0
                )
            """)
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_trigger_at ON reminders(trigger_at)")
        }
        logger.info("Reminder database initialized")
    }

    /**
     * Creates a reminder.
     * @param message The reminder message
     * @param triggerTime Time string in formats: "in 5 minutes", "in 2 hours", "at 14:30", "2024-12-25 10:00"
     * @param conversationId Optional conversation ID to inject the reminder into
     * @return The created reminder
     */
    fun createReminder(message: String, triggerTime: String, conversationId: String? = null): Reminder {
        val triggerAt = parseTimeString(triggerTime)

        val sql = "INSERT INTO reminders (message, trigger_at, conversation_id, delivered) VALUES (?, ?, ?, 0)"
        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setString(1, message)
            stmt.setLong(2, triggerAt)
            stmt.setString(3, conversationId)
            stmt.executeUpdate()

            val rs = stmt.generatedKeys
            rs.next()
            val id = rs.getLong(1)

            logger.info("Created reminder $id: '$message' at ${formatTime(triggerAt)}")

            val reminder = Reminder(
                id = id,
                message = message,
                triggerAt = triggerAt,
                conversationId = conversationId,
                delivered = false,
            )

            // Wake up the scheduler to recalculate next wake time
            notifyNewReminder()

            return reminder
        }
    }

    fun getActiveReminders(): List<Reminder> {
        val sql = "SELECT id, message, trigger_at, conversation_id, delivered FROM reminders WHERE delivered = 0 ORDER BY trigger_at"
        connection.prepareStatement(sql).use { stmt ->
            val rs = stmt.executeQuery()
            val reminders = mutableListOf<Reminder>()
            while (rs.next()) {
                reminders.add(
                    Reminder(
                        id = rs.getLong("id"),
                        message = rs.getString("message"),
                        triggerAt = rs.getLong("trigger_at"),
                        conversationId = rs.getString("conversation_id"),
                        delivered = rs.getInt("delivered") == 1,
                    )
                )
            }
            return reminders
        }
    }

    fun deleteReminder(id: Long): Boolean {
        val sql = "DELETE FROM reminders WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, id)
            val deleted = stmt.executeUpdate() > 0
            if (deleted) {
                logger.info("Deleted reminder $id")
            }
            return deleted
        }
    }

    private fun markDelivered(id: Long) {
        val sql = "UPDATE reminders SET delivered = 1 WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, id)
            stmt.executeUpdate()
        }
        logger.info("Marked reminder $id as delivered")
    }

    private val _reschedule = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Call this after creating a new reminder to wake up the scheduler
     * and recalculate the next wake time.
     */
    fun notifyNewReminder() {
        _reschedule.tryEmit(Unit)
    }

    fun startScheduler() {
        if (schedulerJob?.isActive == true) return

        schedulerJob = scope.launch {
            logger.info("Reminder scheduler started (event-driven)")

            while (isActive) {
                // Process any due reminders
                processDueReminders()

                // Calculate delay until next reminder
                val nextReminderTime = getNextReminderTime()

                if (nextReminderTime != null) {
                    val delayMs = (nextReminderTime - System.currentTimeMillis()).coerceAtLeast(0)
                    logger.debug("Next reminder at ${formatTime(nextReminderTime)}, sleeping for ${delayMs}ms")

                    // Wait for either: delay expires OR new reminder added
                    val timerDeferred = async { delay(delayMs) }
                    val rescheduleDeferred = async { _reschedule.first() }

                    select<Unit> {
                        timerDeferred.onAwait { }
                        rescheduleDeferred.onAwait { }
                    }

                    // Cancel the other one
                    timerDeferred.cancel()
                    rescheduleDeferred.cancel()
                } else {
                    // No pending reminders, wait for a new one to be added
                    logger.debug("No pending reminders, waiting for new reminder...")
                    _reschedule.first()
                }
            }
        }
    }

    private suspend fun processDueReminders() {
        val now = System.currentTimeMillis()
        val sql = "SELECT id, message, trigger_at, conversation_id FROM reminders WHERE delivered = 0 AND trigger_at <= ?"

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, now)
            val rs = stmt.executeQuery()

            while (rs.next()) {
                val reminder = Reminder(
                    id = rs.getLong("id"),
                    message = rs.getString("message"),
                    triggerAt = rs.getLong("trigger_at"),
                    conversationId = rs.getString("conversation_id"),
                    delivered = false,
                )

                logger.info("Reminder ${reminder.id} is due: '${reminder.message}'")
                markDelivered(reminder.id)
                _dueReminders.tryEmit(reminder)
            }
        }
    }

    private fun getNextReminderTime(): Long? {
        val sql = "SELECT MIN(trigger_at) FROM reminders WHERE delivered = 0"
        connection.prepareStatement(sql).use { stmt ->
            val rs = stmt.executeQuery()
            return if (rs.next()) rs.getLong(1).takeIf { !rs.wasNull() } else null
        }
    }

    fun close() {
        schedulerJob?.cancel()
        connection.close()
        logger.info("Reminder service closed")
    }

    private fun parseTimeString(timeStr: String): Long {
        val now = System.currentTimeMillis()
        val trimmed = timeStr.trim().lowercase()

        // Pattern: "in X minutes/hours/days"
        val inPattern = Regex("""in\s+(\d+)\s+(second|minute|hour|day|week)s?""")
        inPattern.find(trimmed)?.let { match ->
            val amount = match.groupValues[1].toLong()
            val unit = match.groupValues[2]
            val millis = when (unit) {
                "second" -> amount * 1000
                "minute" -> amount * 60 * 1000
                "hour" -> amount * 60 * 60 * 1000
                "day" -> amount * 24 * 60 * 60 * 1000
                "week" -> amount * 7 * 24 * 60 * 60 * 1000
                else -> throw IllegalArgumentException("Unknown time unit: $unit")
            }
            return now + millis
        }

        // Pattern: "at HH:mm" (today or tomorrow if past)
        val atPattern = Regex("""at\s+(\d{1,2}):(\d{2})""")
        atPattern.find(trimmed)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()

            val zone = ZoneId.systemDefault()
            var target = Instant.now()
                .atZone(zone)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)

            if (target.toInstant().toEpochMilli() <= now) {
                target = target.plusDays(1)
            }

            return target.toInstant().toEpochMilli()
        }

        // Pattern: ISO datetime "YYYY-MM-DD HH:mm" or "YYYY-MM-DDTHH:mm"
        try {
            val normalized = trimmed.replace(" ", "T")
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                .withZone(ZoneId.systemDefault())
            val instant = Instant.from(formatter.parse(normalized))
            return instant.toEpochMilli()
        } catch (e: DateTimeParseException) {
            // Fall through
        }

        throw IllegalArgumentException(
            "Could not parse time: '$timeStr'. Supported formats: 'in X minutes/hours/days', 'at HH:mm', 'YYYY-MM-DD HH:mm'"
        )
    }

    private fun formatTime(epochMillis: Long): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        return formatter.format(Instant.ofEpochMilli(epochMillis))
    }
}
