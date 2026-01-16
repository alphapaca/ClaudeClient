package com.github.alphapaca.webagent.agent.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("DocumentationTool")

@LLMDescription("Tools for accessing project documentation")
class DocumentationToolSet(
    private val claudeMdContent: String,
) : ToolSet {

    @Tool
    @LLMDescription(
        """Get the project's CLAUDE.md documentation which contains:
        - Project overview and architecture
        - Build and run commands
        - Module descriptions
        - Key dependencies
        - Development guidelines
        Use this for understanding the project structure and conventions."""
    )
    fun getProjectDocumentation(): String {
        logger.info("=== TOOL CALL: getProjectDocumentation() ===")
        val result = if (claudeMdContent.isNotBlank()) {
            "=== PROJECT DOCUMENTATION (CLAUDE.md) ===\n\n$claudeMdContent"
        } else {
            "Project documentation (CLAUDE.md) is not available."
        }
        logger.info("=== TOOL RESULT: ${result.length} chars ===")
        return result
    }
}
