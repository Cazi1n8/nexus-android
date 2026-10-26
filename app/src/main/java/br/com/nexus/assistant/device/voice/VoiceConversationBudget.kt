package br.com.nexus.assistant.device.voice

/** Bounded turn-taking, not an always-on wake-word detector. Uses monotonic time. */
internal class VoiceConversationBudget(
    private val startedAt: Long,
    val durationMs: Long = 15 * 60_000L,
    val maxTurns: Int = 30,
) {
    private var turns = 0
    private var silenceRetries = 0
    init { require(durationMs > 0 && maxTurns > 0) }
    fun expired(now: Long): Boolean = now - startedAt >= durationMs || turns >= maxTurns
    fun acceptedTurn() { turns++; silenceRetries = 0 }
    fun manualListen() { silenceRetries = 0 }
    /** At most two silence retries per explicit/listening turn, with increasing pauses. */
    fun nextSilenceRetry(now: Long, continuous: Boolean, silence: Boolean): Long? {
        if (!continuous || !silence || expired(now) || silenceRetries >= 2) return null
        silenceRetries++
        return silenceRetries * 1_500L
    }
}
