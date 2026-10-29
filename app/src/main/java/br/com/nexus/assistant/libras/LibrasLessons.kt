package br.com.nexus.assistant.libras

/** Small authored lessons about study and observation; not a sign dictionary. */
enum class LibrasLessonId { LANGUAGE, PARAMETERS, SAFE_PRACTICE }

data class LibrasLesson(
    val id: LibrasLessonId,
    val title: String,
    val paragraphs: List<String>,
    val question: String,
    val answers: List<String>,
    val correctAnswer: Int,
    val explanation: String,
    val sourceTitle: String,
    val sourceUrl: String,
) {
    fun acceptsAnswer(index: Int): Boolean = index in answers.indices && index == correctAnswer
}

object LibrasLessons {
    val entries = listOf(
        LibrasLesson(
            LibrasLessonId.LANGUAGE, "1 · Libras é uma língua",
            listOf(
                "A Língua Brasileira de Sinais é uma língua visual e espacial, com organização gramatical própria. Estudar somente o alfabeto manual não equivale a aprender Libras.",
                "A datilologia usa o alfabeto manual, por exemplo, para soletrar nomes. Para aprender sinais em contexto e suas variações, estude demonstrações de pessoas sinalizantes e materiais educacionais completos.",
            ),
            "Soletrar letras demonstra domínio completo de Libras?",
            listOf("Sim: a língua é apenas um alfabeto de gestos.", "Não: letras são um recurso; Libras tem gramática e sinais em contexto."),
            1,
            "O alfabeto manual é um recurso. Uma configuração isolada não demonstra compreensão de uma língua.",
            ManualLetterCandidate.REFERENCE_TITLE, ManualLetterCandidate.REFERENCE_URL,
        ),
        LibrasLesson(
            LibrasLessonId.PARAMETERS, "2 · O que observar em um sinal",
            listOf(
                "Observe configuração da mão, orientação da palma e dos dedos, localização e movimento. Expressões faciais e corporais também podem integrar a produção e a gramática.",
                "Os exercícios de câmera do NEXUS medem somente uma parte da configuração dos dedos. Não avaliam o conjunto desses elementos, significado, fluência ou correção de um sinal completo.",
            ),
            "O NEXUS confirmou quatro dedos estendidos. O que isso comprova?",
            listOf("Somente a geometria limitada observada naquele exercício.", "Que qualquer sinal com essa configuração está linguisticamente correto."),
            0,
            "O resultado do rastreador não inclui localização linguística, movimento completo, expressões ou gramática.",
            ManualLetterCandidate.REFERENCE_TITLE, ManualLetterCandidate.REFERENCE_URL,
        ),
        LibrasLesson(
            LibrasLessonId.SAFE_PRACTICE, "3 · Praticar, observar e revisar",
            listOf(
                "Apoie o aparelho, enquadre a mão inteira e mantenha boa iluminação. Compare o esquema do exercício com a configuração observada; não force as articulações e pare se houver dor.",
                "Se a mão estiver cortada, de lado ou ocluída, o NEXUS pode não conseguir avaliar. Ausência de classificação não significa que um sinal está errado. Revise com uma pessoa sinalizante ou educador quando precisar de correção linguística.",
            ),
            "A câmera perdeu os dedos por oclusão. Qual é o feedback responsável?",
            listOf("Inventar uma letra para manter o treino funcionando.", "Informar que não consegue avaliar e pedir melhor enquadramento."),
            1,
            "Uma observação insuficiente não justifica atribuir uma letra, nota ou erro linguístico.",
            "INES · Instituto Nacional de Educação de Surdos", "https://www.gov.br/ines/pt-br",
        ),
    )

    fun get(id: LibrasLessonId): LibrasLesson = entries.first { it.id == id }

    /** Reader progress is self-reported study, deliberately separate from camera skill. */
    fun nextToStudy(studied: Set<String>): LibrasLesson? = entries.firstOrNull { it.id.name !in studied }

    fun validStudied(studied: Set<String>): Set<String> = studied.intersect(LibrasLessonId.entries.map { it.name }.toSet())
}
