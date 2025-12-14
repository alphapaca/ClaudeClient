package com.github.alphapaca.claudeclient

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.alphapaca.claudeclient.data.db.ClaudeClientDatabase
import java.io.File

fun createDatabaseDriver(): SqlDriver {
    val dbPath = File(System.getProperty("user.home"), ".claudeclient/claudeclient.db")
    dbPath.parentFile?.mkdirs()

    val dbExists = dbPath.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbPath.absolutePath}")

    if (!dbExists) {
        ClaudeClientDatabase.Schema.create(driver)
    }

    return driver
}
