package br.com.nexus.assistant.data

import br.com.nexus.assistant.commands.LocalActionRouter

/** Provider selection is explicit; local conversations are never forwarded to a server. */
class AiCoordinator(
    private val local: AiProvider,
    private val custom: AiProvider = OpenAiCompatibleProvider(),
    private val online: AiProvider? = null,
) : StreamingAiProvider {
    override suspend fun complete(
        settings: NexusSettings,
        token: String,
        messages: List<AiMessage>,
        memories: List<String>,
    ): String {
        builtInResponse(messages.lastOrNull())?.let { return it }
        return when (settings.aiMode) {
            AiMode.NEXUS_ONLINE -> onlineProvider().complete(settings, "", messages, memories)
            AiMode.NEXUS_LOCAL -> local.complete(settings, "", messages, memories)
            AiMode.CUSTOM -> custom.complete(settings, token, messages, memories)
        }
    }

    override suspend fun stream(
        settings: NexusSettings,
        token: String,
        messages: List<AiMessage>,
        memories: List<String>,
        onDelta: suspend (String) -> Unit,
    ): AiStreamResult {
        val started = System.nanoTime()
        builtInResponse(messages.lastOrNull())?.let {
            return AiStreamResult(it, "tool", null, (System.nanoTime() - started) / 1_000_000L)
        }
        val selected = when (settings.aiMode) {
            AiMode.NEXUS_ONLINE -> onlineProvider()
            AiMode.NEXUS_LOCAL -> local
            AiMode.CUSTOM -> custom
        }
        val credential = if (settings.aiMode == AiMode.CUSTOM) token else ""
        if (selected is StreamingAiProvider) return selected.stream(settings, credential, messages, memories, onDelta)
        // This provider returns a complete answer. Never split it into artificial
        // deltas or invent a time to first token for a non-streaming runtime.
        val answer = selected.complete(settings, credential, messages, memories)
        return AiStreamResult(answer, "completed", null, (System.nanoTime() - started) / 1_000_000L)
    }

    suspend fun streamWithSearch(
        settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>, query: String,
        onDelta: suspend (String) -> Unit,
    ): AiStreamResult {
        if (settings.aiMode == AiMode.NEXUS_ONLINE) {
            val provider = onlineProvider() as? NexusOnlineProvider
                ?: throw AiException("A pesquisa exige o serviço NEXUS AI configurado.")
            return provider.streamWithSearch(settings, messages, memories, query, onDelta)
        }
        if (settings.aiMode != AiMode.CUSTOM || custom !is OpenAiCompatibleProvider) {
            throw AiException("A pesquisa precisa de um gateway NEXUS com a ferramenta de pesquisa configurada. O modo local não pesquisa na internet.")
        }
        return custom.streamWithSearch(settings, token, messages, memories, query, onDelta)
    }

    private fun onlineProvider(): AiProvider = online
        ?: throw AiException("O serviço NEXUS AI ainda não foi configurado pelo desenvolvedor. Os modelos offline continuam opcionais.")

    companion object {
        /**
         * Real on-device tools need neither downloaded LLM weights nor a remote
         * session. Image requests must still reach the selected visual provider.
         * Action tools only propose a confirmed action; this never executes it.
         */
        fun builtInResponse(message: AiMessage?): String? {
            if (message?.role != "user" || message.imagePath != null) return null
            return LocalActionRouter.propose(message.content) ?: ArithmeticTool.answerFor(message.content)
        }
    }
}
