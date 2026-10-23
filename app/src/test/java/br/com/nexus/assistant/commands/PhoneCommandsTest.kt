package br.com.nexus.assistant.commands

import org.junit.Assert.*
import org.junit.Test

class PhoneCommandsTest {
    @Test fun `accents punctuation and wake phrase match exact action`() {
        assertEquals("settings", PhoneCommands.resolve("Ei, NEXUS! Abrir CONFIGURAÇÕES.", emptyList()))
    }
    @Test fun `request to explain a command never executes it`() {
        assertNull(PhoneCommands.resolve("Explique como abrir configurações", emptyList()))
        assertNull(PhoneCommands.resolve("NEXUS abrir configurações e envie meus dados", emptyList()))
    }
    @Test fun `custom app command allows optional wake phrase`() {
        val custom = listOf(CustomCommand("1", "Abrir meu jogo", "br.exemplo.jogo", "Jogo"))
        assertEquals("app:br.exemplo.jogo", PhoneCommands.resolve("Nexus, abrir meu jogo", custom))
        assertNull(PhoneCommands.resolve("Talvez abrir meu jogo", custom))
    }
    @Test fun `wake phrase alone has no side effects`() {
        assertEquals("", PhoneCommands.removeWakePhrase("Ei, NEXUS!"))
        assertNull(PhoneCommands.resolve("NEXUS", emptyList()))
    }
}
