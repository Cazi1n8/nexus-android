package br.com.nexus.assistant.libras

import br.com.nexus.assistant.gestures.HandPoint
import br.com.nexus.assistant.gestures.TrackedHand
import kotlin.math.acos
import kotlin.math.sqrt

/** Preparation only: these exercises do not represent or validate complete Libras signs. */
enum class PreparationExercise(val title: String, val instruction: String) {
    EXTENDED("Quatro dedos estendidos", "Mostre uma mão de frente. Estenda indicador, médio, anelar e mínimo, sem forçar. O polegar não é avaliado."),
    FLEXED("Quatro dedos flexionados", "Mostre uma mão de frente. Flexione indicador, médio, anelar e mínimo em direção à palma. O polegar não é avaliado.")
}

enum class PracticeObservation { NO_HAND, MULTIPLE_HANDS, UNUSABLE, ADJUST, MATCH }

data class PracticeAssessment(val observation: PracticeObservation, val message: String)

/**
 * Conservative 3D landmark geometry, not a trained Libras classifier. It deliberately
 * refuses partial/edge-on/undersized hands and ambiguous, partly bent fingers.
 * No probability or linguistic accuracy score is inferred from these measurements.
 */
object LibrasPracticeEvaluator {
    private val fingers = listOf(intArrayOf(5, 6, 7, 8), intArrayOf(9, 10, 11, 12),
        intArrayOf(13, 14, 15, 16), intArrayOf(17, 18, 19, 20))

    fun evaluate(hands: List<TrackedHand>, exercise: PreparationExercise): PracticeAssessment {
        if (hands.isEmpty()) return PracticeAssessment(PracticeObservation.NO_HAND, "Nenhuma mão detectada. Mostre uma mão inteira e bem iluminada.")
        if (hands.size != 1) return PracticeAssessment(PracticeObservation.MULTIPLE_HANDS, "Use somente uma mão neste exercício.")
        val p = hands.single().points
        if (p.size != 21 || p.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() || it.x !in 0.025f..0.975f || it.y !in 0.025f..0.975f }) {
            return PracticeAssessment(PracticeObservation.UNUSABLE, "Centralize a mão inteira, incluindo o punho, sem cortar os dedos.")
        }
        val palm = length(p[0], p[9])
        val width = length(p[5], p[17])
        // z has MediaPipe's image-width scale; this only rejects strongly edge-on palms.
        val planarWidth = sqrt(squared(p[5].x - p[17].x) + squared(p[5].y - p[17].y))
        if (palm !in 0.08f..0.50f || width < palm * .35f || planarWidth < width * .55f || fingers.any { f ->
                length(p[f[0]], p[f[1]]) < palm * .07f || length(p[f[1]], p[f[2]]) < palm * .035f || length(p[f[2]], p[f[3]]) < palm * .035f
            }) {
            return PracticeAssessment(PracticeObservation.UNUSABLE, "Aproxime a mão e vire a palma para a câmera. Se houver oclusão, não consigo avaliar.")
        }
        val matches = fingers.all { f ->
            val proximalAngle = angle(p[f[0]], p[f[1]], p[f[2]])
            val distalAngle = angle(p[f[1]], p[f[2]], p[f[3]])
            val tipFromWrist = length(p[0], p[f[3]])
            val jointFromWrist = length(p[0], p[f[1]])
            when (exercise) {
                PreparationExercise.EXTENDED -> proximalAngle >= 155f && distalAngle >= 150f && tipFromWrist > jointFromWrist * 1.17f
                PreparationExercise.FLEXED -> proximalAngle <= 125f && tipFromWrist < jointFromWrist * 1.07f
            }
        }
        return if (matches) PracticeAssessment(PracticeObservation.MATCH, "A geometria dos quatro dedos corresponde ao exercício. Mantenha a posição sem forçar.")
        else PracticeAssessment(PracticeObservation.ADJUST, "A posição ainda está ambígua ou diferente da referência. Ajuste os quatro dedos e mantenha a palma de frente.")
    }

    private fun squared(v: Float) = v * v
    private fun length(a: HandPoint, b: HandPoint): Float = sqrt(squared(a.x - b.x) + squared(a.y - b.y) + squared(a.z - b.z))
    private fun angle(a: HandPoint, b: HandPoint, c: HandPoint): Float {
        val u = floatArrayOf(a.x - b.x, a.y - b.y, a.z - b.z)
        val v = floatArrayOf(c.x - b.x, c.y - b.y, c.z - b.z)
        val norm = length(a, b) * length(c, b)
        if (norm <= .000001f) return 0f
        return Math.toDegrees(acos(((u[0] * v[0] + u[1] * v[1] + u[2] * v[2]) / norm).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }
}

/** Require continuous evidence and multiple distinct frames; a delayed single frame cannot pass. */
class PracticeHoldGate(private val requiredMs: Long = 1_200L, private val minimumFrames: Int = 7) {
    private var startedAt: Long? = null
    private var lastAt: Long? = null
    private var frames = 0
    private var emitted = false

    fun update(observation: PracticeObservation, now: Long): Boolean {
        if (observation != PracticeObservation.MATCH || now < 0L) { reset(); return false }
        val last = lastAt
        if (last != null && (now <= last || now - last > 400L)) { reset() }
        if (startedAt == null) startedAt = now
        lastAt = now
        frames++
        if (!emitted && frames >= minimumFrames && now - (startedAt ?: now) >= requiredMs) {
            emitted = true
            return true
        }
        return false
    }

    fun reset() { startedAt = null; lastAt = null; frames = 0; emitted = false }
}
