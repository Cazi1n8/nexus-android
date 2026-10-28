package br.com.nexus.assistant.gestures.global

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session metadata only. Camera images, app contents and accessibility events are never stored. */
data class GlobalMouseState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val cameraReady: Boolean = false,
    val hands: Int = 0,
    val processingMs: Long = 0,
    val error: String? = null,
    val message: String? = null,
    val cameraStatus: String = "Desligada",
    val acceptedGestures: Long = 0,
    val rejectedGestures: Long = 0,
    val cancelledGestures: Long = 0,
    val frameAgeMs: Long = 0,
)

object GlobalMouseRuntime {
    private val mutableState = MutableStateFlow(GlobalMouseState())
    val state: StateFlow<GlobalMouseState> = mutableState.asStateFlow()
    @Volatile internal var stopGeneration: Long = 0L
        private set
    fun stop(context: Context) {
        // Cancel a visible panel's pending camera hand-off as well as an already
        // running service. PARAR must work even during the 350 ms bind transition.
        stopGeneration++
        if (!context.stopService(Intent(context, GlobalMouseCameraService::class.java))) {
            mutableState.value = GlobalMouseState()
        }
    }
    fun calibrate(context: Context) {
        if (state.value.active && !state.value.paused) context.startService(Intent(context, GlobalMouseCameraService::class.java)
            .setAction(GlobalMouseCameraService.ACTION_CALIBRATE))
    }
    fun pause(context: Context) {
        if (state.value.active && !state.value.paused) context.startService(Intent(context, GlobalMouseCameraService::class.java)
            .setAction(GlobalMouseCameraService.ACTION_PAUSE))
    }
    /** The visible panel checks RESUMED before calling. Notification never resumes a camera. */
    internal fun resumeFromVisiblePanel(context: Context, lifecycle: Lifecycle) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (state.value.active && state.value.paused) context.startService(Intent(context, GlobalMouseCameraService::class.java)
            .setAction(GlobalMouseCameraService.ACTION_RESUME))
    }
    internal fun gestureResult(accepted: Boolean, cancelled: Boolean = false) {
        val state = mutableState.value
        if (!state.active) return
        mutableState.value = state.copy(
            acceptedGestures = state.acceptedGestures + if (accepted && !cancelled) 1 else 0,
            rejectedGestures = state.rejectedGestures + if (!accepted && !cancelled) 1 else 0,
            cancelledGestures = state.cancelledGestures + if (cancelled) 1 else 0,
            message = if (!accepted && !cancelled) "O Android recusou um gesto. Confira a acessibilidade; algumas telas protegidas não aceitam controle." else state.message,
        )
    }
    internal fun update(value: GlobalMouseState) { mutableState.value = value }
    internal fun fail(message: String) { mutableState.value = GlobalMouseState(error = message) }
}
