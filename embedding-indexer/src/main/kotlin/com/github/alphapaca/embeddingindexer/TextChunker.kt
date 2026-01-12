package com.github.alphapaca.embeddingindexer

class TextChunker {

    private val sentenceDelimiterPattern = Regex("""(?<=[.!?])\s+""")
    private val paragraphDelimiterPattern = Regex("""\n\s*\n""")

    /**
     * Simple sentence-based chunking (legacy method).
     * Each sentence becomes a separate chunk.
     */
    fun chunkBySentences(text: String): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        var currentOffset = 0

        val sentences = text.split(sentenceDelimiterPattern)

        for (sentence in sentences) {
            val trimmed = sentence.trim()
            if (trimmed.length < MIN_CHUNK_LENGTH) {
                currentOffset += sentence.length + 1
                continue
            }

            val startOffset = text.indexOf(sentence, currentOffset)
            val endOffset = startOffset + sentence.length

            chunks.add(
                TextChunk(
                    content = trimmed,
                    startOffset = startOffset,
                    endOffset = endOffset,
                )
            )

            currentOffset = endOffset
        }

        return chunks
    }

    /**
     * Paragraph-based chunking with overlap.
     * Groups paragraphs into chunks of target token size with overlap.
     *
     * @param text The input text to chunk
     * @param targetTokens Target tokens per chunk (default: 400)
     * @param overlapParagraphs Number of paragraphs to overlap between chunks (default: 1)
     * @param minParagraphLength Minimum paragraph length to include (default: 50 chars)
     */
    fun chunkByParagraphs(
        text: String,
        targetTokens: Int = DEFAULT_TARGET_TOKENS,
        overlapParagraphs: Int = DEFAULT_OVERLAP_PARAGRAPHS,
        minParagraphLength: Int = MIN_PARAGRAPH_LENGTH,
    ): List<TextChunk> {
        // Split into paragraphs
        val paragraphTexts = text.split(paragraphDelimiterPattern)
            .map { it.trim() }
            .filter { it.length >= minParagraphLength }

        if (paragraphTexts.isEmpty()) {
            return emptyList()
        }

        // Track paragraph positions in original text
        val paragraphs = mutableListOf<ParagraphInfo>()
        var searchStart = 0
        var paragraphNumber = 0
        for (paraText in paragraphTexts) {
            val startOffset = text.indexOf(paraText, searchStart)
            if (startOffset >= 0) {
                paragraphs.add(
                    ParagraphInfo(
                        content = paraText,
                        startOffset = startOffset,
                        endOffset = startOffset + paraText.length,
                        estimatedTokens = estimateTokens(paraText),
                        paragraphNumber = paragraphNumber,
                    )
                )
                searchStart = startOffset + paraText.length
                paragraphNumber++
            }
        }

        // Group paragraphs into chunks with overlap
        val chunks = mutableListOf<TextChunk>()
        var i = 0

        while (i < paragraphs.size) {
            var currentTokens = 0
            var endIndex = i

            // Add paragraphs until we reach target token count
            while (endIndex < paragraphs.size && currentTokens < targetTokens) {
                currentTokens += paragraphs[endIndex].estimatedTokens
                endIndex++
            }

            // Ensure at least one paragraph per chunk
            if (endIndex == i) {
                endIndex = i + 1
            }

            // Create chunk from paragraphs[i] to paragraphs[endIndex-1]
            val chunkParagraphs = paragraphs.subList(i, endIndex)
            val chunkContent = chunkParagraphs.joinToString("\n\n") { it.content }
            val startOffset = chunkParagraphs.first().startOffset
            val endOffset = chunkParagraphs.last().endOffset
            val paragraphNumber = chunkParagraphs.first().paragraphNumber

            chunks.add(
                TextChunk(
                    content = chunkContent,
                    startOffset = startOffset,
                    endOffset = endOffset,
                    paragraphNumber = paragraphNumber,
                )
            )

            // Move forward with overlap
            // If we have overlap, step back by overlapParagraphs
            val step = maxOf(1, endIndex - i - overlapParagraphs)
            i += step
        }

        return chunks
    }

    /**
     * Estimate token count for text.
     * Rough approximation: ~4 characters per token for English.
     */
    private fun estimateTokens(text: String): Int {
        return (text.length / CHARS_PER_TOKEN).coerceAtLeast(1)
    }

    private data class ParagraphInfo(
        val content: String,
        val startOffset: Int,
        val endOffset: Int,
        val estimatedTokens: Int,
        val paragraphNumber: Int,
    )

    companion object {
        private const val MIN_CHUNK_LENGTH = 5
        private const val MIN_PARAGRAPH_LENGTH = 50
        private const val DEFAULT_TARGET_TOKENS = 400
        private const val DEFAULT_OVERLAP_PARAGRAPHS = 1
        private const val CHARS_PER_TOKEN = 4
    }
}
