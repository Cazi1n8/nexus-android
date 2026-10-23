package br.com.nexus.assistant.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class CustomCommand(val id: String, val phrase: String, val packageName: String, val label: String)
private val Context.commandPreferences by preferencesDataStore(name = "nexus_commands")

class CommandStore(context: Context) {
    private val context = context.applicationContext
    private val key = stringPreferencesKey("custom_commands")
    val commands: Flow<List<CustomCommand>> = this.context.commandPreferences.data
        .catch { if (it is java.io.IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { decode(it[key]) }
    suspend fun save(phrase: String, packageName: String, label: String) {
        require(phrase.isNotBlank() && phrase.length <= 100) { "Escreva um comando de até 100 caracteres." }
        require(PhoneCommands.removeWakePhrase(phrase).isNotBlank()) { "Adicione uma ação depois de NEXUS." }
        require(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+").matches(packageName)) { "Escolha um aplicativo disponível." }
        require(PhoneCommands.resolve(phrase, emptyList()) == null) { "Este comando já é reservado pelo NEXUS. Escolha outra frase." }
        context.commandPreferences.edit { prefs ->
            val old = decode(prefs[key]).filter { PhoneCommands.normalize(PhoneCommands.removeWakePhrase(it.phrase)) != PhoneCommands.normalize(PhoneCommands.removeWakePhrase(phrase)) }
            require(old.size < 100) { "O limite é de 100 comandos personalizados." }
            prefs[key] = encode(old + CustomCommand(UUID.randomUUID().toString(), phrase.trim(), packageName, label.take(120)))
        }
    }
    suspend fun delete(id: String) {
        context.commandPreferences.edit { prefs -> prefs[key] = encode(decode(prefs[key]).filter { it.id != id }) }
    }
    suspend fun clear() { context.commandPreferences.edit { it.remove(key) } }
    private fun decode(raw: String?): List<CustomCommand> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            CustomCommand(json.getString("id"), json.getString("phrase"), json.getString("package"), json.optString("label", json.getString("package")))
        }
    }.getOrDefault(emptyList())
    private fun encode(commands: List<CustomCommand>): String = JSONArray().apply {
        commands.forEach { put(JSONObject().put("id", it.id).put("phrase", it.phrase).put("package", it.packageName).put("label", it.label)) }
    }.toString()
}

/** Exact, user initiated actions only; chat output is never executed as a command. */
object PhoneCommands {
    val examples = listOf("NEXUS, abrir configurações", "NEXUS, abrir câmera", "NEXUS, abrir YouTube", "NEXUS, abrir WhatsApp",
        "NEXUS, ativar mouse virtual", "NEXUS, iniciar modo mãos livres", "NEXUS, abrir tutor de Libras")
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim().replace(Regex("\\s+"), " ")
    fun removeWakePhrase(text: String): String = text.trim().replace(
        Regex("^(?:ei[ ,!]+)?nexus(?:[ ,!:]+|$)", RegexOption.IGNORE_CASE), "").trim()
    fun resolve(spoken: String, commands: List<CustomCommand>): String? {
        val phrase = normalize(removeWakePhrase(spoken))
        val builtIn = when (phrase) {
            "abrir configuracoes" -> "settings"
            "abrir camera" -> "camera"
            "abrir youtube" -> "app:com.google.android.youtube"
            "abrir whatsapp" -> "app:com.whatsapp"
            "ativar mouse virtual", "abrir mouse virtual" -> "route:mouse"
            "desativar mouse virtual" -> "route:home"
            "iniciar modo maos livres", "abrir modo maos livres" -> "route:hands"
            "abrir tutor de libras", "abrir libras" -> "route:libras"
            "abrir nexus vision", "abrir vision" -> "route:vision"
            "abrir nexus 3d" -> "route:cube"
            "abrir central de comandos" -> "route:commands"
            else -> null
        }
        return builtIn ?: commands.firstOrNull { normalize(removeWakePhrase(it.phrase)) == phrase }?.let { "app:${it.packageName}" }
    }
    fun execute(context: Context, action: String): String? {
        return try {
        val intent = when {
            action == "settings" -> Intent(Settings.ACTION_SETTINGS)
            action == "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            action.startsWith("app:") -> context.packageManager.getLaunchIntentForPackage(action.removePrefix("app:"))
                ?: return "Este aplicativo não está instalado ou não possui uma tela que possa ser aberta."
            else -> return "Ação não reconhecida."
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: android.content.ActivityNotFoundException) { "Nenhum aplicativo disponível para esta ação."
        } catch (_: SecurityException) { "O Android não autorizou a abertura deste aplicativo." }
    }

    @Suppress("DEPRECATION")
    fun installedApps(context: Context): List<Pair<String, String>> = context.packageManager
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
        .distinctBy { it.first }.sortedBy { it.second.lowercase(Locale.ROOT) }
}
