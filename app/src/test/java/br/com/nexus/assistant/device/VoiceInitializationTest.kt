package br.com.nexus.assistant.device

import org.junit.Assert.*
import org.junit.Test

class VoiceInitializationTest {
    @Test fun failedInitializationCannotBeRevivedByALateReadyCallback() {
        val lifecycle = VoiceInitialization()
        val first = lifecycle.begin()
        assertTrue(lifecycle.fail(first))
        assertEquals(VoiceInitialization.Phase.FAILED, lifecycle.phase)
        assertFalse(lifecycle.ready(first))
        assertFalse(lifecycle.pending(first))
    }

    @Test fun explicitRetryRejectsThePreviousEngineCallback() {
        val lifecycle = VoiceInitialization()
        val oldEngine = lifecycle.begin()
        lifecycle.fail(oldEngine)
        val retry = lifecycle.begin()
        assertFalse(lifecycle.ready(oldEngine))
        assertTrue(lifecycle.pending(retry))
        assertTrue(lifecycle.ready(retry))
        assertEquals(VoiceInitialization.Phase.READY, lifecycle.phase)
        assertFalse(lifecycle.fail(retry))
    }

    @Test fun closingDiscardsInitializationAndCannotStartAnotherEngine() {
        val lifecycle = VoiceInitialization()
        val ticket = lifecycle.begin()
        lifecycle.close()
        assertFalse(lifecycle.ready(ticket))
        assertFalse(lifecycle.fail(ticket))
        assertFalse(lifecycle.pending(ticket))
        assertEquals(VoiceInitialization.Phase.CLOSED, lifecycle.phase)
        try {
            lifecycle.begin()
            fail("A closed controller cannot create another TTS engine")
        } catch (_: IllegalStateException) { }
    }

    @Test fun progressWatchdogHasGenerousFiniteAllowanceForSlowVoicesAndInvalidRates() {
        assertEquals(30_000L, speechCompletionTimeoutMs(0, 1f))
        assertEquals(speechCompletionTimeoutMs(600, 1f), speechCompletionTimeoutMs(600, Float.NaN))
        assertEquals(speechCompletionTimeoutMs(600, 1f), speechCompletionTimeoutMs(600, Float.POSITIVE_INFINITY))
        assertTrue(speechCompletionTimeoutMs(600, .5f) > speechCompletionTimeoutMs(600, 2f))
        assertTrue(speechCompletionTimeoutMs(600, .5f) < 600_000L)
        assertEquals(600_000L, speechCompletionTimeoutMs(Int.MAX_VALUE, .5f))
        assertEquals(30_000L, speechCompletionTimeoutMs(-5, 1f))
    }
}
