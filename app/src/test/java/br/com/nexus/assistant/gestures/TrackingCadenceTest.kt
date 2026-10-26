package br.com.nexus.assistant.gestures

import org.junit.Assert.*
import org.junit.Test

class TrackingCadenceTest {
    @Test fun framePacingAcceptsFirstFrameAndSkipsFramesUntilDue() {
        val pacing = TrackingCadence(160)
        assertTrue(pacing.shouldAnalyze(1_000))
        assertFalse(pacing.shouldAnalyze(1_001))
        assertFalse(pacing.shouldAnalyze(1_159))
        assertTrue(pacing.shouldAnalyze(1_160))
        assertFalse(pacing.shouldAnalyze(1_161))
    }

    @Test fun slowInferenceReducesRateAndNeverAccumulatesUnboundedDelay() {
        val pacing = TrackingCadence(110)
        repeat(100) { pacing.observeCost(2_000) }
        assertTrue(pacing.intervalMs in 400L..500L)
        assertTrue(pacing.shouldAnalyze(5_000))
        assertFalse(pacing.shouldAnalyze(5_100))
        assertTrue(pacing.shouldAnalyze(5_500))
        repeat(1_000) { pacing.observeCost(Long.MAX_VALUE) }
        assertTrue(pacing.intervalMs <= 500)
    }

    @Test fun stableFastInferenceRecoversToDeviceFloor() {
        val pacing = TrackingCadence(220)
        repeat(40) { pacing.observeCost(1_500) }
        val slow = pacing.intervalMs
        repeat(120) { pacing.observeCost(25) }
        assertTrue(pacing.intervalMs < slow)
        assertEquals(220L, pacing.intervalMs)
    }

    @Test fun invalidMeasurementsAndLimitsCannotCreateZeroDelay() {
        val low = TrackingCadence(-5)
        repeat(100) { low.observeCost(-100) }
        assertEquals(100L, low.intervalMs)
        val high = TrackingCadence(Long.MAX_VALUE)
        high.observeCost(0)
        assertEquals(500L, high.intervalMs)
    }

    @Test fun pointerModeHasExplicitFiftyMillisecondFloorAndStillBacksOffForSlowHardware() {
        val pointer = TrackingCadence(50, minimumIntervalMs = 50)
        assertTrue(pointer.shouldAnalyze(0))
        assertFalse(pointer.shouldAnalyze(49))
        assertTrue(pointer.shouldAnalyze(50))
        repeat(30) { pointer.observeCost(250) }
        assertTrue(pointer.intervalMs in 300L..400L)
        repeat(120) { pointer.observeCost(15) }
        assertEquals(50L, pointer.intervalMs)
        // Normal Libras/3D camera calls retain their previous pacing.
        assertEquals(160L, TrackingCadence(160).intervalMs)
    }
}
