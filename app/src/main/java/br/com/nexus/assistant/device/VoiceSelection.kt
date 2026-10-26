package br.com.nexus.assistant.device

import java.util.Locale

/** Selection metadata only; a fallback never claims that an opaque Android engine is offline. */
internal sealed interface SpeechVoiceChoice {
    data class Named(val option: VoiceOption) : SpeechVoiceChoice
    data object EngineLanguageDefault : SpeechVoiceChoice
    data object LanguageUnavailable : SpeechVoiceChoice
    data object ExplicitNetworkVoiceRequired : SpeechVoiceChoice
}

internal fun ttsSupportsLocale(language: String, availability: Int?): Boolean {
    val locale = Locale.forLanguageTag(language)
    if (locale.language.isBlank() || availability == null) return false
    // Android LANG_AVAILABLE=0 can be another country (e.g. pt-PT). A country
    // request needs LANG_COUNTRY_AVAILABLE=1 or LANG_COUNTRY_VAR_AVAILABLE=2.
    return availability >= if (locale.country.isBlank()) 0 else 1
}

internal fun chooseSpeechVoice(
    language: String,
    requestedName: String?,
    options: List<VoiceOption>,
    languageAvailability: Int?,
): SpeechVoiceChoice {
    val eligible = options.filter { voiceMatchesLanguage(it.language, language) }
    eligible.firstOrNull { it.name == requestedName }?.let { return SpeechVoiceChoice.Named(it) }
    eligible.filterNot { it.requiresNetwork }.minByOrNull { it.name }?.let { return SpeechVoiceChoice.Named(it) }
    if (eligible.any { it.requiresNetwork }) return SpeechVoiceChoice.ExplicitNetworkVoiceRequired
    // Some Samsung/other TTS engines support setLanguage but do not enumerate
    // voices. Refusing an empty getVoices() made these working engines silent.
    return if (ttsSupportsLocale(language, languageAvailability)) SpeechVoiceChoice.EngineLanguageDefault
    else SpeechVoiceChoice.LanguageUnavailable
}
