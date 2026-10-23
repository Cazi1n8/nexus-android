package br.com.nexus.assistant.data

/** Preferences only: credentials are deliberately excluded from DataStore. */
data class NexusSettings(
    val endpoint: String = "",
    val model: String = "",
    val userName: String = "",
    val personality: String = "Amigável",
    val detail: String = "Equilibrado",
    val language: String = "pt-BR",
    val voiceRate: Float = 1f,
    val voiceName: String = "",
    val autoSpeak: Boolean = false,
    val remoteConsent: Boolean = false,
)
