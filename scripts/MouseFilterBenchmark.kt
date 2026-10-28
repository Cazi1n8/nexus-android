import br.com.nexus.assistant.gestures.CursorFilter
import br.com.nexus.assistant.gestures.HandPoint
import br.com.nexus.assistant.gestures.LatestFrameDelivery
import kotlin.math.exp
import kotlin.math.sqrt

/** Exact85ms exponential baseline from the previous CursorFilter. No LLM data. */
private class LegacyFilter {
    private var value = .1f
    private var at = 0L
    fun update(point: Float, now: Long): Float {
        val alpha = (1.0 - exp(-(now - at).coerceIn(1L, 500L) / 85.0)).toFloat()
        value += alpha * (point - value)
        at = now
        return value
    }
}

fun main() {
    fun stepMs(step: Long, modern: Boolean): Long {
        val next = CursorFilter().apply { update(HandPoint(.1f, .5f), 1f, 0L) }
        val old = LegacyFilter()
        for (t in step..1000L step step) {
            val x = if (modern) next.update(HandPoint(.9f, .5f), 1f, t).x else old.update(.9f, t)
            if (x >= .82f) return t
        }
        return 1000L
    }
    fun noiseRms(modern: Boolean): Double {
        val next = CursorFilter().apply { update(HandPoint(.1f, .5f), 1f, 0L) }
        val old = LegacyFilter()
        var square = 0.0
        for (index in 1..1000) {
            val raw = .1f + if (index % 2 == 0) .004f else -.004f
            val x = if (modern) next.update(HandPoint(raw, .5f), 1f, index * 50L).x else old.update(raw, index * 50L)
            if (index > 100) square += (x - .1f) * (x - .1f)
        }
        return sqrt(square / 900.0)
    }
    var posts = 0
    val latest = LatestFrameDelivery<Int>({ posts++ }) { }
    repeat(10_000) { latest.offer(it) }
    println("""{"kind":"synthetic_production_kotlin_filter","not_android_hardware":true,"legacy":"85ms exponential from alpha.3-core","modern":"One Euro min1.3Hz beta2 derivative1Hz","step_90percent_ms":[${listOf(10L,50L,100L).joinToString(",") { spacing -> "{\"frame_spacing_ms\":$spacing,\"before\":${stepMs(spacing,false)},\"after\":${stepMs(spacing,true)}}" }}],"stationary_rms_normalized":{"before":${noiseRms(false)},"after":${noiseRms(true)}},"main_thread_posts_for_10000_results_while_busy":{"before":10000,"after":$posts}}""")
}
