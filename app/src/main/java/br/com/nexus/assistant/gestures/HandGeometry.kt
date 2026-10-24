package br.com.nexus.assistant.gestures

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Screen-normalized coordinates. No Android dependencies so recognition rules are testable. */
data class HandPoint(val x: Float, val y: Float, val z: Float = 0f)

data class TrackedHand(
    val points: List<HandPoint>,
    /** MediaPipe handedness is retained to avoid swapping the controlling hand each frame. */
    val handedness: String = "Unknown",
    val confidence: Float = 0f
) {
    val index: HandPoint get() = points[8]
    val palmSize: Float get() = distance(points[0], points[9]).coerceAtLeast(.025f)
    val pinchRatio: Float get() = distance(points[4], points[8]) / palmSize

    fun fingerExtended(tip: Int, joint: Int): Boolean =
        distance(points[tip], points[0]) > distance(points[joint], points[0]) * 1.14f

    fun configuration(pinching: Boolean): HandConfiguration {
        if (pinching) return HandConfiguration.PINCH
        val index = fingerExtended(8, 6)
        val middle = fingerExtended(12, 10)
        val ring = fingerExtended(16, 14)
        val little = fingerExtended(20, 18)
        return when {
            index && middle && ring && little -> HandConfiguration.OPEN
            !index && !middle && !ring && !little -> HandConfiguration.FIST
            index && middle && !ring && !little -> HandConfiguration.TWO_FINGERS
            else -> HandConfiguration.OTHER
        }
    }
}

enum class HandConfiguration(val label: String) {
    OPEN("Mão aberta"), FIST("Punho fechado"), TWO_FINGERS("Dois dedos"),
    PINCH("Pinça"), OTHER("Ajustando a mão")
}

fun distance(a: HandPoint, b: HandPoint): Float = hypot(a.x - b.x, a.y - b.y)

class CursorFilter {
    private var previous: HandPoint? = null
    private var offset = HandPoint(0f, 0f)

    fun update(point: HandPoint, sensitivity: Float): HandPoint {
        val adjusted = HandPoint(
            ((point.x - .5f) * sensitivity + .5f + offset.x).coerceIn(0f, 1f),
            ((point.y - .5f) * sensitivity + .5f + offset.y).coerceIn(0f, 1f)
        )
        val old = previous
        val next = if (old == null) adjusted else HandPoint(
            old.x + .32f * (adjusted.x - old.x), old.y + .32f * (adjusted.y - old.y)
        )
        previous = next
        return next
    }

    fun center(point: HandPoint, sensitivity: Float) {
        offset = HandPoint(-((point.x - .5f) * sensitivity), -((point.y - .5f) * sensitivity))
        previous = null
    }

    fun reset() { previous = null }
}

data class PinchEvent(
    val clicked: Boolean = false,
    val doubleClicked: Boolean = false,
    val dragStarted: Boolean = false,
    val dragEnded: Boolean = false,
    val dragging: Boolean = false,
    val pinching: Boolean = false
)

/** Hysteresis prevents threshold noise. A held pinch drags; short release clicks. */
class PinchInterpreter {
    private var pressedAt: Long? = null
    private var previousClickAt: Long? = null
    private var dragging = false

    fun update(ratio: Float, now: Long, threshold: Float = .35f): PinchEvent {
        val pressed = if (pressedAt != null) ratio < threshold + .16f else ratio < threshold
        if (pressed && pressedAt == null) pressedAt = now
        if (pressed) {
            val starts = !dragging && now - (pressedAt ?: now) >= 450L
            if (starts) dragging = true
            return PinchEvent(dragStarted = starts, dragging = dragging, pinching = true)
        }
        val start = pressedAt ?: return PinchEvent()
        val wasDragging = dragging
        val isClick = !wasDragging && now - start in 40L..449L
        val double = isClick && previousClickAt?.let { now - it in 0L..380L } == true
        if (isClick) previousClickAt = if (double) null else now
        pressedAt = null
        dragging = false
        return PinchEvent(clicked = isClick, doubleClicked = double, dragEnded = wasDragging)
    }

    fun reset() { pressedAt = null; previousClickAt = null; dragging = false }
}

/** Emit once after stability; retaining a pose never repeatedly opens an app/screen. */
class StableGestureGate {
    private var candidate = HandConfiguration.OTHER
    private var since = 0L
    private var emitted = false
    private var lastEmission = Long.MIN_VALUE / 2

    fun update(gesture: HandConfiguration, now: Long): HandConfiguration? {
        if (gesture != candidate) { candidate = gesture; since = now; emitted = false }
        if (gesture == HandConfiguration.OTHER || emitted || now - since < 420L || now - lastEmission < 1100L) return null
        emitted = true
        lastEmission = now
        return gesture
    }

    fun reset() { candidate = HandConfiguration.OTHER; since = 0L; emitted = false }
}

data class CubePoint(val x: Float, val y: Float, val depth: Float)

/** Perspective projection of actual cube vertices, not a prerecorded animation. */
fun projectCube(yaw: Float, pitch: Float, scale: Float): List<CubePoint> {
    val vertices = listOf(
        floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
        floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
        floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f),
        floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f)
    )
    return vertices.map { v ->
        val x = v[0] * cos(yaw) + v[2] * sin(yaw)
        val z = -v[0] * sin(yaw) + v[2] * cos(yaw)
        val y = v[1] * cos(pitch) - z * sin(pitch)
        val depth = v[1] * sin(pitch) + z * cos(pitch)
        val perspective = 3.6f / (3.6f + depth)
        CubePoint(x * perspective * scale, y * perspective * scale, depth)
    }
}
