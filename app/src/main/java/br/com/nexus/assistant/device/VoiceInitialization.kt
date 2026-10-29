package br.com.nexus.assistant.device

/** One engine initialization at a time. Late callbacks cannot revive a failed or closed session. */
internal class VoiceInitialization {
    enum class Phase { INITIALIZING, READY, FAILED, CLOSED }
    var phase: Phase = Phase.FAILED; private set
    private var generation = 0L

    fun begin(): Long {
        check(phase != Phase.CLOSED)
        generation++
        phase = Phase.INITIALIZING
        return generation
    }

    fun pending(ticket: Long): Boolean = generation == ticket && phase == Phase.INITIALIZING
    fun ready(ticket: Long): Boolean {
        if (!pending(ticket)) return false
        phase = Phase.READY
        return true
    }
    fun fail(ticket: Long): Boolean {
        if (!pending(ticket)) return false
        phase = Phase.FAILED
        return true
    }
    fun close() { generation++; phase = Phase.CLOSED }
}

/** A bounded watchdog, not an estimate shown as the actual speech duration.
 * Generous allowance for slow/local/network engines; the real engine callback
 * finishes speech. Missing callbacks must not block an authorized voice session forever.
 */
internal fun speechCompletionTimeoutMs(characters: Int, rate: Float): Long {
    val safeRate = if (rate.isFinite()) rate.coerceIn(.5f, 2f) else 1f
    return (30_000L + (characters.coerceAtLeast(0) * 240.0 / safeRate).toLong()).coerceAtMost(600_000L)
}
