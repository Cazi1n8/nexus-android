package br.com.nexus.assistant.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import br.com.nexus.assistant.data.local.LocalModelProfile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.nexusPreferences by preferencesDataStore(name = "nexus_preferences")

/** Persistent settings plus a separate Android Keystore protected session token. */
class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val encryptedPreferences = appContext.getSharedPreferences("nexus_session", Context.MODE_PRIVATE)
    private val tokenLock = Any()

    val settings: Flow<NexusSettings> = appContext.nexusPreferences.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::decodeNexusSettings)

    suspend fun save(settings: NexusSettings) {
        appContext.nexusPreferences.edit { preferences -> encodeNexusSettings(preferences, settings) }
    }

    /** Store a backend session token here, never a provider's private API key. */
    var sessionToken: String
        get() = synchronized(tokenLock) {
            val stored = encryptedPreferences.getString(TOKEN_VALUE, null) ?: return@synchronized ""
            try {
                val parts = stored.split(':', limit = 2)
                check(parts.size == 2)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
                )
                String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
            } catch (error: Exception) {
                throw IllegalStateException("Não foi possível abrir o token protegido. Apague-o e configure uma nova sessão.", error)
            }
        }
        set(value) = synchronized(tokenLock) {
            if (value.isBlank()) {
                check(encryptedPreferences.edit().remove(TOKEN_VALUE).commit()) {
                    "Não foi possível apagar o token de sessão."
                }
            } else {
                require(value.length <= 8_192) { "Token de sessão muito longo." }
                try {
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
                    val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
                    val encrypted = Base64.encodeToString(cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
                    check(encryptedPreferences.edit().putString(TOKEN_VALUE, "$iv:$encrypted").commit())
                } catch (error: Exception) {
                    throw IllegalStateException("Não foi possível proteger o token de sessão neste aparelho.", error)
                }
            }
        }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "nexus.backend.session.v1"
        const val TOKEN_VALUE = "encrypted_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

private object Keys {
    val aiMode = stringPreferencesKey("ai_mode")
    val localModelProfile = stringPreferencesKey("local_model_profile")
    val automaticProfile = booleanPreferencesKey("automatic_profile")
    val endpoint = stringPreferencesKey("endpoint")
    val model = stringPreferencesKey("model")
    val userName = stringPreferencesKey("user_name") // Legacy migration only.
    val personality = stringPreferencesKey("personality") // Legacy combined style/instructions.
    val userPreferredName = stringPreferencesKey("user_preferred_name")
    val assistantPersonality = stringPreferencesKey("assistant_personality")
    val assistantInstructions = stringPreferencesKey("assistant_instructions")
    val userPreferences = stringPreferencesKey("user_preferences")
    val detail = stringPreferencesKey("detail")
    val language = stringPreferencesKey("language")
    val voiceRate = floatPreferencesKey("voice_rate")
    val voicePitch = floatPreferencesKey("voice_pitch")
    val voiceName = stringPreferencesKey("voice_name")
    val autoSpeak = booleanPreferencesKey("auto_speak")
    val remoteConsent = booleanPreferencesKey("remote_consent")
}

/** Pure mapping keeps migrations testable without starting Android sensors or a database. */
internal fun decodeNexusSettings(preferences: Preferences): NexusSettings {
    val legacyStyle = preferences[Keys.personality]
    val legacyCustomInstructions = legacyStyle?.takeUnless { it in AssistantIdentity.personalities }.orEmpty()
    return NexusSettings(
        aiMode = preferences[Keys.aiMode]?.let { value -> AiMode.entries.firstOrNull { it.name == value } } ?: AiMode.NEXUS_LOCAL,
        localModelProfile = preferences[Keys.localModelProfile]?.let { value -> LocalModelProfile.entries.firstOrNull { it.name == value } } ?: LocalModelProfile.COMPACT,
        automaticProfile = preferences[Keys.automaticProfile] ?: true,
        endpoint = preferences[Keys.endpoint].orEmpty(), model = preferences[Keys.model].orEmpty(),
        userPreferredName = preferences[Keys.userPreferredName] ?: preferences[Keys.userName].orEmpty(),
        assistantPersonality = preferences[Keys.assistantPersonality]
            ?: legacyStyle?.takeIf { it in AssistantIdentity.personalities }
            ?: if (legacyCustomInstructions.isNotBlank()) "Personalizado" else "Amigável",
        assistantInstructions = preferences[Keys.assistantInstructions] ?: legacyCustomInstructions,
        userPreferences = preferences[Keys.userPreferences].orEmpty(),
        detail = preferences[Keys.detail] ?: "Equilibrado", language = preferences[Keys.language] ?: "pt-BR",
        voiceRate = preferences[Keys.voiceRate] ?: 1f, voicePitch = preferences[Keys.voicePitch] ?: 1f,
        voiceName = preferences[Keys.voiceName].orEmpty(), autoSpeak = preferences[Keys.autoSpeak] ?: false,
        remoteConsent = preferences[Keys.remoteConsent] ?: false,
    )
}

internal fun encodeNexusSettings(preferences: MutablePreferences, settings: NexusSettings) {
    preferences[Keys.aiMode] = settings.aiMode.name
    preferences[Keys.localModelProfile] = settings.localModelProfile.name
    preferences[Keys.automaticProfile] = settings.automaticProfile
    preferences[Keys.endpoint] = settings.endpoint.trim()
    preferences[Keys.model] = settings.model.trim()
    preferences[Keys.userPreferredName] = settings.userPreferredName.take(100).trim()
    preferences[Keys.assistantPersonality] = settings.assistantPersonality.take(100)
    preferences[Keys.assistantInstructions] = settings.assistantInstructions.take(2_000)
    preferences[Keys.userPreferences] = settings.userPreferences.take(2_000)
    // Delete the old duplicated fields after migration, so a cleared/new name cannot revive.
    preferences.remove(Keys.userName)
    preferences.remove(Keys.personality)
    preferences[Keys.detail] = settings.detail.take(100)
    preferences[Keys.language] = settings.language.take(50)
    preferences[Keys.voiceRate] = settings.voiceRate.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
    preferences[Keys.voicePitch] = settings.voicePitch.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
    preferences[Keys.voiceName] = settings.voiceName.take(200)
    preferences[Keys.autoSpeak] = settings.autoSpeak
    preferences[Keys.remoteConsent] = settings.remoteConsent
}
