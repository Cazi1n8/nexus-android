package br.com.nexus.assistant.data

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A real incremental provider. A completed JSON response is never called a stream. */
interface StreamingAiProvider : AiProvider {
    suspend fun stream(
        settings: NexusSettings,
        token: String,
        messages: List<AiMessage>,
        memories: List<String>,
        onDelta: suspend (String) -> Unit,
    ): AiStreamResult
}

data class AiSource(val title: String, val url: String, val snippet: String)

data class AiStreamResult(
    /** Final validated answer. A tool proposal is validated JSON, not the temporary text delta. */
    val text: String,
    val finishReason: String,
    val firstTokenMs: Long?,
    val totalMs: Long,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val sources: List<AiSource> = emptyList(),
) {
    val truncated: Boolean get() = finishReason == "length"
}

/** Stateless frame reading and stateful content assembly are kept separate for testing. */
internal class SseEventReader(body: ResponseBody) {
    private val source = body.source()
    private var receivedBytes = 0L

    /** Bounded, strict UTF-8; malformed byte sequences are errors, not replacement text. */
    private fun readLine(): String? {
        val newline = source.indexOf('\n'.code.toByte(), 0, MAX_LINE_BYTES + 1)
        val bytes = if (newline >= 0) {
            source.readByteArray(newline + 1)
        } else {
            if (source.buffer.size > MAX_LINE_BYTES) throw AiException("Um evento da IA excedeu o limite permitido.")
            // indexOf reads until EOF or its bound. Less than that bound with
            // no newline is therefore the final partial line, not another wait.
            if (source.buffer.size == 0L) return null
            source.readByteArray()
        }
        receivedBytes += bytes.size
        if (receivedBytes > MAX_STREAM_BYTES) throw AiException("A resposta da IA excedeu o limite permitido.")
        val text = try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: Exception) {
            throw AiException("A IA enviou texto com codificação inválida.", error)
        }
        return text.removeSuffix("\n").removeSuffix("\r")
    }

    fun nextData(): String? {
        val data = StringBuilder()
        while (true) {
            val line = readLine()
            if (line == null || line.isEmpty()) {
                if (data.isNotEmpty()) return data.toString().removeSuffix("\n")
                if (line == null) return null
                continue
            }
            if (line.startsWith("data:")) {
                data.append(line.removePrefix("data:").removePrefix(" ")).append('\n')
                if (data.length > MAX_LINE_BYTES) throw AiException("Um evento da IA excedeu o limite permitido.")
            }
            // SSE comments, event names, ids and retry fields are metadata, never answer text.
        }
    }

    companion object {
        private const val MAX_LINE_BYTES = 64L * 1024
        private const val MAX_STREAM_BYTES = 1024L * 1024
    }
}

/** Appends each delta exactly once and requires an explicit completion marker. */
internal class OpenAiStreamDecoder {
    private val text = StringBuilder()
    private val toolName = StringBuilder()
    private val toolArguments = StringBuilder()
    private var sawTool = false
    var finishReason: String? = null; private set
    var done = false; private set
    var inputTokens: Int? = null; private set
    var outputTokens: Int? = null; private set
    var sources: List<AiSource> = emptyList(); private set

