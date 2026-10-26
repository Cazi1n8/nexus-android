package br.com.nexus.assistant.device

import br.com.nexus.assistant.device.voice.VoiceConversationBudget
import org.junit.Assert.*
import org.junit.Test

class VoiceConversationBudgetTest {
    @Test fun continuousSilenceHasOnlyTwoBoundedRetriesAndManualActionCanResume() {
        val budget = VoiceConversationBudget(0)
        assertEquals(1500L, budget.nextSilenceRetry(10, true, true))
        assertEquals(3000L, budget.nextSilenceRetry(20, true, true))
        assertNull(budget.nextSilenceRetry(30, true, true))
        budget.manualListen()
        assertEquals(1500L, budget.nextSilenceRetry(40, true, true))
    }
    @Test fun networkAndManualModesDoNotEnterAnAutomaticRecognitionLoop() {
        val budget = VoiceConversationBudget(0)
        assertNull(budget.nextSilenceRetry(10, true, false))
        assertNull(budget.nextSilenceRetry(10, false, true))
    }
    @Test fun durationAndTurnBudgetsRequireAnExplicitNewSession() {
        val budget = VoiceConversationBudget(100, durationMs = 1000, maxTurns = 2)
        assertFalse(budget.expired(1099))
        assertTrue(budget.expired(1100))
        assertNull(budget.nextSilenceRetry(1100, true, true))
        val turns = VoiceConversationBudget(0, maxTurns = 2)
        turns.acceptedTurn(); assertFalse(turns.expired(10))
        turns.acceptedTurn(); assertTrue(turns.expired(10))
    }
}
