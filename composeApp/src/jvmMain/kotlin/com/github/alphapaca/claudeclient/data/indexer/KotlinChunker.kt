package com.github.alphapaca.claudeclient.data.indexer

import co.touchlab.kermit.Logger
import java.io.File

class KotlinChunker {

    private val classPattern = Regex(
        """(?:(?:/\*\*[\s\S]*?\*/|//[^\n]*)\s*)?""" +
        """(?:@\w+(?:\([^)]*\))?\s*)*""" +
        """(?:(?:public|private|internal|protected|abstract|open|sealed|data|inline|value|enum|annotation)\s+)*""" +
        """(class|interface|object)\s+(\w+)""" +
        """(?:<[^>]+>)?""" +
        """(?:\s*\([^)]*\))?""" +
        """(?:\s*:\s*[^\{]+)?""" +
        """\s*\{""",
        setOf(RegexOption.MULTILINE)
    )

    private val functionPattern = Regex(
        """(?:(?:/\*\*[\s\S]*?\*/|//[^\n]*)\s*)?""" +
        """(?:@\w+(?:\([^)]*\))?\s*)*""" +
        """(?:(?:public|private|internal|protected|suspend|inline|override|open|abstract|operator|infix|tailrec|actual|expect)\s+)*""" +
        """fun\s+(?:<[^>]+>\s*)?(\w+)\s*\(([^)]*)\)""" +
        """(?:\s*:\s*([^\{=\n]+))?""" +
        """(?:\s*(?:=|\{))""",
        setOf(RegexOption.MULTILINE)
    )

    fun chunkFile(filePath: String): List<CodeChunk> {
        val file = File(filePath)
        if (!file.exists()) {
            Logger.w("KotlinChunker") { "File not found: $filePath" }
            return emptyList()
        }

        val content = file.readText()
        if (content.isBlank()) {
            return emptyList()
        }

        val lines = content.lines()
        val chunks = mutableListOf<CodeChunk>()
        val declarations = findTopLevelDeclarations(content)

        if (declarations.isEmpty() || estimateTokens(content) < MAX_CHUNK_TOKENS) {
            chunks.add(
                CodeChunk(
                    content = addContextPrefix(filePath, null, content),
                    filePath = filePath,
                    startLine = 1,
                    endLine = lines.size,
                    chunkType = CodeChunkType.FILE,
                    name = file.nameWithoutExtension,
                )
            )
            return chunks
        }

        for ((index, decl) in declarations.withIndex()) {
            val endPos = if (index < declarations.lastIndex) {
                declarations[index + 1].startPos
            } else {
                content.length
            }

            val blockEnd = findBlockEnd(content, decl.startPos, endPos)
            val chunkContent = content.substring(decl.startPos, blockEnd).trim()

            if (chunkContent.isBlank()) continue

            val startLine = content.substring(0, decl.startPos).count { it == '\n' } + 1
            val endLine = startLine + chunkContent.count { it == '\n' }

            if (estimateTokens(chunkContent) > MAX_CHUNK_TOKENS) {
                chunks.addAll(splitLargeDeclaration(filePath, decl, chunkContent, startLine))
            } else {
                chunks.add(
                    CodeChunk(
                        content = addContextPrefix(filePath, decl.parentName, chunkContent),
                        filePath = filePath,
                        startLine = startLine,
                        endLine = endLine,
                        chunkType = decl.type,
                        name = decl.name,
                        parentName = decl.parentName,
                        signature = decl.signature,
                    )
                )
            }
        }

        if (chunks.isEmpty()) {
            chunks.add(
                CodeChunk(
                    content = addContextPrefix(filePath, null, content),
                    filePath = filePath,
                    startLine = 1,
                    endLine = lines.size,
                    chunkType = CodeChunkType.FILE,
                    name = file.nameWithoutExtension,
                )
            )
        }

        return chunks
    }

    fun chunkDirectory(
        directory: File,
        excludePatterns: List<String> = listOf("/build/", "/test/", "Test.kt", "/.gradle/", "/generated/")
    ): List<CodeChunk> {
        return directory
            .walkTopDown()
            .filter { it.extension == "kt" }
            .filter { file -> excludePatterns.none { pattern -> file.path.contains(pattern) } }
            .flatMap { file -> chunkFile(file.absolutePath) }
            .toList()
    }