    fun accept(data: String): String {
        if (done) throw AiException("A IA enviou conteúdo após encerrar a resposta.")
        if (data == "[DONE]") { done = true; return "" }
        try {
            val event = JSONObject(data)
            if (event.has("error")) throw AiException("O servidor interrompeu a geração. Tente novamente.")
            event.optJSONArray("nexus_sources")?.let { entries ->
                if (entries.length() > 5) throw AiException("O servidor enviou fontes demais.")
                sources = (0 until entries.length()).map { index ->
                    val entry = entries.optJSONObject(index) ?: throw AiException("Fonte de pesquisa inválida.")
                    val title = entry.opt("title") as? String ?: throw AiException("Fonte de pesquisa inválida.")
                    val rawUrl = entry.opt("url") as? String ?: throw AiException("Fonte de pesquisa inválida.")
                    val snippet = entry.opt("snippet") as? String ?: throw AiException("Fonte de pesquisa inválida.")
                    val url = rawUrl.toHttpUrlOrNull() ?: throw AiException("URL de fonte inválida.")
                    if (rawUrl.length > 2_000 || title.length > 250 || snippet.length > 1_500 ||
                        url.username.isNotEmpty() || url.password.isNotEmpty() || rawUrl.any { it.isISOControl() }) {
                        throw AiException("Fonte de pesquisa excede os limites permitidos.")
                    }
                    AiSource(title, url.toString(), snippet)
                }
            }
            event.optJSONObject("usage")?.let { usage ->
                inputTokens = boundedUsage(usage.opt("prompt_tokens")) ?: inputTokens
                outputTokens = boundedUsage(usage.opt("completion_tokens")) ?: outputTokens
            }
            val choices = event.optJSONArray("choices") ?: throw AiException("Evento de IA incompatível.")
            if (choices.length() == 0) return "" // A usage-only final event is valid.
            val choice = (0 until choices.length()).mapNotNull(choices::optJSONObject)
                .firstOrNull { it.optInt("index", 0) == 0 } ?: return ""
            val delta = choice.optJSONObject("delta") ?: throw AiException("Evento de IA incompatível.")
            val fragment = when (val content = delta.opt("content")) {
                is String -> content
                is JSONArray -> (0 until content.length()).mapNotNull { index ->
                    content.optJSONObject(index)?.takeIf { it.optString("type") == "text" }?.opt("text") as? String
                }.joinToString("")
                else -> (delta.opt("refusal") as? String).orEmpty()
            }
            if (fragment.isNotEmpty()) {
                if (finishReason != null) throw AiException("A IA enviou texto depois da condição de parada.")
                text.append(fragment)
                if (text.length > 24_000) throw AiException("A resposta foi longa demais. Solicite uma resposta menor.")
            }
            delta.optJSONArray("tool_calls")?.let { calls ->
                if (finishReason != null) throw AiException("A IA enviou uma ação depois da condição de parada.")
                if (calls.length() > 1) throw AiException("A IA propôs várias ações. Peça uma ação por vez.")
                for (index in 0 until calls.length()) {
                    val call = calls.getJSONObject(index)
                    if (call.optInt("index", 0) != 0 || (call.has("type") && call.optString("type") != "function")) {
                        throw AiException("A IA propôs uma ferramenta incompatível.")
                    }
                    sawTool = true
                    call.optJSONObject("function")?.let { function ->
                        (function.opt("name") as? String)?.let(toolName::append)
                        (function.opt("arguments") as? String)?.let(toolArguments::append)
                    }
                    if (toolName.length > 100 || toolArguments.length > 2_048) {
                        throw AiException("A proposta de ação excedeu o limite permitido.")
                    }
                }
            }
            (choice.opt("finish_reason") as? String)?.let { reason ->
                if (reason !in setOf("stop", "length", "tool_calls")) throw AiException("O provedor interrompeu a resposta.")
                finishReason = reason
            }
            return fragment
        } catch (error: JSONException) {
            throw AiException("A resposta incremental do servidor é incompatível.", error)
        }
    }

    fun finish(): String {
        if (!done || finishReason == null) throw AiException("A conexão terminou antes da resposta completa. Tente novamente.")
        if (sawTool) {
            if (finishReason == "length") throw AiException("A proposta da IA foi interrompida antes de terminar.")
            val arguments = try { JSONObject(toolArguments.toString()) } catch (error: JSONException) {
                throw AiException("A proposta da IA foi interrompida ou é inválida.", error)
            }
            return AiCompletion.toolResponse(toolName.toString(), arguments, text.toString())
        }
        return text.toString().trim().ifBlank { throw AiException("A IA não devolveu texto.") }
    }

    private fun boundedUsage(value: Any?): Int? = (value as? Number)?.toLong()
        ?.takeIf { it in 0..10_000_000L }?.toInt()
}
