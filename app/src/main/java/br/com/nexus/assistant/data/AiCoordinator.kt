package br.com.nexus.assistant.data

/** Provider selection is explicit; local conversations are never forwarded to a server. */
class AiCoordinator(
    private val local: AiProvider,
    private val custom: AiProvider = OpenAiCompatibleProvider(),
) : AiProvider {
    override suspend fun complete(
        settings: NexusSettings,
        token: String,
        messages: List<AiMessage>,
        memories: List<String>,
    ): String = when (settings.aiMode) {
        AiMode.NEXUS_LOCAL -> local.complete(settings, "", messages, memories)
        AiMode.CUSTOM -> custom.complete(settings, token, messages, memories)
    }
}
