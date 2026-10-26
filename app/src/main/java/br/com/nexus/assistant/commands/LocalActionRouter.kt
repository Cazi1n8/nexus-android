package br.com.nexus.assistant.commands

import br.com.nexus.assistant.data.AiCompletion
import org.json.JSONObject

/**
 * A small command grammar, separate from language generation. Only a complete
 * user request can propose a known action; ordinary conversation still goes to
 * the selected language model. No action is performed here.
 */
object LocalActionRouter {
    fun propose(text: String): String? {
        if (text.length > 200 || text.any { it in "\n\r\"'“”‘’`" }) return null
        val phrase = PhoneCommands.normalize(PhoneCommands.removeWakePhrase(text))
            .removePrefix("por favor ").removeSuffix(" por favor")
        val request = when (phrase) {
            "abrir configuracoes", "abra configuracoes", "abre configuracoes",
            "abrir as configuracoes", "abra as configuracoes", "abre as configuracoes" ->
                "open_settings" to JSONObject()

            "abrir camera", "abra camera", "abre camera",
            "abrir a camera", "abra a camera", "abre a camera",
            "abrir vision", "abra vision", "abre vision",
            "abrir nexus vision", "abra nexus vision", "abre nexus vision" -> navigate("vision")

            "ativar mouse virtual", "ative mouse virtual", "ativa mouse virtual",
            "ativar o mouse virtual", "ative o mouse virtual", "ativa o mouse virtual",
            "abrir mouse virtual", "abra mouse virtual", "abre mouse virtual" -> navigate("mouse")

            "iniciar modo maos livres", "inicie modo maos livres", "inicia modo maos livres",
            "abrir modo maos livres", "abra modo maos livres", "abre modo maos livres" -> navigate("hands")

            "quero aprender libras", "abrir libras", "abra libras", "abre libras",
            "abrir tutor de libras", "abra tutor de libras", "abre tutor de libras",
            "abrir o tutor de libras", "abra o tutor de libras", "abre o tutor de libras" -> navigate("libras")

            "abrir nexus 3d", "abra nexus 3d", "abre nexus 3d" -> navigate("cube")
            "abrir central de controle", "abra central de controle", "abre central de controle" -> navigate("control")
            "abrir configuracoes do nexus", "abra configuracoes do nexus", "abre configuracoes do nexus" -> navigate("settings")
            "abrir historico", "abra historico", "abre historico" -> navigate("memory")

            "abrir youtube", "abra youtube", "abre youtube",
            "abrir o youtube", "abra o youtube", "abre o youtube" -> openApp("com.google.android.youtube")
            "abrir whatsapp", "abra whatsapp", "abre whatsapp",
            "abrir o whatsapp", "abra o whatsapp", "abre o whatsapp" -> openApp("com.whatsapp")
            else -> return null
        }
        // Same strictly typed allowlist as remote tool calls. A model cannot
        // broaden these capabilities or turn a route into a permission grant.
        val proposal = ActionProposal.parse(request.first, request.second)
        return AiCompletion.toolResponse(request.first, request.second,
            "Confirme para continuar: ${proposal.label}. A ação ainda não foi executada.")
    }

    private fun navigate(route: String) = "navigate" to JSONObject().put("route", route)
    private fun openApp(packageName: String) = "open_app" to JSONObject().put("packageName", packageName)
}
