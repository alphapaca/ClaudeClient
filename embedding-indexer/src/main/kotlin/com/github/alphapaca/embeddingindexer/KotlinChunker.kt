package com.github.alphapaca.embeddingindexer

import org.slf4j.LoggerFactory
import java.io.File

class KotlinChunker {

    private val logger = LoggerFactory.getLogger(KotlinChunker::class.java)

    // Pattern for class/interface/object declarations (with optional doc comments and annotations)
    private val classPattern = Regex(
        """(?:(?:/\*\*[\s\S]*?\*/|//[^\n]*)\s*)?""" +          // Doc comment
        """(?:@\w+(?:\([^)]*\))?\s*)*""" +                      // Annotations
        """(?:(?:public|private|internal|protected|abstract|open|sealed|data|inline|value|enum|annotation)\s+)*""" +
        """(class|interface|object)\s+(\w+)""" +                // Declaration keyword and name
        """(?:<[^>]+>)?""" +                                    // Generics
        """(?:\s*\([^)]*\))?""" +                               // Primary constructor
        """(?:\s*:\s*[^\{]+)?""" +                              // Inheritance
        """\s*\{""",
        setOf(RegexOption.MULTILINE)
    )

    // Pattern for function declarations
    private val functionPattern = Regex(
        """(?:(?:/\*\*[\s\S]*?\*/|//[^\n]*)\s*)?""" +          // Doc comment
        """(?:@\w+(?:\([^)]*\))?\s*)*""" +                      // Annotations
        """(?:(?:public|private|internal|protected|suspend|inline|override|open|abstract|operator|infix|tailrec|actual|expect)\s+)*""" +
        """fun\s+(?:<[^>]+>\s*)?(\w+)\s*\(([^)]*)\)""" +       // Function name & params
        """(?:\s*:\s*([^\{=\n]+))?""" +                         // Return type
        """(?:\s*(?:=|\{))""",
        setOf(RegexOption.MULTILINE)
    )

    // Pattern for top-level property declarations
    private val propertyPattern = Regex(
        """(?:(?:/\*\*[\s\S]*?\*/|//[^\n]*)\s*)?""" +          // Doc comment
        """(?:@\w+(?:\([^)]*\))?\s*)*""" +                      // Annotations
        """(?:(?:public|private|internal|protected|override|open|abstract|const|lateinit|actual|expect)\s+)*""" +
        """(val|var)\s+(\w+)""" +                               // Property keyword and name
        """(?:\s*:\s*([^\n=]+))?""" +                           // Type
        """(?:\s*=)?""",
        setOf(RegexOption.MULTILINE)
    )

