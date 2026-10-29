package br.com.nexus.assistant.gestures

import kotlin.math.PI

/** All meshes are generated locally; these are data, never downloaded executable plugins. */
enum class SceneShape(val label: String) {
    CUBE("Cubo"), SPHERE("Esfera"), PYRAMID("Pirâmide"), CYLINDER("Cilindro"), CONE("Cone"), TORUS("Toro")
}

data class SceneObject(
    val id: String,
    val shape: SceneShape = SceneShape.CUBE,
    val transform: CubeState = CubeState(),
    val colorArgb: Int = 0xFF31E7FA.toInt(),
)

data class SceneState(
    val objects: List<SceneObject> = listOf(SceneObject("initial-cube")),
    val selectedObjectId: String? = objects.firstOrNull()?.id,
) {
    val selectedObject: SceneObject? get() = objects.firstOrNull { it.id == selectedObjectId }

    /** Reject corrupted/imported state rather than handing non-finite values to OpenGL. */
    fun validated(): SceneState {
        require(objects.size <= MAX_OBJECTS) { "A cena suporta até 12 objetos." }
        require(objects.map { it.id }.toSet().size == objects.size) { "Há identificadores repetidos na cena." }
        require(selectedObjectId == null || objects.any { it.id == selectedObjectId }) { "A seleção não pertence à cena." }
        objects.forEach { item ->
            require(item.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Objeto com identificador inválido." }
            val state = item.transform
            require(state.center.x.isFinite() && state.center.x in .08f.. .92f &&
                state.center.y.isFinite() && state.center.y in .08f.. .92f &&
                state.yaw.isFinite() && state.pitch.isFinite() && state.pitch in -1.5f..1.5f &&
                state.scale.isFinite() && state.scale in .1f.. .65f) { "Transformação inválida na cena." }
        }
        return this
    }

    companion object { const val MAX_OBJECTS = 12 }
}

internal fun sceneAngle(value: Float): Float {
    val full = (PI * 2).toFloat()
    var angle = value % full
    if (angle > PI) angle -= full
    if (angle < -PI) angle += full
    return angle
}
