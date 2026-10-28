package br.com.nexus.assistant.data

import br.com.nexus.assistant.commands.LocalActionRouter

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
    ): String {
        builtInResponse(messages.lastOrNull())?.let { return it }
        return when (settings.aiMode) {
            AiMode.NEXUS_LOCAL -> local.complete(settings, "", messages, memories)
            AiMode.CUSTOM -> custom.complete(settings, token, messages, memories)
        }
    }

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
