package br.com.nexus.assistant.data.local

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.json.JSONObject

/** Exact model text plus technical completion metadata; never a generated-text footer. */
data class LocalGenerationResult(
    val text: String,
    val finishReason: String,
    val promptTokens: Int,
    val generatedTokens: Int,
    val inputMessages: Int,
    val usedMessages: Int,
    val droppedMessages: Int,
    val contextTokens: Int,
    val maxOutputTokens: Int,
    val cachedPromptTokens: Int = 0,
    val evaluatedPromptTokens: Int = promptTokens,
) {
    val truncated: Boolean get() = finishReason == "token_limit"

    companion object {
        fun parse(bytes: ByteArray): LocalGenerationResult {
            val json = JSONObject(strictUtf8(bytes))
            val result = LocalGenerationResult(
                text = json.getString("text"), finishReason = json.getString("finishReason"),
                promptTokens = json.getInt("promptTokens"), generatedTokens = json.getInt("generatedTokens"),
                inputMessages = json.getInt("inputMessages"), usedMessages = json.getInt("usedMessages"),
                droppedMessages = json.getInt("droppedMessages"), contextTokens = json.getInt("contextTokens"),
                maxOutputTokens = json.getInt("maxOutputTokens"),
                cachedPromptTokens = json.optInt("cachedPromptTokens", 0),
                evaluatedPromptTokens = json.optInt("evaluatedPromptTokens", json.getInt("promptTokens")),
            )
            require(result.finishReason in setOf("end_of_turn", "token_limit")) { "Finalização de geração local inválida." }
            require(result.promptTokens >= 0 && result.generatedTokens in 0..result.maxOutputTokens &&
                result.maxOutputTokens in 1..1024 && result.contextTokens in 512..4096 &&
                result.inputMessages in 1..256 && result.usedMessages in 1..result.inputMessages &&
                result.droppedMessages == result.inputMessages - result.usedMessages &&
                result.cachedPromptTokens in 0..result.promptTokens &&
                result.evaluatedPromptTokens == result.promptTokens - result.cachedPromptTokens &&
                result.promptTokens + result.maxOutputTokens <= result.contextTokens) { "Metadados de geração local inconsistentes." }
            return result
        }

        fun strictUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }
}

/** No prompt, response, name, image, path or credential is stored in this diagnostic. */
data class LocalGenerationDiagnostics(
    val state: String = "idle",
    val model: String = "",
    val inputMessages: Int = 0,
    val usedMessages: Int = 0,
    val promptTokens: Int = 0,
    val generatedTokens: Int = 0,
    val cachedPromptTokens: Int = 0,
    val evaluatedPromptTokens: Int = 0,
    val contextTokens: Int = 0,
    val elapsedMilliseconds: Long = 0,
    val finishReason: String? = null,
    val qualityIssue: String? = null,
)

enum class GenerationQualityIssue { EMPTY, INTERNAL_MARKERS, EXCESSIVE_REPETITION }

/** Detects a failed output; does not edit, trim repeated sentences or replace model text. */
object GenerationQuality {
    fun inspect(text: String): GenerationQualityIssue? {
        if (text.isBlank()) return GenerationQualityIssue.EMPTY
        val beginning = text.trimStart()
        if (listOf("<|im_start|>", "<|im_end|>", "<|endoftext|>").any(beginning::startsWith)) {
            return GenerationQualityIssue.INTERNAL_MARKERS
        }
        // Code often legitimately repeats identifiers. Ignore fenced code only
        // for this quality check; the original answer remains completely intact.
        val prose = text.replace(Regex("```[\\s\\S]*?(?:```|$)"), " ")
        val words = Regex("[\\p{L}\\p{N}]+").findAll(prose.lowercase(java.util.Locale.ROOT)).map { it.value }.toList()
        if (words.size < 24) return null
        // Three immediately repeated blocks are a signal of generation collapse,
        // not merely a word occurring frequently in a long, useful response.
        for (width in 4..minOf(32, words.size / 3)) {
            for (start in 0..words.size - width * 3) {
                var same = true
                for (offset in 0 until width) {
                    if (words[start + offset] != words[start + width + offset] ||
                        words[start + offset] != words[start + width * 2 + offset]) { same = false; break }
                }
                if (same) return GenerationQualityIssue.EXCESSIVE_REPETITION
            }
        }
        return null
    }
}
