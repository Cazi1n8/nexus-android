package br.com.nexus.assistant.device.vision

import org.junit.Assert.*
import org.junit.Test

class VisualObservationTest {
    @Test fun uncertainOrLowDetailFramesDoNotClaimConfidentIdentification() {
        assertNull(ImageObservation(emptyList(), 1, 1, 0).confidentCandidate)
        assertNull(ImageObservation(listOf(ObjectCandidate("cat", .44f)), 1, 1, 0).confidentCandidate)
        assertNull(ImageObservation(listOf(ObjectCandidate("cat", .60f), ObjectCandidate("dog", .55f)), 1, 1, 0).confidentCandidate)
        assertNull(ImageObservation(listOf(ObjectCandidate("cat", .99f)), 1, 1, 0, lowDetail = true).confidentCandidate)
        assertEquals("cat", ImageObservation(listOf(ObjectCandidate("cat", .90f), ObjectCandidate("dog", .30f)), 1, 1, 0).confidentCandidate?.label)
    }
    @Test fun evidenceIncludesRealScoresAndClassificationLimits() {
        val evidence = ImageObservation(listOf(ObjectCandidate("cheeseburger", .9f)), 1, 1, 0).evidence()
        assertTrue(evidence.contains("cheeseburger")); assertTrue(evidence.contains("0.90"))
        assertTrue(evidence.contains("hipóteses")); assertTrue(evidence.contains("não fornecem posição"))
    }
}
