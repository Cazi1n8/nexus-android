package br.com.nexus.assistant.device.vision

import org.junit.Assert.*
import org.junit.Test

class VisionConversationGateTest {
    @Test fun requiresExplicitSessionAndSerializesRequests() {
        val gate = VisionConversationGate()
        assertNull(gate.begin(1, periodic = false))
        gate.start()
        val first = gate.begin(1, periodic = false)!!
        assertTrue(gate.accepts(first))
        assertNull(gate.begin(50_000, periodic = false))
        assertTrue(gate.finish(first))
        assertNotNull(gate.begin(50_000, periodic = false))
    }

    @Test fun samplesAtMostEveryTenSecondsAndNeverReplaysACompletedTicket() {
        val gate = VisionConversationGate()
        gate.start()
        val first = gate.begin(20_000, periodic = true)!!
        assertTrue(gate.finish(first))
        assertFalse(gate.accepts(first))
        assertFalse(gate.finish(first))
        assertNull(gate.begin(19_999, periodic = true))
        assertNull(gate.begin(29_999, periodic = true))
        assertNotNull(gate.begin(30_000, periodic = true))
    }

    @Test fun stopAndRestartInvalidateLateCameraAndImportCallbacks() {
        val gate = VisionConversationGate()
        gate.start()
        val stale = gate.begin(1, periodic = false)!!
        gate.stop()
        assertFalse(gate.accepts(stale))
        gate.start()
        val current = gate.begin(2, periodic = false)!!
        assertFalse(gate.accepts(stale))
        assertFalse(gate.finish(stale))
        assertTrue(gate.accepts(current))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesUnboundedFrameFrequency() {
        VisionConversationGate(100)
    }
}
