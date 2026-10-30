package br.com.nexus.assistant.data

import br.com.nexus.assistant.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/** Individual NEXUS credentials, stored separately from advanced provider configuration. */
data class StoredOnlineSession(
    val serviceUrl: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
) {
    override fun toString(): String = "StoredOnlineSession(credentials=<redacted>)"
}

/** Implementations must keep credentials in private, Keystore-encrypted storage. */
interface OnlineSessionStorage {
    fun read(): StoredOnlineSession?
    fun write(value: StoredOnlineSession?)
}

/**
 * Authentication for the developer-operated NEXUS service. No provider key or
 * shared application secret is required by this client. An invitation redeems
 * once; refresh tokens rotate before inference, never by retrying a generation.
 */
class NexusOnlineSessionManager(
    serviceUrl: String,
    private val storage: OnlineSessionStorage,
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build(),
    private val allowInsecureLocal: Boolean = BuildConfig.DEBUG,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) {
    // Security boundaries apply even when an application/test supplies a client.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()
    /** Public deployment location; never contains authentication material. */
    val serviceUrl: String = canonicalServiceUrl(serviceUrl, allowInsecureLocal)
    val configured: Boolean get() = serviceUrl.isNotBlank()

    fun hasSession(): Boolean = configured && storage.read()?.let {
        it.serviceUrl == serviceUrl && validCredential(it.accessToken) && validCredential(it.refreshToken)
    } == true

    suspend fun redeem(invitationCode: String) = withContext(Dispatchers.IO) { credentialLock.withLock {
        requireConfigured()
        val code = invitationCode.trim()
        if (code.length !in 16..256 || code.any { it.code !in 33..126 }) {
            throw AiException("Informe o convite de acesso recebido do responsável pelo NEXUS AI.")
        }
        val session = try {
            parseSession(post("redeem", JSONObject().put("invitation_code", code)))
        } catch (_: SessionRejectedException) {
            throw AiException("O convite foi recusado, expirou ou já foi utilizado. Solicite um novo convite ao responsável pelo NEXUS AI.")
        }
        storage.write(session)
    } }

    suspend fun accessToken(): String = withContext(Dispatchers.IO) { credentialLock.withLock {
        requireConfigured()
        val previous = storage.read()
        if (previous == null || previous.serviceUrl != serviceUrl || !validCredential(previous.refreshToken) ||
            !validCredential(previous.accessToken)) {
            storage.write(null)
            throw AiException("Conecte sua sessão NEXUS AI nas Configurações antes de conversar online.")
        }
        if (previous.expiresAtEpochSeconds - nowEpochSeconds() > REFRESH_MARGIN_SECONDS) {
            return@withLock previous.accessToken
        }
        try {
            val fresh = parseSession(post("refresh", JSONObject().put("refresh_token", previous.refreshToken)))
            if (fresh.refreshToken == previous.refreshToken) {
                storage.write(null)
                throw AiException("O serviço não renovou a sessão com segurança. Conecte o NEXUS AI novamente.")
            }
            storage.write(fresh)
            fresh.accessToken
        } catch (error: SessionRejectedException) {
            storage.write(null)
            throw AiException("Sua sessão NEXUS AI expirou ou foi revogada. Conecte novamente nas Configurações.")
        }
    } }

    /** Local logout always succeeds even if remote revocation cannot be reached. */
    suspend fun revoke() = withContext(Dispatchers.IO) { credentialLock.withLock {
        try {
            val previous = storage.read()
            if (configured && previous?.serviceUrl == serviceUrl && validCredential(previous.refreshToken)) {
                post("revoke", JSONObject().put("refresh_token", previous.refreshToken), expectSession = false)
            }
        } finally {
            storage.write(null)
        }
    } }

    suspend fun clearLocal() = withContext(Dispatchers.IO) { credentialLock.withLock { storage.write(null) } }

    private fun requireConfigured() {
        if (!configured) throw AiException("O serviço NEXUS AI ainda não foi implantado nesta versão. O desenvolvedor precisa configurar o servidor; nenhuma mensagem foi enviada.")
    }

    private suspend fun post(action: String, payload: JSONObject, expectSession: Boolean = true): JSONObject {
        val request = Request.Builder().url(sessionEndpoint(serviceUrl, action, allowInsecureLocal))
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        AiException("Não foi possível conectar ao serviço NEXUS AI. Verifique a conexão e tente novamente.", error),
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!response.isSuccessful) throw when (response.code) {
                                400, 401, 403 -> SessionRejectedException()
                                429 -> AiException("O serviço atingiu o limite de conexões ou uso. Aguarde antes de tentar novamente.")
                                in 300..399 -> AiException("O serviço tentou redirecionar a sessão. A conexão foi recusada para proteger suas credenciais.")
                                else -> AiException("O serviço NEXUS AI está indisponível (HTTP ${response.code}).")
                            }
                            if (!expectSession && response.code == 204) {
                                if (continuation.isActive) continuation.resume(JSONObject())
                                return
                            }
                            val body = response.body ?: throw AiException("O serviço retornou uma sessão vazia.")
                            if (body.contentType()?.let { "${it.type}/${it.subtype}" } != "application/json") {
                                throw AiException("O serviço retornou um formato de sessão incompatível.")
                            }
                            if (body.contentLength() > MAX_BODY_BYTES || body.source().request(MAX_BODY_BYTES + 1L)) {
                                throw AiException("A resposta de autenticação excedeu o limite permitido.")
                            }
                            val json = JSONObject(body.source().readUtf8())
                            if (continuation.isActive) continuation.resume(json)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(
                                if (error is AiException || error is SessionRejectedException) error
                                else AiException("Não foi possível validar a sessão devolvida pelo serviço.", error),
                            )
                        }
                    }
                }
            })
        }
    }

    private fun parseSession(payload: JSONObject): StoredOnlineSession {
        val access = payload.opt("access_token") as? String ?: ""
        val refresh = payload.opt("refresh_token") as? String ?: ""
        val expiry = payload.opt("expires_in")
        val seconds = (expiry as? Number)?.toLong() ?: -1L
        if (!validCredential(access) || !validCredential(refresh) || payload.optString("model") != MODEL_ALIAS ||
            seconds !in 60L..3_600L || (expiry as Number).toDouble() != seconds.toDouble()) {
            throw AiException("O serviço devolveu credenciais ou validade incompatíveis. A sessão não foi salva.")
        }
        return StoredOnlineSession(serviceUrl, access, refresh, nowEpochSeconds() + seconds)
    }

    private class SessionRejectedException : Exception("Sessão ou convite recusado")

    companion object {
        const val MODEL_ALIAS = "nexus-ai"
        private const val REFRESH_MARGIN_SECONDS = 60L
        private const val MAX_BODY_BYTES = 32_768L
        // ViewModel and foreground voice may construct separate clients backed by
        // the same encrypted preferences. Rotation must be serialized process-wide.
        private val credentialLock = Mutex()

        internal fun validCredential(value: String): Boolean = value.length in 24..8_192 && value.all { it.code in 33..126 }

        internal fun canonicalServiceUrl(raw: String, allowInsecureLocal: Boolean): String {
            if (raw.isBlank()) return ""
            val completion = OpenAiCompatibleProvider.endpoint(raw, allowInsecureLocal)
            return completion.newBuilder().encodedPath(completion.encodedPath.removeSuffix("/chat/completions")).build().toString().trimEnd('/')
        }

        internal fun sessionEndpoint(base: String, action: String, allowInsecureLocal: Boolean): HttpUrl {
            require(action in setOf("redeem", "refresh", "revoke"))
            val completion = OpenAiCompatibleProvider.endpoint(base, allowInsecureLocal)
            return completion.newBuilder().encodedPath(completion.encodedPath.removeSuffix("/chat/completions") + "/sessions/$action").build()
        }
    }
}
