package br.com.nexus.assistant.gestures

/** Bounded adaptive pacing, shared by visible CameraX and foreground-service analyzers. */
class TrackingCadence(baseIntervalMs: Long) {
    private val base = baseIntervalMs.coerceIn(100L, 500L)
    var intervalMs: Long = base; private set
    private var lastFrameAt: Long? = null
    private var smoothedCost: Double? = null

    fun shouldAnalyze(now: Long): Boolean {
        val previous = lastFrameAt
        if (previous != null && now - previous < intervalMs) return false
        lastFrameAt = now
        return true
    }

    fun observeCost(processingMs: Long) {
        val cost = processingMs.coerceIn(0L, 10_000L).toDouble()
        val smoothed = smoothedCost?.let { it + .25 * (cost - it) } ?: cost
        smoothedCost = smoothed
        // Keep headroom for UI and cooling; do not accumulate ever-increasing delays.
        val target = (smoothed * 1.35).toLong().coerceIn(base, 500L)
        intervalMs = (intervalMs + .35 * (target - intervalMs)).toLong().coerceIn(base, 500L)
    }
}
