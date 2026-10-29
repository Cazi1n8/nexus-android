package br.com.nexus.assistant.gestures

import kotlin.math.atan2
import kotlin.math.abs

data class CubeState(
    val center: HandPoint = HandPoint(.5f, .5f),
    val yaw: Float = .6f,
    val pitch: Float = .35f,
    val scale: Float = .24f,
    val selected: Boolean = false,
    val bimanual: Boolean = false
)

/** Independent 3D grab state. A pinch begun outside the object never teleports it. */
class CubeController(initialState: CubeState = CubeState(), initialPinches: Map<String, Boolean> = emptyMap()) {
    var state = initialState.copy(selected = false, bimanual = false); private set
    private val pinches = initialPinches.toMutableMap()
    private var selectedHand: String? = null
    private var previousPoint: HandPoint? = null
    private var pairDistance: Float? = null
    private var pairAngle = 0f
    private var pairScale = .24f
    private var pairYaw = 0f
    private var pairCenter = HandPoint(.5f, .5f)
    private var pairStart = HandPoint(.5f, .5f)
    var aspect = 1f

    fun update(frame: HandFrame, rotate: Boolean, threshold: Float): CubeState {
        val hands = frame.hands.filter { it.points.size == 21 }.take(2)
        // Handedness is retained from MediaPipe; duplicate categories cannot masquerade as two hands.
        val byId = hands.associateBy { it.handedness }
        val newlyPressed = mutableSetOf<String>()
        byId.forEach { (id, hand) ->
            val was = pinches[id] == true
            val active = hand.pinchRatio < threshold + if (was) .16f else 0f
            pinches[id] = active
            if (active && !was) newlyPressed += id
        }
        pinches.keys.retainAll(byId.keys)
        if (selectedHand != null && (byId[selectedHand] == null || pinches[selectedHand] != true)) release()
        if (selectedHand == null) {
            val first = newlyPressed.firstOrNull { id -> hit(byId.getValue(id).index) }
            if (first != null) {
                selectedHand = first
                previousPoint = byId.getValue(first).index
                state = state.copy(selected = true)
            }
        }
        val primary = selectedHand?.let(byId::get) ?: return state
        val second = byId.entries.firstOrNull { it.key != selectedHand && pinches[it.key] == true }
        // A second grab must also start on the object's visible area.
        val canPair = second != null && (state.bimanual || (second.key in newlyPressed && hit(second.value.index, 1.35f)))
        if (canPair && second != null) {
            val a = primary.index
            val b = second.value.index
            val gap = distance(a, b).coerceAtLeast(.03f)
            val midpoint = HandPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            val angle = atan2(b.y - a.y, b.x - a.x)
            if (pairDistance == null) {
                pairDistance = gap; pairAngle = angle; pairScale = state.scale; pairYaw = state.yaw
                pairStart = midpoint; pairCenter = state.center
            }
            var angleDelta = angle - pairAngle
            while (angleDelta > Math.PI) angleDelta -= (Math.PI * 2).toFloat()
            while (angleDelta < -Math.PI) angleDelta += (Math.PI * 2).toFloat()
            val targetX = (pairCenter.x + midpoint.x - pairStart.x).coerceIn(.08f, .92f)
            val targetY = (pairCenter.y + midpoint.y - pairStart.y).coerceIn(.08f, .92f)
            val targetScale = (pairScale * gap / (pairDistance ?: gap)).coerceIn(.1f, .65f)
            state = state.copy(center = HandPoint(state.center.x + .35f * (targetX - state.center.x),
                state.center.y + .35f * (targetY - state.center.y)),
                scale = state.scale + .28f * (targetScale - state.scale),
                yaw = state.yaw + .35f * (pairYaw + angleDelta - state.yaw), selected = true, bimanual = true)
            previousPoint = a
        } else {
            pairDistance = null
            val point = primary.index
            previousPoint?.let { old ->
                val dx = (point.x - old.x).coerceIn(-.12f, .12f)
                val dy = (point.y - old.y).coerceIn(-.12f, .12f)
                state = if (rotate) state.copy(yaw = state.yaw + dx * 5f, pitch = (state.pitch + dy * 5f).coerceIn(-1.5f, 1.5f), bimanual = false)
                else state.copy(center = HandPoint((state.center.x + dx * .5f).coerceIn(.08f, .92f),
                    (state.center.y + dy * .5f).coerceIn(.08f, .92f)), bimanual = false)
            }
            previousPoint = point
        }
        return state
    }
    private fun hit(point: HandPoint, padding: Float = 1f): Boolean =
        abs(point.x - state.center.x) <= state.scale * .85f * padding / aspect.coerceAtLeast(.4f) &&
            abs(point.y - state.center.y) <= state.scale * .85f * padding
    fun release(): CubeState {
        selectedHand = null; previousPoint = null; pairDistance = null
        state = state.copy(selected = false, bimanual = false)
        return state
    }
    fun reset(): CubeState { pinches.clear(); release(); state = CubeState(); return state }
    fun setManualYaw(yaw: Float): CubeState { state = state.copy(yaw = yaw); return state }
}
