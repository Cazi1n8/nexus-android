package br.com.nexus.assistant.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunksTest {
    @Test fun longResponseIsSplitWithoutDroppingItsWords() {
        val original = "Uma resposta longa. Com instruções claras para o usuário e exemplos."
        val chunks = speechChunks(original, 25)
        assertTrue(chunks.all { it.length <= 25 })
        assertEquals(original.split(Regex("\\s+")), chunks.joinToString(" ").trim().split(Regex("\\s+")))
    }

    @Test fun giantUnbrokenWordStaysWithinTheEngineLimit() {
        val original = "x".repeat(100)
        val chunks = speechChunks(original, 23)
        assertTrue(chunks.all { it.length <= 23 })
        assertEquals(original, chunks.joinToString(""))
    }

    @Test fun surrogatePairsAreNeverSeparated() {
        val original = "🙂".repeat(20)
        val chunks = speechChunks(original, 7)
        assertEquals(original, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 7 && !Character.isHighSurrogate(it.last()) && !Character.isLowSurrogate(it.first()) })
    }

    @Test fun blankInputDoesNotQueueAnEmptyUtterance() {
        assertTrue(speechChunks("   ", 10).isEmpty())
    }
}
