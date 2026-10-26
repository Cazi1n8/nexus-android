package br.com.nexus.assistant.device.vision

import java.util.Locale

/** ImageNet classification is probabilistic evidence, not a description of the whole scene. */
data class ObjectCandidate(val label: String, val score: Float)

data class ImageObservation(
    val candidates: List<ObjectCandidate>,
    val width: Int,
    val height: Int,
    val elapsedMs: Long,
    val model: String = "EfficientNet-Lite0 INT8 · ImageNet",
    val lowDetail: Boolean = false,
) {
    val confidentCandidate: ObjectCandidate?
        get() = candidates.firstOrNull()?.takeIf { first ->
            !lowDetail && first.score >= .45f &&
                (candidates.getOrNull(1)?.let { first.score - it.score >= .10f } ?: true)
        }

    /** Data from a real classifier; never claims colors, location, identity or unseen detail. */
    fun evidence(): String = if (candidates.isEmpty()) {
        if (lowDetail) "Classificação local: imagem sem detalhes suficientes para avaliar objetos."
        else "Classificação local: nenhuma categoria atingiu o limiar de confiança."
    } else buildString {
        append("Classificação local de imagem, probabilística, limitada às categorias ImageNet: ")
        append(candidates.take(3).joinToString("; ") {
            "${it.label.take(100)} (pontuação ${String.format(Locale.ROOT, "%.2f", it.score)})"
        })
        append(". Essas categorias são hipóteses; não fornecem posição, cor, texto, identidade de pessoas nem compreensão da cena inteira.")
    }
}
