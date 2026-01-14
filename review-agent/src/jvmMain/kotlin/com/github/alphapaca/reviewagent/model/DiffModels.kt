package com.github.alphapaca.reviewagent.model

enum class DiffLineType {
    ADDITION,
    DELETION,
    CONTEXT
}

data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val newLineNumber: Int?,
    val oldLineNumber: Int?
)

data class DiffHunk(
    val header: String,
    val oldStart: Int,
    val newStart: Int,
    val lines: MutableList<DiffLine> = mutableListOf()
)

data class FileDiff(
    val filePath: String,
    val hunks: MutableList<DiffHunk> = mutableListOf()
)

data class HunkHeader(
    val oldStart: Int,
    val newStart: Int
)
