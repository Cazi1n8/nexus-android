package br.com.nexus.assistant.gestures.global

import android.content.Context
import android.content.Intent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session metadata only. Camera images, app contents and accessibility events are never stored. */
data class GlobalMouseState(
    val active: Boolean = false,
    val cameraReady: Boolean = false,
    val hands: Int = 0,
    val processingMs: Long = 0,
    val error: String? = null,
    val message: String? = null,
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
        if (state.value.active) context.startService(Intent(context, GlobalMouseCameraService::class.java)
            .setAction(GlobalMouseCameraService.ACTION_CALIBRATE))
    }
    internal fun update(value: GlobalMouseState) { mutableState.value = value }
    internal fun fail(message: String) { mutableState.value = GlobalMouseState(error = message) }
}
