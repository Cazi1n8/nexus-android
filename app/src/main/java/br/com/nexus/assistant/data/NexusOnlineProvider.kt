package br.com.nexus.assistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The developer's service uses individual sessions; advanced tokens stay separate. */
class NexusOnlineProvider(
    private val sessions: NexusOnlineSessionManager,
    private val delegate: OpenAiCompatibleProvider = OpenAiCompatibleProvider(),
) : StreamingAiProvider {
    private fun onlineSettings(settings: NexusSettings): NexusSettings {
        if (!settings.onlineConsent) {
            throw AiException("Autorize o envio de mensagens, memórias e fotos ao serviço NEXUS AI antes de conversar online.")
        }
        return settings.copy(endpoint = sessions.serviceUrl, model = NexusOnlineSessionManager.MODEL_ALIAS, remoteConsent = true)
    }

    /** A revoked account must not leave a cached session appearing connected. */
    private suspend fun <T> authenticatedRequest(request: suspend (String) -> T): T {
        val accessToken = sessions.accessToken()
        try {
            return request(accessToken)
        } catch (error: AiHttpException) {
            if (error.statusCode != 401 && error.statusCode != 403) throw error
            // Never replay an inference: it might already have been billed. The
            // operator maps provider credential errors to 502; only a rejected
            // NEXUS account/session produces 401/403 here.
            withContext(NonCancellable + Dispatchers.IO) { sessions.clearLocal() }
            throw AiException("Sua conta ou sessão NEXUS AI expirou ou foi revogada. Conecte novamente nas Configurações.")
        }
    }

    override suspend fun complete(
        settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>,
    ): String = withContext(Dispatchers.IO) {
        val configured = onlineSettings(settings)
        authenticatedRequest { accessToken -> delegate.complete(configured, accessToken, messages, memories) }
    }

    override suspend fun stream(
        settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>,
        onDelta: suspend (String) -> Unit,
    ): AiStreamResult {
        val started = System.nanoTime()
        return withContext(Dispatchers.IO) {
            val configured = onlineSettings(settings)
            var firstTextMs: Long? = null
            val result = authenticatedRequest { accessToken ->
                delegate.stream(configured, accessToken, messages, memories) { delta ->
                    if (delta.isNotEmpty() && firstTextMs == null) firstTextMs = (System.nanoTime() - started) / 1_000_000L
                    onDelta(delta)
                }
            }
            result.copy(firstTokenMs = firstTextMs, totalMs = (System.nanoTime() - started) / 1_000_000L)
        }
    }

    suspend fun streamWithSearch(
        settings: NexusSettings, messages: List<AiMessage>, memories: List<String>, query: String,
        onDelta: suspend (String) -> Unit,
    ): AiStreamResult {
        val started = System.nanoTime()
        return withContext(Dispatchers.IO) {
            val configured = onlineSettings(settings)
            var firstTextMs: Long? = null
            val result = authenticatedRequest { accessToken ->
                delegate.streamWithSearch(configured, accessToken, messages, memories, query) { delta ->
                    if (delta.isNotEmpty() && firstTextMs == null) firstTextMs = (System.nanoTime() - started) / 1_000_000L
                    onDelta(delta)
                }
            }
            result.copy(firstTokenMs = firstTextMs, totalMs = (System.nanoTime() - started) / 1_000_000L)
        }
    }
}
