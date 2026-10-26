package br.com.nexus.assistant.device.voice

import android.content.Context
import br.com.nexus.assistant.gestures.global.GlobalMouseRuntime

/** Explicit stop controls only: no start, restart, permissions or camera ownership. */
object NexusSessionControls {
    const val ACTION_STOP_ALL = "br.com.nexus.assistant.STOP_ALL_SESSIONS"

    fun stopAll(context: Context) {
        VoiceSessionRuntime.stop(context)
        GlobalMouseRuntime.stop(context)
    }
}
