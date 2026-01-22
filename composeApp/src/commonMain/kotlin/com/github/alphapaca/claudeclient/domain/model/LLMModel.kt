package com.github.alphapaca.claudeclient.domain.model

enum class LLMModel(
    val apiName: String,
    val displayName: String,
    val inputPricePerMillionTokens: Double,
    val outputPricePerMillionTokens: Double,
) {
    CLAUDE_SONNET_4_5("claude-sonnet-4-5", "Claude Sonnet 4.5", 3.0, 15.0),
    CLAUDE_OPUS_4_5("claude-opus-4-5", "Claude Opus 4.5", 5.0, 25.0),
    DEEPSEEK_V3_2("deepseek-chat", "DeepSeek V3.2", 0.28, 0.42),
    LLAMA_3_2("llama3.2:1b", "Llama 3.2 1B", 0.0, 0.0);

    fun calculateCost(inputTokens: Int, outputTokens: Int): Double {
        return (inputTokens * inputPricePerMillionTokens + outputTokens * outputPricePerMillionTokens) / 1_000_000.0
    }

    companion object {
        val DEFAULT = CLAUDE_SONNET_4_5

        fun fromApiName(apiName: String): LLMModel {
            return entries.find { it.apiName == apiName } ?: DEFAULT
        }
    }
}
