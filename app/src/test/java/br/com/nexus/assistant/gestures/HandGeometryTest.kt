package br.com.nexus.assistant.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandGeometryTest {
    @Test fun `short release clicks and hysteresis ignores threshold noise`() {
        val detector = PinchInterpreter()
        assertTrue(detector.update(.3f, 0L).pinching)
        assertTrue(detector.update(.43f, 90L).pinching)
        val released = detector.update(.6f, 160L)
        assertTrue(released.clicked)
        assertFalse(released.doubleClicked)
        assertFalse(detector.update(.6f, 180L).clicked)
    }

    @Test fun `two rapid pinches become double click`() {
        val detector = PinchInterpreter()
        detector.update(.2f, 0L)
        detector.update(.8f, 100L)
        detector.update(.2f, 220L)
        assertTrue(detector.update(.8f, 310L).doubleClicked)
    }

    @Test fun `held pinch drags and never clicks on release`() {
        val detector = PinchInterpreter()
        detector.update(.2f, 0L)
        assertTrue(detector.update(.2f, 450L).dragStarted)
        assertFalse(detector.update(.2f, 500L).dragStarted)
        val release = detector.update(.8f, 700L)
        assertTrue(release.dragEnded)
        assertFalse(release.clicked)
    }

    @Test fun `hand loss resets pinch without creating click`() {
        val detector = PinchInterpreter()
        detector.update(.2f, 0L)
        detector.reset()
        assertFalse(detector.update(.8f, 200L).clicked)
    }

    @Test fun `a held gesture emits once and needs another pose`() {
        val gate = StableGestureGate()
        assertNull(gate.update(HandConfiguration.OPEN, 0L))
        assertEquals(HandConfiguration.OPEN, gate.update(HandConfiguration.OPEN, 420L))
        assertNull(gate.update(HandConfiguration.OPEN, 5_000L))
        gate.update(HandConfiguration.OTHER, 5_100L)
        gate.update(HandConfiguration.OPEN, 5_200L)
        assertEquals(HandConfiguration.OPEN, gate.update(HandConfiguration.OPEN, 5_620L))
    }

    @Test fun `calibration centers point and cursor stays in bounds`() {
        val filter = CursorFilter()
        val point = HandPoint(.85f, .15f)
        filter.center(point, 1.3f)
        val center = filter.update(point, 1.3f)
        assertEquals(.5f, center.x, .0001f)
        assertEquals(.5f, center.y, .0001f)
        repeat(30) {
            val result = filter.update(HandPoint(5f, -2f), 2.3f)
            assertTrue(result.x in 0f..1f && result.y in 0f..1f)
        }
    }

    @Test fun `cube has eight finite projected vertices and reacts to rotation`() {
        val initial = projectCube(0f, 0f, .15f)
        val rotated = projectCube(.7f, .4f, .15f)
        assertEquals(8, initial.size)
        assertTrue(rotated.all { it.x.isFinite() && it.y.isFinite() && it.depth.isFinite() })
        assertTrue(initial != rotated)
    }

    @Test fun `cursor smoothing uses elapsed time so slow camera frames do not add half second lag`() {
        fun response(step: Long): Float {
            val filter = CursorFilter()
            filter.update(HandPoint(0f, 0f), 1f, 0)
            var x = 0f
            var time = step
            while (time <= 160) {
                x = filter.update(HandPoint(1f, 1f), 1f, time).x
                time += step
            }
            return x
        }
        // An identical 160 ms movement settles equally at 6.25 or 50 frames/s.
        val lowFps = response(160)
        val highFps = response(20)
        assertEquals(highFps, lowFps, .0001f)
        assertTrue(lowFps > .84f)
    }

    @Test fun `recalibration clears timestamp history without discarding the new center`() {
        val filter = CursorFilter()
        filter.update(HandPoint(.1f, .1f), 1.3f, 0)
        filter.update(HandPoint(.9f, .9f), 1.3f, 500)
        val center = HandPoint(.85f, .15f)
        filter.center(center, 1.3f)
        val result = filter.update(center, 1.3f, 550)
        assertEquals(.5f, result.x, .0001f)
        assertEquals(.5f, result.y, .0001f)
    }
}
