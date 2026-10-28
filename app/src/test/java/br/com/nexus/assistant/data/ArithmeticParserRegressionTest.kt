package br.com.nexus.assistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Safe-parser regression cases from the A03 report; no model or network involved. */
class ArithmeticParserRegressionTest {
    private fun result(expression: String): String =
        requireNotNull(ArithmeticTool.answerFor(expression)).substringBefore('\n').substringAfter(" = ")

    @Test fun `A03 reported expression respects multiplication before addition`() {
        assertEquals("2600", result("Quanto é 50 + 50 + 50 × 50?"))
        assertEquals("2600", result("NEXUS, quanto é 50 + 50 + 50 × 50?"))
        assertEquals("2600", result("Ei, NEXUS, por favor calcule 50 + 50 + 50 × 50"))
        assertEquals("2600", result("Quanto é 50 + 50 + 50 × 50, por favor?"))
    }

    @Test fun `all elementary operations parentheses and powers are deterministic`() {
        mapOf("37+15" to "52", "37-15" to "22", "37*15" to "555", "37/2" to "18,5",
            "(50+50+50)*50" to "7500", "2^3^2" to "512", "-2^2" to "-4",
            "(-2)^2" to "4", "2^-3" to "0,125", "10+10÷10×10" to "20",
            "Quanto é 25 vezes 18?" to "450").forEach { (input, expected) ->
            assertEquals(input, expected, result(input))
        }
    }

    @Test fun `spoken multiplication and integer powers preserve precedence`() {
        assertEquals("6", result("Quanto é 2 multiplicado por 3?"))
        assertEquals("8", result("Calcule 2 elevado a 3"))
        assertEquals("4", result("Quanto é 2 ao quadrado?"))
        assertEquals("8", result("Quanto é 2 ao cubo?"))
        assertEquals("25", result("Quanto é (2 + 3) elevado ao 2?"))
        assertEquals("-4", result("Calcule -2 ao quadrado"))
    }

    @Test fun `explicit explanation uses the real evaluated steps`() {
        val answer = requireNotNull(ArithmeticTool.answerFor("Quanto é 50 + 50 + 50 × 50? Explique o cálculo."))
        assertTrue(answer.startsWith("50 + 50 + 50 × 50 = 2600"))
        assertTrue(answer.indexOf("50 × 50 = 2500") < answer.indexOf("100 + 2500 = 2600"))
    }

    @Test fun `invalid or expensive arithmetic returns bounded errors`() {
        mapOf("1/0" to "zero", "0^-1" to "Zero", "0^0" to "Zero", "2^101" to "expoentes",
            "2^0,5" to "expoentes", "2+(3" to "Não entendi", "1+".repeat(200) + "1" to "longo",
            "(".repeat(30) + "2+2" + ")".repeat(30) to "demais",
            "9".repeat(81) + "+1" to "80 algarismos").forEach { (input, expected) ->
            assertTrue(input, requireNotNull(ArithmeticTool.answerFor(input)).contains(expected))
        }
    }

    @Test fun `numbers in prose or code are not turned into calculator requests`() {
        listOf("Escreva uma redação de 50 linhas sobre 50 tecnologias", "Explique 50+50+50×50 em Kotlin",
            "50+50+50×50; System.exit(0)", "Quanto custa 50 + 50 produtos?", "2 elevado a três",
            "me faz um texto de desculpa", "Qual é meu nome?", "Quem multiplicou por 2?").forEach {
            assertNull(it, ArithmeticTool.answerFor(it))
        }
    }
}
