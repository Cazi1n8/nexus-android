package br.com.nexus.assistant.data

import br.com.nexus.assistant.data.local.LocalModelProfile

enum class AiMode { NEXUS_ONLINE, NEXUS_LOCAL, CUSTOM }

/** Preferences only: credentials are deliberately excluded from DataStore. */
data class NexusSettings(
    val aiMode: AiMode = AiMode.NEXUS_ONLINE,
    val localModelProfile: LocalModelProfile = LocalModelProfile.COMPACT,
    val automaticProfile: Boolean = true,
    val endpoint: String = "",
    val model: String = "",
    val userPreferredName: String = "",
    val assistantPersonality: String = "Amigável",
    val assistantInstructions: String = "",
    val userPreferences: String = "",
    val detail: String = "Equilibrado",
    val language: String = "pt-BR",
    val voiceRate: Float = 1f,
    val voicePitch: Float = 1f,
    val voiceName: String = "",
    val autoSpeak: Boolean = false,
    val remoteConsent: Boolean = false,
    val onlineConsent: Boolean = false,
) {
    /** The assistant's identity is not an editable user-profile preference. */
    val assistantName: String get() = AssistantIdentity.NAME

    @Deprecated("Use userPreferredName; this is the user's name, never the assistant's.")
    val userName: String get() = userPreferredName

    @Deprecated("Use assistantPersonality and assistantInstructions separately.")
    val personality: String get() = assistantPersonality
}
