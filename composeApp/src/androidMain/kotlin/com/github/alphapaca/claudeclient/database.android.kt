package com.github.alphapaca.claudeclient

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.github.alphapaca.claudeclient.data.db.ClaudeClientDatabase

fun createDatabaseDriver(context: Context): SqlDriver {
    return AndroidSqliteDriver(ClaudeClientDatabase.Schema, context, "claudeclient.db")
}
