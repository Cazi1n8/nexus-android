package br.com.nexus.assistant.device.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceSessionPhase { IDLE, PREPARING, READY, LISTENING, THINKING, SPEAKING, PAUSED, STOPPING, FAILED }

data class BackgroundVoiceState(
    val active: Boolean = false,
    val phase: VoiceSessionPhase = VoiceSessionPhase.IDLE,
    val message: String = "Sessão de voz desativada",
    val continuous: Boolean = false,
    val completedTurns: Int = 0,
)

/** In-memory status only. No voice recordings, transcripts, keys or personal logs. */
object VoiceSessionRuntime {
    private val mutableState = MutableStateFlow(BackgroundVoiceState())
    val state: StateFlow<BackgroundVoiceState> = mutableState.asStateFlow()
    @Volatile internal var stopRequested = false
        private set

    fun start(context: Context, continuous: Boolean) {
        if (state.value.active) return
        if (!SharedNexusModel.uiVisible) {
            mutableState.value = BackgroundVoiceState(phase = VoiceSessionPhase.FAILED,
                message = "Abra o NEXUS e inicie a sessão pelo botão de voz enquanto o aplicativo estiver visível.")
            return
        }
        stopRequested = false
        // Mark the in-flight foreground transition so Activity.onStop does not
        // cancel the same model before Service.onStartCommand receives it.
        mutableState.value = BackgroundVoiceState(true, VoiceSessionPhase.PREPARING,
            "Iniciando sessão de voz com notificação", continuous)
        try {
            ContextCompat.startForegroundService(context, Intent(context, BackgroundVoiceService::class.java)
                .setAction(BackgroundVoiceService.ACTION_START).putExtra(BackgroundVoiceService.EXTRA_CONTINUOUS, continuous))
        } catch (_: Exception) {
            mutableState.value = BackgroundVoiceState(phase = VoiceSessionPhase.FAILED,
                message = "O Android não permitiu iniciar a voz. Abra o NEXUS e confira microfone e notificações.")
        }
    }
    fun listen(context: Context) {
        if (state.value.active) context.startService(Intent(context, BackgroundVoiceService::class.java)
            .setAction(BackgroundVoiceService.ACTION_LISTEN))
    }
    fun interrupt(context: Context) {
        if (state.value.active) context.startService(Intent(context, BackgroundVoiceService::class.java)
            .setAction(BackgroundVoiceService.ACTION_INTERRUPT))
    }
    fun stop(context: Context) {
        stopRequested = true
        // onDestroy releases audio. Keep ownership until then, preventing a
        // second session from starting during teardown, but reject late work.
        mutableState.value = BackgroundVoiceState(active = true, phase = VoiceSessionPhase.STOPPING,
            message = "Encerrando voz e liberando microfone")
        if (!context.stopService(Intent(context, BackgroundVoiceService::class.java))) mutableState.value = BackgroundVoiceState()
    }
    internal fun update(state: BackgroundVoiceState) {
        if (stopRequested && state.active) return
        mutableState.value = state
    }
}
