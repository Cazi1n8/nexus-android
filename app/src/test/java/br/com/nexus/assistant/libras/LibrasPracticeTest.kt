package br.com.nexus.assistant.libras

import br.com.nexus.assistant.gestures.HandPoint
import br.com.nexus.assistant.gestures.TrackedHand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrasPracticeTest {
    // Synthetic landmark fixtures represent geometry only, not validated human signs.
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

    private fun observation(points: List<HandPoint>, exercise: PreparationExercise = PreparationExercise.EXTENDED) =
        LibrasPracticeEvaluator.evaluate(listOf(TrackedHand(points)), exercise).observation

    @Test fun acceptsOnlyCorrespondingSyntheticGeometry() {
        assertEquals(PracticeObservation.MATCH, observation(open))
        assertEquals(PracticeObservation.MATCH, observation(bent, PreparationExercise.FLEXED))
        assertEquals(PracticeObservation.ADJUST, observation(bent))
        assertEquals(PracticeObservation.ADJUST, observation(open, PreparationExercise.FLEXED))
    }

    @Test fun freeModeIdentifiesOnlyTheSupportedGeometricConfigurations() {
        val extended = LibrasPracticeEvaluator.observe(listOf(TrackedHand(open)))
        assertEquals(PracticeObservation.MATCH, extended.observation)
        assertEquals(PreparationExercise.EXTENDED, extended.configuration)
        val flexed = LibrasPracticeEvaluator.observe(listOf(TrackedHand(bent)))
        assertEquals(PracticeObservation.MATCH, flexed.observation)
        assertEquals(PreparationExercise.FLEXED, flexed.configuration)
        val mixed = open.mapIndexed { i, point -> if (i in 9..12) bent[i] else point }
        val ambiguous = LibrasPracticeEvaluator.observe(listOf(TrackedHand(mixed)))
        assertEquals(PracticeObservation.ADJUST, ambiguous.observation)
        assertEquals(null, ambiguous.configuration)
    }

    @Test fun freeModeRefusesToLabelMissingClippedOrMultipleHands() {
        assertEquals(PracticeObservation.NO_HAND, LibrasPracticeEvaluator.observe(emptyList()).observation)
        assertEquals(PracticeObservation.MULTIPLE_HANDS,
            LibrasPracticeEvaluator.observe(listOf(TrackedHand(open), TrackedHand(bent))).observation)
        val clipped = open.mapIndexed { index, point -> if (index == 8) point.copy(x = .999f) else point }
        val assessment = LibrasPracticeEvaluator.observe(listOf(TrackedHand(clipped)))
        assertEquals(PracticeObservation.UNUSABLE, assessment.observation)
        assertEquals(null, assessment.configuration)
    }

    @Test fun mirroredHandHasSameResult() {
        assertEquals(PracticeObservation.MATCH, observation(open.map { it.copy(x = 1f - it.x) }))
        assertEquals(PracticeObservation.MATCH, observation(bent.map { it.copy(x = 1f - it.x) }, PreparationExercise.FLEXED))
    }

    @Test fun refusesMissingMultipleAndPartialHands() {
        assertEquals(PracticeObservation.NO_HAND, LibrasPracticeEvaluator.evaluate(emptyList(), PreparationExercise.EXTENDED).observation)
        assertEquals(PracticeObservation.MULTIPLE_HANDS, LibrasPracticeEvaluator.evaluate(listOf(TrackedHand(open), TrackedHand(open)), PreparationExercise.EXTENDED).observation)
        assertEquals(PracticeObservation.UNUSABLE, observation(open.dropLast(1)))
    }

    @Test fun refusesNonFiniteClippedAndTooSmallHands() {
        assertEquals(PracticeObservation.UNUSABLE, observation(open.mapIndexed { i, p -> if (i == 8) p.copy(x = Float.NaN) else p }))
        assertEquals(PracticeObservation.UNUSABLE, observation(open.mapIndexed { i, p -> if (i == 8) p.copy(x = .995f) else p }))
        assertEquals(PracticeObservation.UNUSABLE, observation(open.map { HandPoint(.5f + (it.x - .5f) * .1f, .5f + (it.y - .5f) * .1f) }))
        assertEquals(PracticeObservation.UNUSABLE, observation(open.map { it.copy(z = Float.POSITIVE_INFINITY) }))
    }

    @Test fun oneBentFingerPreventsOpenConfirmation() {
        val mixed = open.mapIndexed { i, point -> if (i in 9..12) bent[i] else point }
        assertEquals(PracticeObservation.ADJUST, observation(mixed))
    }

    @Test fun edgeOnPalmIsNotEvaluated() {
        val edgeOn = open.map { it.copy(x = .5f + (it.x - .5f) * .05f, z = it.x - .5f) }
        assertEquals(PracticeObservation.UNUSABLE, observation(edgeOn))
    }

    @Test fun stableHoldRequiresDurationAndDistinctFramesAndEmitsOnce() {
        val gate = PracticeHoldGate()
        for (now in 1_000L..2_000L step 200L) assertFalse(gate.update(PracticeObservation.MATCH, now))
        assertTrue(gate.update(PracticeObservation.MATCH, 2_200L))
        assertFalse(gate.update(PracticeObservation.MATCH, 2_400L))
    }

    @Test fun slowAdaptiveTrackingCanCompleteTheActivityInsteadOfResettingForever() {
        val gate = PracticeHoldGate()
        // TrackingCadence may use 500ms on modest devices. It must not be rejected
        // by the old 400ms continuity threshold on every valid frame.
        for (now in 1_000L..3_500L step 500L) assertFalse(gate.update(PracticeObservation.MATCH, now))
        assertTrue(gate.update(PracticeObservation.MATCH, 4_000L))
        assertFalse(gate.update(PracticeObservation.MATCH, 4_500L))
    }

    @Test fun badFramesLongGapsAndDuplicateTimestampsCannotCompleteAHold() {
        val gate = PracticeHoldGate()
        for (now in 1_000L..2_000L step 200L) assertFalse(gate.update(PracticeObservation.MATCH, now))
        assertFalse(gate.update(PracticeObservation.ADJUST, 2_100L))
        assertFalse(gate.update(PracticeObservation.MATCH, 2_200L))
        assertFalse(gate.update(PracticeObservation.MATCH, 5_000L))
        repeat(20) { assertFalse(gate.update(PracticeObservation.MATCH, 5_000L)) }
        assertFalse(gate.update(PracticeObservation.NO_HAND, 5_100L))
    }
}
