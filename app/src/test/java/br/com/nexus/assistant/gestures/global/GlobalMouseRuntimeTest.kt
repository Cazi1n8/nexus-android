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
