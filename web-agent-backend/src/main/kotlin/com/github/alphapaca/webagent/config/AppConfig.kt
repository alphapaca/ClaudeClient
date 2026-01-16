package com.github.alphapaca.webagent.config

import java.io.File
import java.util.Properties

data class AppConfig(
    val port: Int,
    val anthropicApiKey: String,
    val voyageApiKey: String,
    val githubToken: String?,
    val githubOwner: String,
    val githubRepo: String,
    val vectorDbPath: String,
    val claudeMdPath: String,
    val projectPath: String,
    val autoIndex: Boolean,
    val enableCodeSearch: Boolean,
    val enableIssueSearch: Boolean,
    // Koog agent configuration
    val llmModel: String,
    val maxTokens: Int,
    val temperature: Double,
    val maxAgentIterations: Int,
) {
    companion object {
        fun load(): AppConfig {
            return AppConfig(
                port = loadEnvParameter("PORT")?.toIntOrNull() ?: 8080,
                anthropicApiKey = loadEnvParameter("ANTHROPIC_API_KEY")
                    ?: error("ANTHROPIC_API_KEY is required"),
                voyageApiKey = loadEnvParameter("VOYAGE_API_KEY")
                    ?: loadEnvParameter("VOYAGEAI_API_KEY")
                    ?: error("VOYAGE_API_KEY is required"),
                githubToken = loadEnvParameter("GITHUB_TOKEN"),
                githubOwner = loadEnvParameter("GITHUB_OWNER") ?: "alphapaca",
                githubRepo = loadEnvParameter("GITHUB_REPO") ?: "ClaudeClient",
                vectorDbPath = loadEnvParameter("VECTOR_DB_PATH")
                    ?: "${System.getProperty("user.home")}/.web-agent/code-vectors.db",
                claudeMdPath = loadEnvParameter("CLAUDE_MD_PATH")
                    ?: "./CLAUDE.md",
                projectPath = loadEnvParameter("PROJECT_PATH")
                    ?: System.getProperty("user.dir"),
                autoIndex = loadEnvParameter("AUTO_INDEX")?.toBoolean() ?: true,
                enableCodeSearch = true,
                enableIssueSearch = true,
                // Koog agent configuration
                llmModel = loadEnvParameter("LLM_MODEL") ?: "claude-sonnet-4",
                maxTokens = loadEnvParameter("MAX_TOKENS")?.toIntOrNull() ?: 4096,
                temperature = loadEnvParameter("TEMPERATURE")?.toDoubleOrNull() ?: 0.7,
                maxAgentIterations = loadEnvParameter("MAX_AGENT_ITERATIONS")?.toIntOrNull() ?: 10,
            )
        }

        private fun loadEnvParameter(key: String): String? {
            // First try environment variable
            System.getenv(key)?.let { return it }

            // Try local.properties in current directory
            val localPropsFile = File("local.properties")
            if (localPropsFile.exists()) {
                val props = Properties()
                localPropsFile.inputStream().use { props.load(it) }
                props.getProperty(key)?.let { return it }
            }

            // Try config file in home directory
            val homePropsFile = File(System.getProperty("user.home"), ".web-agent/config.properties")
            if (homePropsFile.exists()) {
                val props = Properties()
                homePropsFile.inputStream().use { props.load(it) }
                props.getProperty(key)?.let { return it }
            }

            return null
        }
    }
}
