package br.com.nexus.assistant.commands

import br.com.nexus.assistant.data.AiException
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** An untrusted model can propose only these actions. The UI must confirm every proposal. */
class ActionProposal private constructor(val id: String, val action: String, val label: String) {
    companion object {
        val routes = setOf("home", "chat", "voice", "vision", "mouse", "hands", "cube", "libras", "commands", "personality", "memory", "settings", "diagnostics")
        private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

        fun parse(name: String, arguments: JSONObject): ActionProposal {
            val (action, label) = when (name) {
                "open_settings" -> {
                    requireKeys(arguments, emptySet())
                    "settings" to "Abrir as configurações do Android"
                }
                "open_app" -> {
                    requireKeys(arguments, setOf("packageName"))
                    val pkg = arguments.opt("packageName") as? String
                        ?: throw AiException("A IA propôs um aplicativo inválido.")
                    if (pkg.length > 200 || !packagePattern.matches(pkg)) throw AiException("A IA propôs um aplicativo inválido.")
                    "app:$pkg" to "Abrir o aplicativo $pkg"
                }
                "navigate" -> {
                    requireKeys(arguments, setOf("route"))
                    val route = arguments.opt("route") as? String
                    if (route !in routes) throw AiException("A IA propôs uma tela que não existe no NEXUS.")
                    "route:$route" to "Abrir a tela $route no NEXUS"
                }
                else -> throw AiException("A IA propôs uma ação que não é permitida.")
            }
            return ActionProposal(UUID.randomUUID().toString(), action, label)
        }

        internal fun requireKeys(json: JSONObject, expected: Set<String>) {
            if (json.keys().asSequence().toSet() != expected) throw AiException("A proposta da IA contém argumentos inválidos.")
        }

        /** Standard OpenAI-compatible tool schemas; deliberately no shell, URL or private-data tools. */
        fun schemas(): JSONArray = JSONArray().apply {
            fun add(name: String, description: String, properties: JSONObject, required: List<String>) {
                put(JSONObject().put("type", "function").put("function", JSONObject()
                    .put("name", name).put("description", description)
                    .put("parameters", JSONObject().put("type", "object").put("properties", properties)
                        .put("required", JSONArray(required)).put("additionalProperties", false))))
            }
            add("open_settings", "Propor abrir configurações do Android. O usuário confirma antes de executar.", JSONObject(), emptyList())
            add("open_app", "Propor abrir aplicativo instalado. Use somente o identificador conhecido; não invente pacotes.",
                JSONObject().put("packageName", JSONObject().put("type", "string")), listOf("packageName"))
            add("navigate", "Propor uma tela interna do NEXUS. O usuário confirma antes de executar.",
                JSONObject().put("route", JSONObject().put("type", "string").put("enum", JSONArray(routes.toList()))), listOf("route"))
        }
    }
}
