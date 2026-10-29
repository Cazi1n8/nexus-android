package br.com.nexus.assistant.gestures.global

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalMouseRuntimeTest {
    @After fun releaseSessionState() = GlobalMouseRuntime.update(GlobalMouseState())

    @Test fun lateGestureCallbacksCannotCreateAStoppedSession() {
        GlobalMouseRuntime.update(GlobalMouseState())
        GlobalMouseRuntime.gestureResult(accepted = true)
        GlobalMouseRuntime.gestureResult(accepted = false)
        assertEquals(GlobalMouseState(), GlobalMouseRuntime.state.value)
    }

    @Test fun stopImmediatelyBlocksInputAndRetainsTheFailureForDiagnostics() {
        GlobalMouseRuntime.update(GlobalMouseState(active = true, cameraReady = true, acceptedGestures = 4,
            error = "A sobreposição foi removida pelo Android."))
        val generation = GlobalMouseRuntime.stopGeneration
        GlobalMouseRuntime.markStopping()
        assertEquals(generation + 1, GlobalMouseRuntime.stopGeneration)
        assertEquals(GlobalMouseState(error = "A sobreposição foi removida pelo Android."), GlobalMouseRuntime.state.value)
        GlobalMouseRuntime.updateFromFrame { it.copy(active = true, cameraReady = true, hands = 1) }
        GlobalMouseRuntime.gestureResult(accepted = true)
        assertEquals(GlobalMouseState(error = "A sobreposição foi removida pelo Android."), GlobalMouseRuntime.state.value)
    }

    @Test fun delayedFramesCannotClearPauseOrMakeStoppedMetadataLookActive() {
        val paused = GlobalMouseState(active = true, paused = true, cameraStatus = "Pausada; câmera liberada")
        GlobalMouseRuntime.update(paused)
        GlobalMouseRuntime.updateFromFrame { it.copy(paused = false, cameraReady = true, hands = 1) }
        assertEquals(paused, GlobalMouseRuntime.state.value)
        GlobalMouseRuntime.update(GlobalMouseState(active = true))
        GlobalMouseRuntime.updateFromFrame { it.copy(cameraReady = true, hands = 1) }
        assertTrue(GlobalMouseRuntime.state.value.cameraReady)
        assertEquals(1, GlobalMouseRuntime.state.value.hands)
    }

    @Test fun gestureDiagnosticsKeepPauseAndCameraStatus() {
        GlobalMouseRuntime.update(GlobalMouseState(active = true, paused = true, cameraStatus = "Pausada; câmera liberada"))
        GlobalMouseRuntime.gestureResult(accepted = true)
        GlobalMouseRuntime.gestureResult(accepted = false)
        GlobalMouseRuntime.gestureResult(accepted = false, cancelled = true)
        val state = GlobalMouseRuntime.state.value
        assertTrue(state.active)
        assertTrue(state.paused)
        assertEquals("Pausada; câmera liberada", state.cameraStatus)
        assertEquals(1L, state.acceptedGestures)
        assertEquals(1L, state.rejectedGestures)
        assertEquals(1L, state.cancelledGestures)
        assertTrue(state.message!!.contains("Android recusou"))
    }
}
