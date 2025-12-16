package com.github.alphapaca.claudeclient.data.mcp

import kotlinx.serialization.json.JsonObject

data class MCPTool(
    val serverName: String,
    val name: String,
    val description: String?,
    val inputSchema: JsonObject? = null,
)
