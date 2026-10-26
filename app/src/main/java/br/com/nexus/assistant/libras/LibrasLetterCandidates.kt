package br.com.nexus.assistant.libras

import br.com.nexus.assistant.gestures.HandPoint
import br.com.nexus.assistant.gestures.TrackedHand
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Three static manual-alphabet configurations, not a complete Libras classifier.
 * The source introduces manual alphabet/configuration of hands; it does not validate
 * these thresholds or certify the user's execution. Motion/orientation variants and
 * all other letters must remain unclassified rather than being guessed.
 */
enum class ManualLetterCandidate(val instruction: String) {
    L("Estenda o indicador e o polegar formando um L. Flexione os outros três dedos. Mantenha a mão inteira de frente para a câmera."),
    V("Estenda e separe indicador e médio. Flexione anelar e mínimo e recolha o polegar junto à palma."),
    Y("Estenda polegar e mínimo. Flexione indicador, médio e anelar. Mostre a mão inteira de frente.");

    companion object {
        const val REFERENCE_URL = "https://www.libras.ufsc.br/colecaoLetrasLibras/eixoFormacaoEspecifica/linguaBrasileiraDeSinaisI/assets/459/Texto_base.pdf"
        const val REFERENCE_TITLE = "UFSC · Língua Brasileira de Sinais I (Quadros, Pizzio e Rezende, 2009)"
    }
}

data class LetterCandidateAssessment(val candidate: ManualLetterCandidate?, val message: String)

/**
 * Strict geometric candidates. No trained model or calibrated probability is implied.
 * TrackedHand.confidence is a handedness score, NOT confidence in a letter, so it
 * deliberately is not used as a language/recognition score here.
 */
object LibrasLetterCandidates {
    private val fingers = listOf(intArrayOf(5, 6, 7, 8), intArrayOf(9, 10, 11, 12),
        intArrayOf(13, 14, 15, 16), intArrayOf(17, 18, 19, 20))
    private enum class Shape { EXTENDED, FLEXED, UNCERTAIN }

    fun observe(hands: List<TrackedHand>): LetterCandidateAssessment {
        val visibility = LibrasPracticeEvaluator.evaluate(hands, PreparationExercise.EXTENDED)
        if (visibility.observation in setOf(PracticeObservation.NO_HAND, PracticeObservation.MULTIPLE_HANDS, PracticeObservation.UNUSABLE)) {
            return LetterCandidateAssessment(null, visibility.message)
        }
        val p = hands.single().points
        val palm = length(p[0], p[9])
        val shapes = fingers.map { f ->
            val proximal = angle(p[f[0]], p[f[1]], p[f[2]])
            val distal = angle(p[f[1]], p[f[2]], p[f[3]])
            val tip = length(p[0], p[f[3]])
            val joint = length(p[0], p[f[1]])
            when {
                proximal >= 155f && distal >= 150f && tip > joint * 1.17f -> Shape.EXTENDED
                proximal <= 125f && tip < joint * 1.07f -> Shape.FLEXED
                else -> Shape.UNCERTAIN
            }
        }
        if (Shape.UNCERTAIN in shapes) return unknown()
        val thumbExtended = angle(p[2], p[3], p[4]) >= 155f &&
            length(p[0], p[4]) > length(p[0], p[3]) * 1.08f && length(p[4], p[5]) >= palm * .55f
        val thumbTucked = length(p[4], p[9]) < palm * .85f && length(p[4], p[5]) < palm * .85f
        val indexThumbAngle = vectorAngle(p[5], p[8], p[2], p[4])
        val indexMiddleAngle = vectorAngle(p[5], p[8], p[9], p[12])
        val littleThumbAngle = vectorAngle(p[17], p[20], p[2], p[4])
        val candidate = when {
            shapes == listOf(Shape.EXTENDED, Shape.FLEXED, Shape.FLEXED, Shape.FLEXED) && thumbExtended &&
                indexThumbAngle in 55f..120f && length(p[4], p[8]) > palm * .85f -> ManualLetterCandidate.L
            shapes == listOf(Shape.EXTENDED, Shape.EXTENDED, Shape.FLEXED, Shape.FLEXED) && thumbTucked &&
                indexMiddleAngle in 8f..45f && length(p[8], p[12]) > palm * .45f -> ManualLetterCandidate.V
            shapes == listOf(Shape.FLEXED, Shape.FLEXED, Shape.FLEXED, Shape.EXTENDED) && thumbExtended &&
                littleThumbAngle in 45f..120f && length(p[4], p[20]) > palm * .90f -> ManualLetterCandidate.Y
            else -> null
        }
        return if (candidate == null) unknown() else LetterCandidateAssessment(candidate,
            "Possível letra ${candidate.name} · hipótese geométrica experimental. Mantenha a posição; isto não avalia um sinal completo nem certifica a letra.")
    }

    private fun unknown() = LetterCandidateAssessment(null,
        "Letra não identificada. Este experimento distingue somente configurações de L, V e Y quando estão claras; não classifica as demais letras.")
    private fun length(a: HandPoint, b: HandPoint): Float = sqrt(
        (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))
    private fun angle(a: HandPoint, b: HandPoint, c: HandPoint): Float = vectorAngle(b, a, b, c)
    private fun vectorAngle(a: HandPoint, b: HandPoint, c: HandPoint, d: HandPoint): Float {
        val norm = length(a, b) * length(c, d)
        if (norm <= .000001f) return 0f
        val dot = (b.x - a.x) * (d.x - c.x) + (b.y - a.y) * (d.y - c.y) + (b.z - a.z) * (d.z - c.z)
        return Math.toDegrees(acos((dot / norm).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }
}

/** Stable label only after several real frames; no percentages or fabricated confidence. */
class StableLetterCandidate(
    private val requiredMs: Long = 1_200L,
    private val minimumFrames: Int = 5,
    private val maximumFrameGapMs: Long = 1_500L,
) {
    private var candidate: ManualLetterCandidate? = null
    private var started: Long? = null
    private var last: Long? = null
    private var frames = 0

    fun update(next: ManualLetterCandidate?, now: Long): ManualLetterCandidate? {
        if (next == null || now < 0L) { reset(); return null }
        val previous = last
        if (next != candidate || previous != null && (now <= previous || now - previous > maximumFrameGapMs)) reset()
        if (started == null) { candidate = next; started = now }
        last = now
        frames++
        return next.takeIf { frames >= minimumFrames && now - (started ?: now) >= requiredMs }
    }

    fun reset() { candidate = null; started = null; last = null; frames = 0 }
}
