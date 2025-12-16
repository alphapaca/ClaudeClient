package com.github.alphapaca.claudeclient.data.mcp

import kotlinx.serialization.Serializable

@Serializable
data class MCPServerConfig(
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
)
