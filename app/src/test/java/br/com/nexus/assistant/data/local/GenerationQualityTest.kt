package br.com.nexus.assistant.data.local

import java.nio.charset.CharacterCodingException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GenerationQualityTest {
    private fun result(text: String = "Olá, Calebe! Sou NEXUS. 🚀", finish: String = "end_of_turn") = JSONObject()
        .put("text", text).put("finishReason", finish).put("promptTokens", 500).put("generatedTokens", 64)
        .put("inputMessages", 21).put("usedMessages", 17).put("droppedMessages", 4)
        .put("contextTokens", 2048).put("maxOutputTokens", 512)

    @Test fun parsesExactModelTextAndActualContextStatistics() {
        val exact = "  Olá, coração! 🚀\n\nSegundo parágrafo.  "
        val parsed = LocalGenerationResult.parse(result(exact).toString().toByteArray())
        assertEquals(exact, parsed.text)
        assertEquals(500, parsed.promptTokens)
        assertEquals(17, parsed.usedMessages)
        assertEquals(4, parsed.droppedMessages)
        assertFalse(parsed.truncated)
    }

    @Test fun lengthFinishIsVisibleAsMetadataWithoutInventingAnEnding() {
        val parsed = LocalGenerationResult.parse(result("Texto interrompido antes do fim", "token_limit").toString().toByteArray())
        assertTrue(parsed.truncated)
        assertEquals("Texto interrompido antes do fim", parsed.text)
    }

    @Test fun cachedTokensAreAccountedForWithoutChangingModelText() {
        val payload = result("Resposta exata").put("cachedPromptTokens", 420).put("evaluatedPromptTokens", 80)
        val parsed = LocalGenerationResult.parse(payload.toString().toByteArray())
        assertEquals(420, parsed.cachedPromptTokens)
        assertEquals(80, parsed.evaluatedPromptTokens)
        assertEquals("Resposta exata", parsed.text)
        assertEquals(500, LocalGenerationResult.parse(result().toString().toByteArray()).evaluatedPromptTokens)
        for (invalid in listOf(payload.put("evaluatedPromptTokens", 81).toString(),
            result().put("cachedPromptTokens", -1).toString(),
            result().put("cachedPromptTokens", 501).toString())) {
            assertThrows(IllegalArgumentException::class.java) { LocalGenerationResult.parse(invalid.toByteArray()) }
        }
    }

    @Test fun rejectsInvalidUtf8InsteadOfDisplayingReplacementCharacters() {
        assertThrows(CharacterCodingException::class.java) {
            LocalGenerationResult.strictUtf8(byteArrayOf(0xc3.toByte(), 0x28))
        }
    }

    @Test fun invalidCountersAndUnknownFinishDoNotBecomeSuccess() {
        for (invalid in listOf(
            result().put("finishReason", "unknown"),
            result().put("usedMessages", 22), result().put("droppedMessages", 0),
            result().put("maxOutputTokens", 2000), result().put("promptTokens", 2000),
        )) assertThrows(IllegalArgumentException::class.java) { LocalGenerationResult.parse(invalid.toString().toByteArray()) }
    }

    @Test fun repetitionIsDetectedWithoutRewritingTheAnswer() {
        val block = "Não tenho nada pelo que me desculpar. Estou aqui para ajudar você. "
        val answer = block.repeat(4)
        assertEquals(GenerationQualityIssue.EXCESSIVE_REPETITION, GenerationQuality.inspect(answer))
        assertEquals(block.repeat(4), answer)
    }

    @Test fun validParagraphsAndRepeatedCodeIdentifiersRemainUntouched() {
        val answer = "Quero pedir desculpas pela minha atitude. Reconheço que te magoei e quero ouvir o que você sentiu.\n\nGostaria de conversar com calma e reparar o que estiver ao meu alcance."
        assertNull(GenerationQuality.inspect(answer))
        val code = "Exemplo de laço:\n```kotlin\n" + "println(\"teste repetido pela aplicação\")\n".repeat(10) + "```"
        assertNull(GenerationQuality.inspect(code))
    }

    @Test fun discussingAControlTokenAsCodeIsNotAProtocolFailure() {
        assertNull(GenerationQuality.inspect("O marcador `<|im_start|>` é usado para delimitar mensagens em alguns templates de chat."))
    }

    @Test fun emptyAndInternalControlTokensAreFailures() {
        assertEquals(GenerationQualityIssue.EMPTY, GenerationQuality.inspect(" \n"))
        assertEquals(GenerationQualityIssue.INTERNAL_MARKERS, GenerationQuality.inspect("<|im_start|>assistant\nOlá"))
    }
}
