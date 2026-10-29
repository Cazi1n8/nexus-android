package br.com.nexus.assistant.device.vision

/** Pure session/request gate. Late camera/import callbacks cannot start another upload. */
internal class VisionConversationGate(private val minimumIntervalMs: Long = 10_000L) {
    init { require(minimumIntervalMs >= 10_000L) }

    data class Ticket internal constructor(val session: Long, val request: Long)

    private var session = 0L
    private var request = 0L
    private var pending: Ticket? = null
    private var lastCaptureMs: Long? = null
    var active = false; private set

    fun start() {
        session++
        active = true
        pending = null
        lastCaptureMs = null
    }

    fun stop() {
        session++
        active = false
        pending = null
        lastCaptureMs = null
    }

    fun begin(nowMs: Long, periodic: Boolean): Ticket? {
        if (!active || pending != null) return null
        val previous = lastCaptureMs
        if (periodic && previous != null && (nowMs < previous || nowMs - previous < minimumIntervalMs)) return null
        if (nowMs < 0) return null
        if (periodic || nowMs >= (previous ?: 0)) lastCaptureMs = nowMs
        return Ticket(session, ++request).also { pending = it }
    }

    fun accepts(ticket: Ticket): Boolean = active && ticket.session == session && pending == ticket

    fun finish(ticket: Ticket): Boolean {
        if (!accepts(ticket)) return false
        pending = null
        return true
    }
}
