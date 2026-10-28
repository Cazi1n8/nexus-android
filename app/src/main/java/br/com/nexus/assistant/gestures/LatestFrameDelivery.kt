package br.com.nexus.assistant.gestures

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One pending value and one main-thread callback, even when the UI is busy.
 *
 * Reposting on every result would keep moving the delivery to the end of a busy
 * Handler queue. Here a new observation only replaces the pending observation.
 * The scheduler must enqueue (not invoke inline) and delivery owns the consumer.
 */
class LatestFrameDelivery<T : Any>(
    private val schedule: (Runnable) -> Unit,
    private val consume: (T) -> Unit,
) : AutoCloseable {
    private val latest = AtomicReference<T?>(null)
    private val scheduled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val drain = Runnable {
        try {
            val value = latest.getAndSet(null)
            if (!closed.get() && value != null) consume(value)
        } finally {
            scheduled.set(false)
            if (!closed.get() && latest.get() != null) enqueue()
        }
    }

    fun offer(value: T) {
        if (closed.get()) return
        latest.set(value)
        if (closed.get()) latest.set(null) else enqueue()
    }

    private fun enqueue() {
        if (scheduled.compareAndSet(false, true)) schedule(drain)
    }

    override fun close() { closed.set(true); latest.set(null) }
}

/** Camera timestamps have a device-specific clock origin. Estimate only excess
 * queue delay relative to the best observed offset, never claim absolute sensor
 * latency. Analysis-to-delivery age uses the app's own elapsed-realtime clock.
 */
class CameraFrameTimeline(private val maximumQueueDelayMs: Long = 250L) {
    data class Stamp(val analysisStartedAtMs: Long, val excessQueueDelayMs: Long)
    private var previousCameraNs: Long? = null
    private var bestOffsetNs: Long? = null

    fun begin(cameraTimestampNs: Long, elapsedRealtimeNs: Long): Stamp? {
        val previous = previousCameraNs
        if (previous != null && cameraTimestampNs <= previous) return null
        previousCameraNs = cameraTimestampNs
        val offset = elapsedRealtimeNs - cameraTimestampNs
        val best = bestOffsetNs?.coerceAtMost(offset) ?: offset
        bestOffsetNs = best
        val excess = ((offset - best).coerceAtLeast(0L) / 1_000_000L)
        if (excess > maximumQueueDelayMs) return null
        return Stamp(elapsedRealtimeNs / 1_000_000L, excess)
    }
}
