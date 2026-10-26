package br.com.nexus.assistant.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArithmeticToolTest {
    private fun result(expression: String) = ArithmeticTool.answerFor(expression)!!.substringBefore('\n').substringAfter(" = ")

    @Test fun `same precedence evaluates multiplication and division from left to right`() {
        assertEquals("20", result("10+10÷10×10"))
        assertEquals("16", result("8/2*4"))
        assertEquals("1", result("8-4+2-5"))
    }
    @Test fun `parentheses decimals signs and exponent precedence are real arithmetic`() {
        assertEquals("20", result("Quanto é (2+3)×4?"))
        assertEquals("3,75", result("calcule 1,5 + 2.25"))
        assertEquals("-4", result("-2^2"))
        assertEquals("4", result("(-2)^2"))
        assertEquals("512", result("2^3^2"))
        assertEquals("0,125", result("2^-3"))
        assertEquals("6", result("2 x 3"))
    }
    @Test fun `spoken operators produce exact results without an automatic commentary footer`() {
        assertEquals("450", result("Quanto é 25 vezes 18?"))
        assertEquals("20", result("10 mais 10 dividido por 10 vezes 10"))
        assertFalse(ArithmeticTool.answerFor("25 vezes 18")!!.contains('\n'))
        assertNull(ArithmeticTool.answerFor("Escreva um texto sobre mais oportunidades"))
    }
    @Test fun `ordinary conversation and embedded calculations are left to language provider`() {
        listOf("Olá", "10", "Explique 10+10", "Tenho 10 anos", "Como programar 2+2 em Kotlin?", "abra configurações").forEach {
            assertNull(it, ArithmeticTool.answerFor(it))
        }
    }
    @Test fun `invalid operations return truthful bounded errors`() {
        assertTrue(ArithmeticTool.answerFor("1/0")!!.contains("zero"))
        assertTrue(ArithmeticTool.answerFor("2+(3")!!.contains("Não entendi"))
        assertTrue(ArithmeticTool.answerFor("1,2.3+1")!!.contains("Não entendi"))
        assertTrue(ArithmeticTool.answerFor("2^101")!!.contains("expoentes"))
        assertTrue(ArithmeticTool.answerFor("(".repeat(30)+"2+2"+")".repeat(30))!!.contains("demais"))
        assertTrue(ArithmeticTool.answerFor("1+".repeat(200)+"1")!!.contains("longo"))
    }
    @Test fun `actual coordinator uses arithmetic before either language provider`() = runBlocking<Unit> {
        val unexpected = object : AiProvider {
            override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String = error("Arithmetic must not be guessed by an LLM")
        }
        val coordinator = AiCoordinator(unexpected, unexpected)
        AiMode.entries.forEach { mode ->
            assertTrue(coordinator.complete(NexusSettings(aiMode=mode), "", listOf(AiMessage("user", "10+10÷10×10")), emptyList()).startsWith("10+10÷10×10 = 20"))
        }
    }
    @Test fun `actual coordinator still dispatches general conversation to explicitly chosen provider`() = runBlocking<Unit> {
        fun provider(name: String) = object : AiProvider {
            override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>) = "$name:$token:${messages.last().content}:${memories.single()}"
        }
        val coordinator = AiCoordinator(provider("local"), provider("custom"))
        val input = listOf(AiMessage("user", "Olá"))
        assertEquals("local::Olá:memória", coordinator.complete(NexusSettings(aiMode=AiMode.NEXUS_LOCAL), "secret", input, listOf("memória")))
        assertEquals("custom:secret:Olá:memória", coordinator.complete(NexusSettings(aiMode=AiMode.CUSTOM), "secret", input, listOf("memória")))
    }
}
