package com.github.alphapaca.reviewagent.service

import com.github.alphapaca.reviewagent.model.DiffHunk
import com.github.alphapaca.reviewagent.model.DiffLine
import com.github.alphapaca.reviewagent.model.DiffLineType
import com.github.alphapaca.reviewagent.model.FileDiff
import com.github.alphapaca.reviewagent.model.HunkHeader
import org.slf4j.LoggerFactory
import java.io.File

class GitService(
    private val repoPath: String = ".",
    private val baseSha: String,
    private val headSha: String
) {
    private val logger = LoggerFactory.getLogger(GitService::class.java)

    /**
     * Read CLAUDE.md from repository root if it exists.
     */
    fun readClaudeMd(): String? {
        val claudeMdPath = File(repoPath, "CLAUDE.md")
        return if (claudeMdPath.exists()) {
            logger.info("Found CLAUDE.md, reading content...")
            claudeMdPath.readText()
        } else {
            logger.info("No CLAUDE.md found in repository root")
            null
        }
    }

    /**
     * Get diff between base and head commit.
     */
    fun getPRDiff(): List<FileDiff> {
        logger.info("Getting diff between $baseSha and $headSha")

        val process = ProcessBuilder(
            "git", "diff", "--unified=5", "$baseSha...$headSha"
        ).directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val diffOutput = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.error("git diff failed with exit code $exitCode: $diffOutput")
            return emptyList()
        }

        return parseDiff(diffOutput)
    }

    /**
     * Get list of changed file paths.
     */
    fun getChangedFiles(): List<String> {
        val process = ProcessBuilder(
            "git", "diff", "--name-only", "$baseSha...$headSha"
        ).directory(File(repoPath))
            .start()

        val output = process.inputStream.bufferedReader().readLines()
        process.waitFor()
        return output
    }

    /**
     * Read file content at HEAD.
     */
    fun readFileAtHead(filePath: String): String? {
        val file = File(repoPath, filePath)
        return if (file.exists()) file.readText() else null
    }

    /**
     * Parse unified diff format into structured FileDiff objects.
     */
    private fun parseDiff(diffOutput: String): List<FileDiff> {
        val fileDiffs = mutableListOf<FileDiff>()
        var currentFile: FileDiff? = null
        var currentHunk: DiffHunk? = null
        var newLineNumber = 0
        var oldLineNumber = 0

        for (line in diffOutput.lines()) {
            when {
                line.startsWith("diff --git") -> {
                    currentFile?.let { fileDiffs.add(it) }
                    val filePath = line.substringAfter(" b/")
                    currentFile = FileDiff(filePath = filePath)
                    currentHunk = null
                }
                line.startsWith("@@") -> {
                    val hunkHeader = parseHunkHeader(line)
                    newLineNumber = hunkHeader.newStart
                    oldLineNumber = hunkHeader.oldStart
                    currentHunk = DiffHunk(
                        header = line,
                        oldStart = hunkHeader.oldStart,
                        newStart = hunkHeader.newStart
                    )
                    currentFile?.hunks?.add(currentHunk)
                }
                currentHunk != null && !line.startsWith("+++") && !line.startsWith("---") -> {
                    val diffLine = when {
                        line.startsWith("+") -> {
                            DiffLine(
                                type = DiffLineType.ADDITION,
                                content = line.substring(1),
                                newLineNumber = newLineNumber++,
                                oldLineNumber = null
                            )
                        }
                        line.startsWith("-") -> {
                            DiffLine(
                                type = DiffLineType.DELETION,
                                content = line.substring(1),
                                newLineNumber = null,
                                oldLineNumber = oldLineNumber++
                            )
                        }
                        else -> {
                            val content = if (line.startsWith(" ")) line.substring(1) else line
                            DiffLine(
                                type = DiffLineType.CONTEXT,
                                content = content,
                                newLineNumber = newLineNumber++,
                                oldLineNumber = oldLineNumber++
                            )
                        }
                    }
                    currentHunk.lines.add(diffLine)
                }
            }
        }
        currentFile?.let { fileDiffs.add(it) }

        logger.info("Parsed ${fileDiffs.size} file diffs")
        return fileDiffs
    }

    private fun parseHunkHeader(line: String): HunkHeader {
        // @@ -1,5 +1,7 @@ optional section header
        val regex = """@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""".toRegex()
        val match = regex.find(line)
        return HunkHeader(
            oldStart = match?.groupValues?.get(1)?.toIntOrNull() ?: 1,
            newStart = match?.groupValues?.get(2)?.toIntOrNull() ?: 1
        )
    }
}