    private fun findTopLevelDeclarations(content: String): List<Declaration> {
        val declarations = mutableListOf<Declaration>()

        classPattern.findAll(content).forEach { match ->
            val beforeMatch = content.substring(0, match.range.first)
            val openBraces = beforeMatch.count { it == '{' }
            val closeBraces = beforeMatch.count { it == '}' }

            if (openBraces == closeBraces) {
                declarations.add(
                    Declaration(
                        name = match.groupValues[2],
                        type = when (match.groupValues[1]) {
                            "interface" -> CodeChunkType.INTERFACE
                            "object" -> CodeChunkType.OBJECT
                            else -> CodeChunkType.CLASS
                        },
                        startPos = findDeclarationStart(content, match.range.first),
                        signature = match.value.substringBefore('{').trim(),
                    )
                )
            }
        }

        functionPattern.findAll(content).forEach { match ->
            val beforeMatch = content.substring(0, match.range.first)
            val openBraces = beforeMatch.count { it == '{' }
            val closeBraces = beforeMatch.count { it == '}' }

            if (openBraces == closeBraces) {
                val name = match.groupValues[1]
                val params = match.groupValues[2]
                val returnType = match.groupValues[3].trim().ifEmpty { "Unit" }

                declarations.add(
                    Declaration(
                        name = name,
                        type = CodeChunkType.FUNCTION,
                        startPos = findDeclarationStart(content, match.range.first),
                        signature = "fun $name($params): $returnType",
                    )
                )
            }
        }

        return declarations.sortedBy { it.startPos }.distinctBy { it.startPos }
    }

    private fun findDeclarationStart(content: String, matchStart: Int): Int {
        var pos = matchStart
        val beforeContent = content.substring(0, matchStart)
        val lines = beforeContent.lines()

        if (lines.isEmpty()) return 0

        var lineIndex = lines.lastIndex
        var currentLineStart = beforeContent.length - lines.last().length

        while (lineIndex >= 0) {
            val line = lines[lineIndex].trim()

            when {
                line.isEmpty() -> {
                    if (lineIndex > 0 && lines[lineIndex - 1].trim().endsWith("*/")) {
                        lineIndex--
                        currentLineStart -= lines[lineIndex + 1].length + 1
                        continue
                    }
                    break
                }
                line.startsWith("@") -> pos = currentLineStart
                line.startsWith("*") || line.startsWith("/*") || line.endsWith("*/") -> pos = currentLineStart
                line.startsWith("//") -> pos = currentLineStart
                else -> break
            }

            lineIndex--
            if (lineIndex >= 0) {
                currentLineStart -= lines[lineIndex + 1].length + 1
            }
        }

        return pos.coerceAtLeast(0)
    }

    private fun findBlockEnd(content: String, startPos: Int, maxPos: Int): Int {
        var braceCount = 0
        var inBlock = false
        var i = startPos
        var foundEquals = false
        var afterSignature = false

        while (i < maxPos) {
            when (content[i]) {
                ')' -> afterSignature = true
                '=' -> if (afterSignature && !inBlock) foundEquals = true
                '{' -> { braceCount++; inBlock = true }
                '}' -> {
                    braceCount--
                    if (inBlock && braceCount == 0) return (i + 1).coerceAtMost(maxPos)
                }
                '\n' -> {
                    if (foundEquals && !inBlock && braceCount == 0) {
                        val remaining = content.substring(i + 1, maxPos)
                        val nextLine = remaining.lines().firstOrNull()?.trim() ?: ""
                        if (!nextLine.startsWith(".") && !nextLine.startsWith("?") &&
                            !nextLine.startsWith("+") && !nextLine.startsWith("-")) {
                            return i
                        }
                    }
                }
            }
            i++
        }
        return maxPos
    }

