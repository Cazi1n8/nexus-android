package br.com.nexus.assistant.data

import br.com.nexus.assistant.BuildConfig
import br.com.nexus.assistant.commands.ActionProposal
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class AiMessage(val role: String, val content: String, val imagePath: String? = null)

interface AiProvider {
    suspend fun complete(
        settings: NexusSettings,
        token: String,
        messages: List<AiMessage>,
        memories: List<String>,
    ): String
}

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Real OpenAI-compatible HTTP transport. No mock response and no embedded API key. */
class OpenAiCompatibleProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
    private val allowInsecureLocal: Boolean = BuildConfig.DEBUG,
) : StreamingAiProvider {
    private val capabilityDiscovery = GatewayCapabilityDiscovery(client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(7, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build())

    /** No endpoint-name guessing: only the authenticated NEXUS declaration disables tools. */
    private suspend fun negotiated(request: Request, searchRequested: Boolean = false): Request {
        val capabilities = capabilityDiscovery.discover(request)
        if (searchRequested && capabilities?.search != true) {
            throw AiException("Não foi possível confirmar a ferramenta de pesquisa NEXUS. Verifique conexão, sessão e configuração de pesquisa do gateway.")
        }
        if (capabilities?.tools != false) return request
        val body = JSONObject(okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        body.remove("tools")
        body.remove("tool_choice")
        return request.newBuilder().post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
    }
    override suspend fun complete(
        settings: NexusSettings,
        token: String,
        messages: List<AiMessage>,
        memories: List<String>,
    ): String = withContext(Dispatchers.IO) {
        val request = buildRequest(settings, token, messages, memories)
        awaitResponse(client.newCall(negotiated(request)))
    }

    /** Search is always an explicit request to a developer-configured gateway. */
    suspend fun completeWithSearch(
        settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>, query: String,
    ): String = withContext(Dispatchers.IO) {
        awaitResponse(client.newCall(negotiated(buildRequest(settings, token, messages, memories, searchQuery = query), searchRequested = true)))
    }

    override suspend fun stream(
        settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>,
        onDelta: suspend (String) -> Unit,
    ): AiStreamResult {
        val startedAt = System.nanoTime()
        return withContext(Dispatchers.IO) {
            streamRequest(negotiated(buildRequest(settings, token, messages, memories, streaming = true)), onDelta, startedAt)
        }
    }

    suspend fun streamWithSearch(
        settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>, query: String,
        onDelta: suspend (String) -> Unit,
    ): AiStreamResult {
        val startedAt = System.nanoTime()
        return withContext(Dispatchers.IO) {
            streamRequest(negotiated(buildRequest(settings, token, messages, memories, streaming = true, searchQuery = query), searchRequested = true), onDelta, startedAt)
        }
    }

    private suspend fun streamRequest(request: Request, onDelta: suspend (String) -> Unit, startedAt: Long): AiStreamResult = withContext(Dispatchers.IO) {
        coroutineScope {
            var firstTokenMs: Long? = null
            val call = client.newCall(request)
            // Cancellation must close an active socket, including while readLine
            // blocks waiting for the next event. Job-completion-only hooks are too late.
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw httpError(response.code)
                    val body = response.body ?: throw AiException("O servidor devolveu uma resposta vazia.")
                    if (body.contentType()?.let { "${it.type}/${it.subtype}" } != "text/event-stream") {
                        throw AiException("O servidor não oferece respostas incrementais neste formato.")
                    }
                    val decoder = OpenAiStreamDecoder()
                    val events = SseEventReader(body)
                    while (!decoder.done) {
                        ensureActive()
                        val data = events.nextData() ?: break
                        val delta = decoder.accept(data)
                        if (delta.isNotEmpty()) {
                            if (firstTokenMs == null) firstTokenMs = (System.nanoTime() - startedAt) / 1_000_000L
                            onDelta(delta)
                        }
                    }
                    AiStreamResult(decoder.finish(), decoder.finishReason ?: "unknown", firstTokenMs,
                        (System.nanoTime() - startedAt) / 1_000_000L, decoder.inputTokens, decoder.outputTokens, decoder.sources)
                }
            } catch (error: IOException) {
                ensureActive()
                throw AiException("A conexão com a IA foi interrompida durante a resposta. Tente novamente.", error)
            } finally { cancellation.cancel() }
        }
    }

    internal fun buildRequest(
        settings: NexusSettings,
        token: String,
        messages: List<AiMessage>,
        memories: List<String>,
        streaming: Boolean = false,
        searchQuery: String? = null,
    ): Request {
        if (!settings.remoteConsent) throw AiException("Autorize o envio de mensagens, memórias e imagens ao servidor em Configurações antes de usar a IA.")
        if (settings.model.isBlank()) throw AiException("Informe o modelo de IA em Configurações. Para imagens, escolha um modelo com visão.")
        if (settings.model.length > 200) throw AiException("O nome do modelo deve ter até 200 caracteres.")
        if (messages.isEmpty()) throw AiException("Escreva uma mensagem ou selecione uma imagem.")
        if (messages.last().role != "user") throw AiException("A solicitação atual precisa ser uma mensagem do usuário.")
        if (messages.any { it.role !in setOf("user", "assistant") }) throw AiException("O histórico contém um papel de mensagem inválido.")
        if (token.any { it.code !in 0x20..0x7e } || token.length > 8_192) throw AiException("Token de sessão inválido. Configure uma nova sessão.")

        val selected = selectContext(messages)
        val imageIndexes = selected.indices.filter { selected[it].imagePath != null }.takeLast(MAX_IMAGES)
        val omittedHistory = selected.size < messages.size
        val contextNotice = if (omittedHistory) "\nParte do histórico anterior foi omitida para caber no contexto. Não invente o conteúdo ausente; use apenas os turnos e imagens realmente presentes nesta requisição." else ""
        val visualInputs = if (imageIndexes.isEmpty()) {
            "Nenhuma imagem foi anexada a esta requisição. Você recebe somente o texto desta conversa; não está vendo o ambiente do usuário."
        } else {
            "Esta requisição contém ${imageIndexes.size} foto(s) anexada(s). Analise somente os pixels dessas fotos; elas podem ser de momentos anteriores."
        }
        val bodyMessages = JSONArray().put(JSONObject().put("role", "system").put("content",
            systemPrompt(settings, memories) + contextNotice + "\nEntradas efetivas desta requisição: " + visualInputs +
                " Não existe transmissão ao vivo da câmera, da tela ou do microfone para este modelo."))
        selected.forEachIndexed { index, message ->
            val content: Any = if (index in imageIndexes) {
                JSONArray()
                    .put(JSONObject().put("type", "text").put("text", message.content.ifBlank { "Descreva esta imagem." }))
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageDataUrl(message.imagePath!!))))
            } else {
                message.content + if (message.imagePath != null) "\n[Imagem anterior omitida para limitar o contexto.]" else ""
            }
            bodyMessages.put(JSONObject().put("role", message.role).put("content", content))
        }
        val body = JSONObject()
            .put("model", settings.model.trim())
            .put("messages", bodyMessages)
            .put("tools", ActionProposal.schemas())
            .put("tool_choice", "auto")
            .put("stream", streaming)
            .put("max_tokens", 1_536)
            .apply {
                if (searchQuery != null) {
                    val query = searchQuery.trim()
                    if (query.length !in 1..500 || query.any { it.isISOControl() }) {
                        throw AiException("A pesquisa deve ter entre 1 e 500 caracteres, sem caracteres de controle.")
                    }
                    put("nexus_search", JSONObject().put("query", query))
                }
            }
            .toString()
        return Request.Builder().url(endpoint(settings.endpoint, allowInsecureLocal))
            .header("Accept", if (streaming) "text/event-stream" else "application/json")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}") }
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    private suspend fun awaitResponse(call: Call): String = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isActive) return
                val message = if (e is SocketTimeoutException) {
                    "A IA demorou para responder. Tente novamente ou verifique se o modelo está carregado no servidor."
                } else {
                    "Não foi possível conectar à IA. Verifique a internet, o endereço HTTPS e o certificado do servidor."
                }
                continuation.resumeWithException(AiException(message, e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!response.isSuccessful) throw httpError(response.code)
                        val body = response.body ?: throw AiException("O servidor devolveu uma resposta vazia.")
                        if (body.contentLength() > MAX_RESPONSE_BYTES) throw AiException("A resposta do servidor ultrapassou o limite de segurança.")
                        val source = body.source()
                        if (source.request(MAX_RESPONSE_BYTES + 1L)) throw AiException("A resposta do servidor ultrapassou o limite de segurança.")
                        val answer = parseAnswer(source.readUtf8())
                        if (continuation.isActive) continuation.resume(answer)
                    } catch (error: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                if (error is AiException) error else AiException("Não foi possível ler a resposta da IA. Confirme a compatibilidade do servidor.", error),
                            )
                        }
                    }
                }
            }
        })
    }

    private fun httpError(code: Int): AiException = AiException(when (code) {
        400, 422 -> "O servidor recusou a mensagem. Confira o nome do modelo e se ele aceita imagens."
        401, 403 -> "A sessão não foi autorizada: o token pode estar incorreto ou expirado. Atualize-o nas Configurações."
        404 -> "Endereço ou modelo não encontrado. Confira a URL da API e o modelo nas Configurações."
        413 -> "A imagem ou conversa é grande demais para o servidor. Envie uma imagem menor."
        429 -> "O servidor atingiu o limite de uso. Aguarde antes de tentar novamente."
        in 500..599 -> "O servidor de IA está indisponível. Verifique se o servidor e o modelo estão funcionando."
        in 300..399 -> "O servidor tentou redirecionar a requisição. Configure o endereço HTTPS final para proteger sua sessão."
        else -> "O servidor de IA recusou a requisição (HTTP $code)."
    })

    companion object {
        // The gateway accepts 64 entries including the system message. This is
        // a transport boundary; the character budget normally limits it first.
        const val MAX_CONTEXT_MESSAGES = 63
        const val MAX_CONTEXT_CHARACTERS = 30_000
        const val MAX_IMAGES = 2
        const val MAX_IMAGE_BYTES = 2 * 1_024 * 1_024
        private const val MAX_RESPONSE_BYTES = 1_024L * 1_024L

        internal fun endpoint(raw: String, allowInsecureLocal: Boolean): HttpUrl {
            val url = raw.trim().toHttpUrlOrNull() ?: throw AiException("Informe uma URL válida para a API em Configurações.")
            if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) {
                throw AiException("A URL da API não pode conter credenciais, parâmetros ou fragmentos. Use o campo de token da sessão.")
            }
            val local = url.host in setOf("localhost", "127.0.0.1", "::1", "10.0.2.2")
            if (url.scheme != "https" && !(allowInsecureLocal && local && url.scheme == "http")) {
                throw AiException("Use HTTPS para proteger mensagens e imagens. HTTP local é permitido apenas em builds de desenvolvimento.")
            }
            val path = url.encodedPath.trimEnd('/')
            return when {
                path.endsWith("/chat/completions") -> url.newBuilder().encodedPath(path).build()
                path.isEmpty() -> url.newBuilder().encodedPath("/v1/chat/completions").build()
                else -> url.newBuilder().encodedPath("$path/chat/completions").build()
            }
        }

        internal fun selectContext(messages: List<AiMessage>): List<AiMessage> {
            val turns = mutableListOf<MutableList<AiMessage>>()
            for (message in messages.dropWhile { it.role != "user" }) {
                if (message.role == "user") turns += mutableListOf<AiMessage>()
                turns.last().add(message)
            }
            if (turns.isEmpty()) throw AiException("O histórico não contém uma mensagem do usuário.")
            val latest = turns.last()
            if (latest.sumOf { it.content.length } > MAX_CONTEXT_CHARACTERS || latest.size > MAX_CONTEXT_MESSAGES) {
                throw AiException("A mensagem atual excede o orçamento de contexto. Divida o texto; o pedido não foi cortado.")
            }
            // A visual follow-up must retain the real most recent photo. Pin
            // its whole turn, never a textual guess pretending to be pixels.
            val imageAnchor = turns.indexOfLast { turn -> turn.any { it.imagePath != null } }
            val pinned = if (imageAnchor >= 0 && imageAnchor != turns.lastIndex) turns[imageAnchor] else emptyList()
            val pinnedCharacters = pinned.sumOf { it.content.length }
            if (pinnedCharacters + latest.sumOf { it.content.length } > MAX_CONTEXT_CHARACTERS ||
                pinned.size + latest.size > MAX_CONTEXT_MESSAGES) {
                throw AiException("A foto anterior e o pedido atual não cabem juntos no contexto. Capture ou anexe a imagem novamente; a análise não prosseguiu sem os pixels.")
            }
            var remainingCharacters = MAX_CONTEXT_CHARACTERS - pinnedCharacters
            var remainingMessages = MAX_CONTEXT_MESSAGES - pinned.size
            val selected = mutableListOf<Int>()
            if (pinned.isNotEmpty()) selected += imageAnchor
            for (index in turns.indices.reversed()) {
                if (pinned.isNotEmpty() && index == imageAnchor) continue
                val turn = turns[index]
                val characters = turn.sumOf { it.content.length }
                if (characters > remainingCharacters || turn.size > remainingMessages) break
                selected += index
                remainingCharacters -= characters
                remainingMessages -= turn.size
            }
            return selected.sorted().flatMap { turns[it] }
        }

        internal fun systemPrompt(settings: NexusSettings, memories: List<String>): String =
            AssistantIdentity.systemPrompt(settings, memories, AssistantPromptMode.REMOTE_MULTIMODAL)

        internal fun imageDataUrl(path: String): String {
            val file = File(path)
            if (!file.isFile) throw AiException("Uma imagem anexada não está mais disponível. Selecione-a novamente.")
            if (file.length() !in 1..MAX_IMAGE_BYTES.toLong()) throw AiException("Cada imagem deve ter até 2 MB. Selecione uma imagem menor.")
            val bytes = file.readBytes()
            val mime = when {
                bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
                bytes.size >= 8 && bytes.sliceArray(0..7).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> "image/png"
                bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
                else -> throw AiException("Formato de imagem inválido. Utilize JPEG, PNG ou WebP.")
            }
            return "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"
        }

        internal fun parseAnswer(raw: String): String {
            try {
                val message = JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                if (message.has("tool_calls") && !message.isNull("tool_calls")) {
                    val calls = message.getJSONArray("tool_calls")
                    if (calls.length() > 0) {
                        if (calls.length() != 1) throw AiException("A IA propôs várias ações. Peça uma ação por vez.")
                        val call = calls.getJSONObject(0)
                        if (call.optString("type") != "function") throw AiException("A IA propôs uma ferramenta incompatível.")
                        val function = call.getJSONObject("function")
                        val arguments = function.getString("arguments")
                        if (arguments.length > 2_048) throw AiException("Os argumentos da ação são longos demais.")
                        return AiCompletion.toolResponse(function.getString("name"), JSONObject(arguments),
                            (message.opt("content") as? String).orEmpty())
                    }
                }
                val content = message.opt("content")
                val answer = when (content) {
                    is String -> content
                    is JSONArray -> (0 until content.length()).mapNotNull { index ->
                        content.optJSONObject(index)?.takeIf { it.optString("type") == "text" }?.opt("text") as? String
                    }.joinToString("\n")
                    else -> ""
                }.ifBlank { (message.opt("refusal") as? String).orEmpty() }.trim()
                if (answer.isBlank()) throw AiException("A IA não devolveu texto. Confira se o modelo suporta conversas neste formato.")
                if (answer.length > 24_000) throw AiException("A resposta foi longa demais para salvar. Solicite uma resposta mais curta.")
                return answer
            } catch (error: JSONException) {
                throw AiException("A resposta do servidor não é compatível com a API de chat. Confira a URL nas Configurações.", error)
            }
        }
    }
}
