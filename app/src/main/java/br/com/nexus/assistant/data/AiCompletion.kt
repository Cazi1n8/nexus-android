package br.com.nexus.assistant.data

import br.com.nexus.assistant.commands.ActionProposal
import org.json.JSONObject
import org.json.JSONTokener

data class AiCompletion(val text: String, val proposal: ActionProposal? = null) {
    companion object {
        /** Only an entire, strictly typed JSON response can create a proposal. Prose is never an action. */
        fun fromText(raw: String): AiCompletion {
            val json = if (raw.trim().startsWith("{") && raw.length <= 16_384) {
                runCatching {
                    val tokens = JSONTokener(raw)
                    val parsed = tokens.nextValue() as? JSONObject
                    if (tokens.nextClean() != '\u0000') null else parsed
                }.getOrNull()
            } else null
            if (json == null || !json.has("nexus_action")) return AiCompletion(raw)
            ActionProposal.requireKeys(json, setOf("nexus_action", "message"))
            val request = json.optJSONObject("nexus_action") ?: throw AiException("A proposta de ação não é válida.")
            ActionProposal.requireKeys(request, setOf("name", "arguments"))
            val name = request.opt("name") as? String ?: throw AiException("A proposta não tem um nome válido.")
            val args = request.optJSONObject("arguments") ?: throw AiException("A proposta não tem argumentos válidos.")
            val message = json.opt("message") as? String ?: throw AiException("A proposta não tem uma mensagem válida.")
            if (message.length > 2_000) throw AiException("A proposta de ação é longa demais.")
            val proposal = ActionProposal.parse(name, args)
            return AiCompletion(message.ifBlank { "Confirme a ação solicitada: ${proposal.label}." }, proposal)
        }

        fun toolResponse(name: String, arguments: JSONObject, message: String): String {
            // Validate at the transport boundary, then validate again before creating the pending proposal.
            ActionProposal.parse(name, arguments)
            return JSONObject().put("nexus_action", JSONObject().put("name", name).put("arguments", arguments))
                .put("message", message.take(2_000)).toString()
        }
    }
}
