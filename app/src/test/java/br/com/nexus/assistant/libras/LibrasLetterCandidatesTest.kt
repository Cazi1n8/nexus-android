package br.com.nexus.assistant.libras

import br.com.nexus.assistant.gestures.HandPoint
import br.com.nexus.assistant.gestures.TrackedHand
import org.junit.Assert.*
import org.junit.Test

/** Synthetic geometry verifies rules/abstention only, never linguistic or physical accuracy. */
class LibrasLetterCandidatesTest {
    private val open = listOf(
        HandPoint(.5f,.84f), HandPoint(.36f,.76f), HandPoint(.26f,.67f), HandPoint(.18f,.59f), HandPoint(.12f,.51f),
        HandPoint(.36f,.60f), HandPoint(.32f,.42f), HandPoint(.30f,.29f), HandPoint(.29f,.18f),
        HandPoint(.46f,.58f), HandPoint(.46f,.37f), HandPoint(.46f,.23f), HandPoint(.46f,.12f),
        HandPoint(.56f,.60f), HandPoint(.59f,.41f), HandPoint(.61f,.28f), HandPoint(.62f,.18f),
        HandPoint(.66f,.63f), HandPoint(.72f,.49f), HandPoint(.76f,.39f), HandPoint(.79f,.31f)
    )
    private val bent = listOf(
        HandPoint(.5f,.84f), HandPoint(.36f,.76f), HandPoint(.28f,.68f), HandPoint(.34f,.64f), HandPoint(.43f,.66f),
        HandPoint(.36f,.60f), HandPoint(.34f,.47f), HandPoint(.37f,.56f), HandPoint(.39f,.64f),
        HandPoint(.46f,.58f), HandPoint(.46f,.42f), HandPoint(.48f,.53f), HandPoint(.49f,.63f),
        HandPoint(.56f,.60f), HandPoint(.58f,.46f), HandPoint(.58f,.56f), HandPoint(.56f,.65f),
        HandPoint(.66f,.63f), HandPoint(.69f,.52f), HandPoint(.68f,.61f), HandPoint(.64f,.68f)
    )
    private fun points(letter: ManualLetterCandidate) = bent.mapIndexed { index, point ->
        when (letter) {
            ManualLetterCandidate.L -> when (index) {
                in 5..8 -> open[index]
                2 -> HandPoint(.28f, .70f)
                3 -> HandPoint(.16f, .70f)
                4 -> HandPoint(.08f, .70f)
                else -> point
            }
            ManualLetterCandidate.V -> if (index in 5..12) open[index] else point
            ManualLetterCandidate.Y -> if (index in 1..4 || index in 17..20) open[index] else point
        }
    }
    private fun observe(points: List<HandPoint>) = LibrasLetterCandidates.observe(listOf(TrackedHand(points)))

    @Test fun distinguishesTheThreeSpecifiedSyntheticShapes() {
        ManualLetterCandidate.entries.forEach { assertEquals(it, observe(points(it)).candidate) }
    }

    @Test fun mirroredHandsProduceTheSameGeometricCandidate() {
        ManualLetterCandidate.entries.forEach { letter ->
            assertEquals(letter, observe(points(letter).map { it.copy(x = 1f - it.x) }).candidate)
        }
    }

    @Test fun refusesUnsupportedOpenFistAndThumbVariants() {
        assertNull(observe(open).candidate)
        assertNull(observe(bent).candidate)
        // An index-only shape with the thumb tucked must not be labelled L.
        assertNull(observe(bent.mapIndexed { i, p -> if (i in 5..8) open[i] else p }).candidate)
        // A little-only shape is not the Y candidate without the extended thumb.
        assertNull(observe(bent.mapIndexed { i, p -> if (i in 17..20) open[i] else p }).candidate)
    }

    @Test fun refusesClippedMissingMultipleAndNonFiniteHands() {
        assertNull(LibrasLetterCandidates.observe(emptyList()).candidate)
        assertNull(LibrasLetterCandidates.observe(listOf(TrackedHand(points(ManualLetterCandidate.L)), TrackedHand(open))).candidate)
        assertNull(observe(points(ManualLetterCandidate.L).dropLast(1)).candidate)
        assertNull(observe(points(ManualLetterCandidate.L).mapIndexed { i, p -> if (i == 8) p.copy(x = .999f) else p }).candidate)
        assertNull(observe(points(ManualLetterCandidate.L).mapIndexed { i, p -> if (i == 8) p.copy(z = Float.NaN) else p }).candidate)
    }

    @Test fun handednessScoreIsNotMisrepresentedAsLetterConfidence() {
        val p = points(ManualLetterCandidate.L)
        val low = LibrasLetterCandidates.observe(listOf(TrackedHand(p, "Right", 0f)))
        val high = LibrasLetterCandidates.observe(listOf(TrackedHand(p, "Right", 1f)))
        assertEquals(low, high)
        assertTrue(high.message.contains("experimental"))
    }

    @Test fun stableCandidateRequiresSeveralFramesAndResetsOnChangeOrOcclusion() {
        val gate = StableLetterCandidate()
        for (now in 1_000L..2_500L step 500L) assertNull(gate.update(ManualLetterCandidate.L, now))
        assertEquals(ManualLetterCandidate.L, gate.update(ManualLetterCandidate.L, 3_000L))
        assertNull(gate.update(ManualLetterCandidate.V, 3_500L))
        assertNull(gate.update(null, 4_000L))
        assertNull(gate.update(ManualLetterCandidate.V, 4_500L))
        repeat(10) { assertNull(gate.update(ManualLetterCandidate.V, 4_500L)) }
        assertNull(gate.update(ManualLetterCandidate.V, 7_000L))
    }
}
