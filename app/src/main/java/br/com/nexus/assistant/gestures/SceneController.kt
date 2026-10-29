package br.com.nexus.assistant.gestures

import java.util.UUID
import kotlin.math.abs

/** Own gesture state for NEXUS 3D. It never dispatches mouse/touch/accessibility events. */
class SceneController(initial: SceneState = SceneState()) {
    var state: SceneState = initial.validated(); private set
    var aspect: Float = 1f
        set(value) { field = if (value.isFinite() && value > 0f) value else 1f }
    private val pinches = mutableMapOf<String, Boolean>()
    private var grab: CubeController? = null
    private var grabbedId: String? = null

    fun update(frame: HandFrame, rotate: Boolean, threshold: Float): SceneState {
        val validHands = frame.hands.filter { hand ->
            hand.points.size == 21 && hand.points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
        }.take(2).associateBy { it.handedness }
        val current = frame.copy(hands = validHands.values.toList())
        val limit = threshold.takeIf(Float::isFinite)?.coerceIn(.2f, .55f) ?: .35f
        val previousPinches = pinches.toMap()
        val started = mutableSetOf<String>()
        validHands.forEach { (id, hand) ->
            val previous = pinches[id] == true
            val active = hand.pinchRatio < limit + if (previous) .16f else 0f
            pinches[id] = active
            if (active && !previous) started += id
        }
        pinches.keys.retainAll(validHands.keys)
        if (grab == null) {
            // Last-created object is selected first inside overlapping screen bounds.
            // A pinch that began outside all objects cannot grab by entering while held.
            val chosen = state.objects.asReversed().firstOrNull { item ->
                started.any { hand -> hit(item.transform, validHands.getValue(hand).index) }
            }
            if (chosen != null) {
                state = state.copy(selectedObjectId = chosen.id)
                grabbedId = chosen.id
                // Retain the gesture history of every visible hand. A second
                // hand held before this selection is not a newly started grab.
                grab = CubeController(chosen.transform, previousPinches)
            }
        }
        val controller = grab ?: return state
        controller.aspect = aspect
        val next = controller.update(current, rotate, limit)
        updateGrabbed(next.copy(yaw = sceneAngle(next.yaw)))
        if (!next.selected) { grab = null; grabbedId = null }
        return state
    }

    fun create(shape: SceneShape): SceneState {
        require(state.objects.size < SceneState.MAX_OBJECTS) { "A cena suporta até 12 objetos. Exclua um antes de criar outro." }
        release()
        val index = state.objects.size
        val position = if (index == 0) HandPoint(.5f, .5f) else
            HandPoint(.24f + (index % 3) * .26f, .25f + (index / 3).coerceAtMost(3) * .16f)
        val item = SceneObject(UUID.randomUUID().toString(), shape, CubeState(center = position, scale = .18f))
        state = state.copy(objects = state.objects + item, selectedObjectId = item.id)
        return state
    }

    fun select(id: String): SceneState {
        require(state.objects.any { it.id == id }) { "Esse objeto não está mais na cena." }
        release()
        state = state.copy(selectedObjectId = id)
        return state
    }

    fun duplicateSelected(): SceneState {
        require(state.objects.size < SceneState.MAX_OBJECTS) { "A cena suporta até 12 objetos." }
        val item = state.selectedObject ?: return state
        release()
        val transform = item.transform.copy(center = HandPoint((item.transform.center.x + .08f).coerceIn(.08f, .92f),
            (item.transform.center.y + .08f).coerceIn(.08f, .92f)), selected = false, bimanual = false)
        val duplicate = item.copy(id = UUID.randomUUID().toString(), transform = transform)
        state = state.copy(objects = state.objects + duplicate, selectedObjectId = duplicate.id)
        return state
    }

    fun deleteSelected(): SceneState {
        val id = state.selectedObjectId ?: return state
        release()
        val remaining = state.objects.filterNot { it.id == id }
        state = state.copy(objects = remaining, selectedObjectId = remaining.lastOrNull()?.id)
        return state
    }

    fun setSelectedColor(argb: Int): SceneState = editSelected { it.copy(colorArgb = argb or 0xFF000000.toInt()) }
    fun setManualYaw(yaw: Float): SceneState {
        require(yaw.isFinite()) { "Rotação inválida." }
        return editSelected { it.copy(transform = it.transform.copy(yaw = sceneAngle(yaw))) }
    }
    fun setManualScale(scale: Float): SceneState {
        require(scale.isFinite()) { "Escala inválida." }
        return editSelected { it.copy(transform = it.transform.copy(scale = scale.coerceIn(.1f, .65f))) }
    }
    fun release(): SceneState {
        grab?.release()?.let(::updateGrabbed)
        grab = null; grabbedId = null
        state = state.copy(objects = state.objects.map { it.copy(transform = it.transform.copy(selected = false, bimanual = false)) })
        return state
    }
    fun reset(): SceneState { grab = null; grabbedId = null; pinches.clear(); state = SceneState(); return state }
    fun restore(scene: SceneState): SceneState {
        val valid = scene.validated()
        release(); pinches.clear()
        state = valid.copy(objects = valid.objects.map { it.copy(transform = it.transform.copy(selected = false, bimanual = false)) })
        return state
    }

    private fun updateGrabbed(transform: CubeState) {
        val id = grabbedId ?: return
        state = state.copy(objects = state.objects.map { if (it.id == id) it.copy(transform = transform) else it })
    }
    private fun editSelected(change: (SceneObject) -> SceneObject): SceneState {
        release()
        state = state.copy(objects = state.objects.map { if (it.id == state.selectedObjectId) change(it) else it })
        return state
    }
    private fun hit(transform: CubeState, point: HandPoint): Boolean =
        abs(point.x - transform.center.x) <= transform.scale * .85f / aspect.coerceAtLeast(.4f) &&
            abs(point.y - transform.center.y) <= transform.scale * .85f
}
