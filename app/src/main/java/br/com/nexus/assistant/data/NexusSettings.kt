package br.com.nexus.assistant.data

import br.com.nexus.assistant.data.local.LocalModelProfile

enum class AiMode { NEXUS_LOCAL, CUSTOM }

/** Preferences only: credentials are deliberately excluded from DataStore. */
data class NexusSettings(
    val aiMode: AiMode = AiMode.NEXUS_LOCAL,
    val localModelProfile: LocalModelProfile = LocalModelProfile.COMPACT,
    val automaticProfile: Boolean = true,
    val endpoint: String = "",
    val model: String = "",
    val userName: String = "",
    val personality: String = "Amigável",
    val detail: String = "Equilibrado",
    val language: String = "pt-BR",
    val voiceRate: Float = 1f,
    val voicePitch: Float = 1f,
    val voiceName: String = "",
    val autoSpeak: Boolean = false,
    val remoteConsent: Boolean = false,
)
