package com.github.alphapaca.embeddingindexer

class TextChunker {

    private val sentenceDelimiterPattern = Regex("""(?<=[.!?])\s+""")

    fun chunkBySentences(text: String): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        var currentOffset = 0

        val sentences = text.split(sentenceDelimiterPattern)

        for (sentence in sentences) {
            val trimmed = sentence.trim()
            if (trimmed.length < MIN_CHUNK_LENGTH) {
                currentOffset += sentence.length + 1 // +1 for the whitespace that was split on
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

    companion object {
        private const val MIN_CHUNK_LENGTH = 5
    }
}
