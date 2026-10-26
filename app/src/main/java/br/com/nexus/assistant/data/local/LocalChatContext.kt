package br.com.nexus.assistant.data.local

import br.com.nexus.assistant.data.AiException
import br.com.nexus.assistant.data.AiMessage
import br.com.nexus.assistant.data.AssistantIdentity
import br.com.nexus.assistant.data.AssistantPromptMode
import br.com.nexus.assistant.data.NexusSettings
import java.text.Normalizer
import org.json.JSONArray
import org.json.JSONObject

/** Safe counters for diagnostics; none of these fields contain conversation text. */
data class LocalContextMetadata(
    val availableMessages: Int,
    val serializedMessages: Int,
    val estimatedPromptTokens: Int,
    val quotedExcerpts: Int,
    val transportOmittedMessages: Int,
    val tokenCountIsEstimate: Boolean = true,
)

data class LocalContextPayload(val json: String, val metadata: LocalContextMetadata)

/**
 * Conversation transport, not a four/eight-message memory window.
 * JNI applies the model's chat template, counts real tokens and prunes complete
 * oldest turns to reserve the actual requested output budget. Estimates here
 * decide whether a small, explicitly quoted recap is useful; they never pretend
 * to be the tokenizer's exact count or truncate the current request.
 */
object LocalChatContext {
    private const val MAX_NATIVE_MESSAGES = 256
    private const val MAX_NATIVE_JSON_BYTES = 1024 * 1024

    fun build(
        settings: NexusSettings,
        messages: List<AiMessage>,
        memories: List<String>,
        contextTokens: Int = 2048,
        reservedOutputTokens: Int = minOf(512, contextTokens / 4),
    ): String = prepare(settings, messages, memories, contextTokens, reservedOutputTokens).json

    fun prepare(
        settings: NexusSettings,
        messages: List<AiMessage>,
        memories: List<String>,
        contextTokens: Int = 2048,
        reservedOutputTokens: Int = minOf(512, contextTokens / 4),
    ): LocalContextPayload {
        if (messages.isEmpty() || messages.last().role != "user") throw AiException("Escreva uma mensagem para a IA local.")
        if (messages.any { it.role !in setOf("user", "assistant") }) throw AiException("O histórico contém uma mensagem inválida.")
        if (messages.any { '\u0000' in it.content }) throw AiException("A mensagem contém um caractere de controle inválido.")
        if (messages.last().imagePath != null) throw AiException(
            "O modelo de conversa local recebe texto. A imagem precisa passar por uma ferramenta visual compatível antes da conversa.",
        )
        require(contextTokens >= 512 && reservedOutputTokens in 1 until contextTokens) { "Orçamento de contexto inválido." }
        val history = messages.dropWhile { it.role != "user" }
        val selectedMemories = relevantMemories(messages.last().content, memories)
        var system = AssistantIdentity.systemPrompt(settings, selectedMemories,
            mode = AssistantPromptMode.LOCAL_TEXT, compact = contextTokens <= 2048)
        // A schema example on unrelated writing/school prompts made the tiny
        // previous model fabricate actions. This is only a tool advertisement,
        // never a canned answer or a claim that an action has happened.
        if (requestsSettingsAction(messages.last().content)) {
            system += "\nFerramenta opcional disponível: {\"nexus_action\":{\"name\":\"open_settings\",\"arguments\":{}},\"message\":\"Deseja abrir as configurações?\"}. É uma proposta; a execução exige confirmação e resultado real."
        }
        val estimatedBeforeRecap = estimateTokens(system) + history.sumOf { estimateTokens(it.content) + 8 }
        val quotes = if (estimatedBeforeRecap > contextTokens - reservedOutputTokens) {
            ExtractiveHistoryCapsule.select(history, contextTokens)
        } else emptyList()
        system += ExtractiveHistoryCapsule.render(quotes)
        val systemObject = JSONObject().put("role", "system").put("content", system)

        // The native bridge has a bounded parser. Keep complete turns within its
        // 256-message/1 MiB safety boundary, not an arbitrary dialog window.
        var first = 0
        while (history.size - first + 1 > MAX_NATIVE_MESSAGES) first = nextTurn(history, first)
        val objects = history.drop(first).map { message ->
            JSONObject().put("role", message.role).put("content", message.content +
                if (message.imagePath != null) "\n[Imagem anterior: sem acesso visual neste contexto de texto.]" else "")
        }
        val objectBytes = objects.map { it.toString().toByteArray(Charsets.UTF_8).size }
        var serializedStart = 0
        var bytes = 2L + systemObject.toString().toByteArray(Charsets.UTF_8).size +
            objectBytes.sumOf(Int::toLong) + objects.size
        while (bytes > MAX_NATIVE_JSON_BYTES && serializedStart < objects.lastIndex) {
            val next = nextTurn(history, first + serializedStart) - first
            for (index in serializedStart until next) bytes -= objectBytes[index] + 1L
            serializedStart = next
        }
        if (bytes > MAX_NATIVE_JSON_BYTES) throw AiException("A mensagem atual ultrapassa o limite de entrada da IA local. Divida o texto.")
        val payload = JSONArray().put(systemObject)
        objects.drop(serializedStart).forEach(payload::put)
        val omitted = first + serializedStart
        return LocalContextPayload(payload.toString(), LocalContextMetadata(
            availableMessages = history.size,
            serializedMessages = history.size - omitted,
            estimatedPromptTokens = estimateTokens(system) + history.drop(omitted).sumOf { estimateTokens(it.content) + 8 },
            quotedExcerpts = quotes.size,
            transportOmittedMessages = omitted,
        ))
    }

    /** Skip an old user turn and all of its assistant messages, keeping the final user intact. */
    private fun nextTurn(messages: List<AiMessage>, first: Int): Int {
        var next = first + 1
        while (next < messages.lastIndex && messages[next].role != "user") next++
        return minOf(next, messages.lastIndex)
    }

    private fun estimateTokens(text: String): Int = (text.toByteArray(Charsets.UTF_8).size + 2) / 3

    private fun requestsSettingsAction(query: String): Boolean {
        val text = normalize(query).trim().replace(Regex("^(?:ei[ ,!]+)?nexus[ ,!:.]*"), "")
        return Regex("^(?:por favor[, ]+)?(?:(?:(?:voce )?(?:pode|poderia)|quero(?: que voce)?|gostaria de) )?(?:abrir|abra|abre|acessar|acesse|entrar|entre|navegar|navegue)\\b").containsMatchIn(text) &&
            Regex("\\bconfigurac(?:ao|oes)\\b").containsMatchIn(text)
    }

    internal fun relevantMemories(query: String, memories: List<String>): List<String> {
        val terms = normalize(query).split(Regex("[^a-z0-9]+" )).filter { it.length >= 3 }.toSet()
        return memories.take(200).mapIndexed { index, memory ->
            val normalized = normalize(memory)
            Triple(memory.take(500), terms.count { it in normalized }, index)
        }.sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
            .take(2).map { it.first }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
}
