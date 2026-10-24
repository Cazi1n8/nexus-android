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
    val error: String? = null
)

object GlobalMouseRuntime {
    private val mutableState = MutableStateFlow(GlobalMouseState())
    val state: StateFlow<GlobalMouseState> = mutableState.asStateFlow()
    fun stop(context: Context) { context.stopService(Intent(context, GlobalMouseCameraService::class.java)) }
    internal fun update(value: GlobalMouseState) { mutableState.value = value }
    internal fun fail(message: String) { mutableState.value = GlobalMouseState(error = message) }
}
