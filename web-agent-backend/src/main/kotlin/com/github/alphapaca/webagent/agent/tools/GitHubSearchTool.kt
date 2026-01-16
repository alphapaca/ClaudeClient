package com.github.alphapaca.webagent.agent.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.github.alphapaca.webagent.data.service.DirectGitHubService

@LLMDescription("Tools for searching GitHub issues related to the project")
class GitHubSearchToolSet(
    private val githubService: DirectGitHubService,
) : ToolSet {

    @Tool
    @LLMDescription(
        """Search GitHub issues for the project. Returns relevant issues including title,
        state (open/closed), URL, labels, and a preview of the issue body.
        Use this to find bug reports, feature requests, or discussions related to a topic."""
    )
    fun searchGitHubIssues(
        @LLMDescription("Keywords to search for in GitHub issues")
        query: String,
        @LLMDescription("Maximum number of issues to return (1-10)")
        limit: Int = 5
    ): String {
        val safeLimit = limit.coerceIn(1, 10)

        return try {
            githubService.searchIssues(query, safeLimit)
                ?: "No issues found matching: $query"
        } catch (e: Exception) {
            "Error searching GitHub issues: ${e.message}"
        }
    }
}
