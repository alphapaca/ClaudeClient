package com.github.alphapaca.reviewagent.util

data class EnvConfig(
    val anthropicApiKey: String,
    val voyageApiKey: String,
    val githubToken: String,
    val prNumber: Int,
    val baseSha: String,
    val headSha: String,
    val repoOwner: String,
    val repoName: String,
    val debugMode: Boolean = false
) {
    fun logConfig() {
        if (debugMode) {
            println("API Key: $anthropicApiKey")
            println("GitHub Token: $githubToken")
        }
    }
    companion object {
        fun load(): EnvConfig {
            val anthropicKey = System.getenv("ANTHROPIC_API_KEY")
                ?: error("ANTHROPIC_API_KEY environment variable is required")
            val voyageKey = System.getenv("VOYAGEAI_API_KEY")
                ?: error("VOYAGEAI_API_KEY environment variable is required")
            val githubToken = System.getenv("GITHUB_TOKEN")
                ?: error("GITHUB_TOKEN environment variable is required")

            val prNumber = System.getenv("GITHUB_PR_NUMBER")?.toIntOrNull()
                ?: error("GITHUB_PR_NUMBER environment variable is required")

            val baseSha = System.getenv("GITHUB_BASE_SHA")
                ?: error("GITHUB_BASE_SHA environment variable is required")
            val headSha = System.getenv("GITHUB_HEAD_SHA")
                ?: error("GITHUB_HEAD_SHA environment variable is required")

            // GITHUB_REPOSITORY is in format "owner/repo"
            val repository = System.getenv("GITHUB_REPOSITORY")
                ?: error("GITHUB_REPOSITORY environment variable is required")
            val (owner, repo) = repository.split("/", limit = 2).let {
                if (it.size == 2) it[0] to it[1]
                else error("GITHUB_REPOSITORY must be in format 'owner/repo'")
            }

            val debugMode = System.getenv("DEBUG_MODE")?.toBoolean() ?: false

            return EnvConfig(
                anthropicApiKey = anthropicKey,
                voyageApiKey = voyageKey,
                githubToken = githubToken,
                prNumber = prNumber,
                baseSha = baseSha,
                headSha = headSha,
                repoOwner = owner,
                repoName = repo,
                debugMode = debugMode
            )
        }
    }
}