    private fun splitLargeDeclaration(
        filePath: String,
        decl: Declaration,
        content: String,
        startLine: Int
    ): List<CodeChunk> {
        val chunks = mutableListOf<CodeChunk>()

        if (decl.type in listOf(CodeChunkType.CLASS, CodeChunkType.INTERFACE, CodeChunkType.OBJECT)) {
            val methods = functionPattern.findAll(content).toList()

            if (methods.isNotEmpty()) {
                val firstMethodStart = methods.first().range.first
                val header = content.substring(0, firstMethodStart).trim()

                if (header.isNotBlank() && estimateTokens(header) >= MIN_CHUNK_TOKENS) {
                    val headerEndLine = startLine + header.count { it == '\n' }
                    chunks.add(
                        CodeChunk(
                            content = addContextPrefix(filePath, null, header),
                            filePath = filePath,
                            startLine = startLine,
                            endLine = headerEndLine,
                            chunkType = decl.type,
                            name = "${decl.name}_header",
                            signature = decl.signature,
                        )
                    )
                }

                for ((index, match) in methods.withIndex()) {
                    val methodEnd = if (index < methods.lastIndex) {
                        findBlockEnd(content, match.range.first, methods[index + 1].range.first)
                    } else {
                        findBlockEnd(content, match.range.first, content.length)
                    }

                    val methodContent = content.substring(match.range.first, methodEnd).trim()
                    if (methodContent.isBlank()) continue

                    val methodStartLine = startLine + content.substring(0, match.range.first).count { it == '\n' }
                    val methodEndLine = methodStartLine + methodContent.count { it == '\n' }
                    val methodName = match.groupValues[1]
                    val methodParams = match.groupValues[2]

                    if (estimateTokens(methodContent) > MAX_CHUNK_TOKENS) {
                        chunks.addAll(slidingWindowChunk(filePath, methodContent, methodStartLine, methodName, decl.name, "fun $methodName($methodParams)"))
                    } else {
                        chunks.add(
                            CodeChunk(
                                content = addContextPrefix(filePath, decl.name, methodContent),
                                filePath = filePath,
                                startLine = methodStartLine,
                                endLine = methodEndLine,
                                chunkType = CodeChunkType.FUNCTION,
                                name = methodName,
                                parentName = decl.name,
                                signature = "fun $methodName($methodParams)",
                            )
                        )
                    }
                }

                return chunks
            }
        }

        return slidingWindowChunk(filePath, content, startLine, decl.name, decl.parentName, decl.signature)
    }

    private fun slidingWindowChunk(
        filePath: String,
        content: String,
        startLine: Int,
        baseName: String,
        parentName: String?,
        signature: String?,
    ): List<CodeChunk> {
        val chunks = mutableListOf<CodeChunk>()
        val lines = content.lines()
        val chunkSize = TARGET_CHUNK_LINES
        val overlap = OVERLAP_LINES
        var lineStart = 0
        var partNumber = 1

        while (lineStart < lines.size) {
            val lineEnd = (lineStart + chunkSize).coerceAtMost(lines.size)
            val chunkLines = lines.subList(lineStart, lineEnd)
            val chunkContent = chunkLines.joinToString("\n")

            if (chunkContent.isNotBlank()) {
                chunks.add(
                    CodeChunk(
                        content = addContextPrefix(filePath, parentName, chunkContent),
                        filePath = filePath,
                        startLine = startLine + lineStart,
                        endLine = startLine + lineEnd - 1,
                        chunkType = CodeChunkType.FUNCTION,
                        name = "${baseName}_part$partNumber",
                        parentName = parentName,
                        signature = signature,
                    )
                )
                partNumber++
            }

            lineStart += chunkSize - overlap
        }

        return chunks
    }

    private fun addContextPrefix(filePath: String, parentClass: String?, content: String): String {
        return buildString {
            appendLine("// File: $filePath")
            if (parentClass != null) {
                appendLine("// Class: $parentClass")
            }
            appendLine()
            append(content)
        }
    }

    private fun estimateTokens(text: String): Int {
        return (text.length / CHARS_PER_TOKEN).coerceAtLeast(1)
    }

    private data class Declaration(
        val name: String,
        val type: CodeChunkType,
        val startPos: Int,
        val signature: String? = null,
        val parentName: String? = null,
    )

    companion object {
        private const val CHARS_PER_TOKEN = 4
        private const val MAX_CHUNK_TOKENS = 800
        private const val MIN_CHUNK_TOKENS = 50
        private const val TARGET_CHUNK_LINES = 60
        private const val OVERLAP_LINES = 10
    }
}