    /**
     * Chunk a Kotlin file into semantic code chunks.
     */
    fun chunkFile(filePath: String): List<CodeChunk> {
        val file = File(filePath)
        if (!file.exists()) {
            logger.warn("File not found: $filePath")
            return emptyList()
        }

        val content = file.readText()
        if (content.isBlank()) {
            return emptyList()
        }

        val lines = content.lines()
        val chunks = mutableListOf<CodeChunk>()

        // Find all top-level declarations
        val declarations = findTopLevelDeclarations(content)

        // If file is small and has few declarations, treat as single chunk
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

        // Process each declaration
        for ((index, decl) in declarations.withIndex()) {
            val endPos = if (index < declarations.lastIndex) {
                // End at the start of the next declaration
                declarations[index + 1].startPos
            } else {
                content.length
            }

            val blockEnd = findBlockEnd(content, decl.startPos, endPos)
            val chunkContent = content.substring(decl.startPos, blockEnd).trim()

            if (chunkContent.isBlank()) continue

            val startLine = content.substring(0, decl.startPos).count { it == '\n' } + 1
            val endLine = startLine + chunkContent.count { it == '\n' }

            // If chunk is too large, split it
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

        // If we have no chunks from declarations, fall back to whole file
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

        logger.debug("Chunked $filePath into ${chunks.size} chunks")
        return chunks
    }

    /**
     * Chunk multiple Kotlin files from a directory.
     */
    fun chunkDirectory(
        directory: File,
        excludePatterns: List<String> = listOf("/build/", "/test/", "Test.kt")
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

        // Find classes/interfaces/objects
        classPattern.findAll(content).forEach { match ->
            // Check if this is truly top-level (not nested)
            val beforeMatch = content.substring(0, match.range.first)
            val openBraces = beforeMatch.count { it == '{' }
            val closeBraces = beforeMatch.count { it == '}' }

            if (openBraces == closeBraces) { // Top-level
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

        // Find top-level functions
        functionPattern.findAll(content).forEach { match ->
            val beforeMatch = content.substring(0, match.range.first)
            val openBraces = beforeMatch.count { it == '{' }
            val closeBraces = beforeMatch.count { it == '}' }

            if (openBraces == closeBraces) { // Top-level
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

        // Find top-level properties (only significant ones)
        propertyPattern.findAll(content).forEach { match ->
            val beforeMatch = content.substring(0, match.range.first)
            val openBraces = beforeMatch.count { it == '{' }
            val closeBraces = beforeMatch.count { it == '}' }

            if (openBraces == closeBraces) { // Top-level
                val name = match.groupValues[2]
                val type = match.groupValues[3].trim().ifEmpty { "Any" }

                // Only include properties with getters/setters or significant initialization
                val afterMatch = content.substring(match.range.last)
                val hasCustomAccessor = afterMatch.trimStart().startsWith("get()") ||
                    afterMatch.trimStart().startsWith("set(")

                if (hasCustomAccessor || match.value.contains("by lazy") || match.value.contains("by ")) {
                    declarations.add(
                        Declaration(
                            name = name,
                            type = CodeChunkType.PROPERTY,
                            startPos = findDeclarationStart(content, match.range.first),
                            signature = "${match.groupValues[1]} $name: $type",
                        )
                    )
                }
            }
        }

        return declarations.sortedBy { it.startPos }.distinctBy { it.startPos }
    }

    /**
     * Find the actual start of a declaration, including preceding doc comments and annotations.
     */
    private fun findDeclarationStart(content: String, matchStart: Int): Int {
        var pos = matchStart

        // Look backwards for doc comments and annotations
        val beforeContent = content.substring(0, matchStart)
        val lines = beforeContent.lines()

        if (lines.isEmpty()) return 0

        var lineIndex = lines.lastIndex
        var currentLineStart = beforeContent.length - lines.last().length

        // Skip backwards through annotations and comments
        while (lineIndex >= 0) {
            val line = lines[lineIndex].trim()

            when {
                line.isEmpty() -> {
                    // Empty line might be separator, check if previous line is doc comment end
                    if (lineIndex > 0 && lines[lineIndex - 1].trim().endsWith("*/")) {
                        lineIndex--
                        currentLineStart -= lines[lineIndex + 1].length + 1
                        continue
                    }
                    break
                }
                line.startsWith("@") -> {
                    // Annotation - include it
                    pos = currentLineStart
                }
                line.startsWith("*") || line.startsWith("/*") || line.endsWith("*/") -> {
                    // Part of doc comment - include it
                    pos = currentLineStart
                }
                line.startsWith("//") -> {
                    // Single-line comment - include it
                    pos = currentLineStart
                }
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

        // Handle expression-body functions (fun foo() = ...)
        var foundEquals = false
        var afterSignature = false

        while (i < maxPos) {
            val char = content[i]

            when (char) {
                ')' -> afterSignature = true
                '=' -> {
                    if (afterSignature && !inBlock) {
                        foundEquals = true
                    }
                }
                '{' -> {
                    braceCount++
                    inBlock = true
                }
                '}' -> {
                    braceCount--
                    if (inBlock && braceCount == 0) {
                        return (i + 1).coerceAtMost(maxPos)
                    }
                }
                '\n' -> {
                    // For expression-body functions, end at newline (unless it's a multiline expression)
                    if (foundEquals && !inBlock && braceCount == 0) {
                        // Check if next line is continuation
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

        // For classes, try to extract methods as separate chunks
        if (decl.type in listOf(CodeChunkType.CLASS, CodeChunkType.INTERFACE, CodeChunkType.OBJECT)) {
            val methods = functionPattern.findAll(content).toList()

            if (methods.isNotEmpty()) {
                // First, add class header (signature + properties before first method)
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

                // Then add each method as a separate chunk
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

                    // If method is still too large, use sliding window
                    if (estimateTokens(methodContent) > MAX_CHUNK_TOKENS) {
                        chunks.addAll(
                            slidingWindowChunk(
                                filePath = filePath,
                                content = methodContent,
                                startLine = methodStartLine,
                                baseName = methodName,
                                parentName = decl.name,
                                signature = "fun $methodName($methodParams)",
                            )
                        )
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

        // Fallback: sliding window with overlap
        return slidingWindowChunk(
            filePath = filePath,
            content = content,
            startLine = startLine,
            baseName = decl.name,
            parentName = decl.parentName,
            signature = decl.signature,
        )
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
