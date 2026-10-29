package br.com.nexus.assistant.device

import org.junit.Assert.*
import org.junit.Test

class VoiceSelectionTest {
    private fun voice(name: String, language: String, network: Boolean = false) = VoiceOption(name, name, language, network)

    @Test fun aWorkingEngineWithoutEnumeratedVoicesIsNotFalselyDisabled() {
        assertEquals(SpeechVoiceChoice.EngineLanguageDefault, chooseSpeechVoice("pt-BR", null, emptyList(), 1))
        assertEquals(SpeechVoiceChoice.EngineLanguageDefault, chooseSpeechVoice("pt-BR", null, emptyList(), 2))
        assertEquals(SpeechVoiceChoice.LanguageUnavailable, chooseSpeechVoice("pt-BR", null, emptyList(), -1))
    }

    @Test fun genericPortugueseMustNotSilentlyReplaceBrazilianPortuguese() {
        assertEquals(SpeechVoiceChoice.LanguageUnavailable, chooseSpeechVoice("pt-BR", null,
            listOf(voice("portugal", "pt-PT")), 0))
        assertTrue(ttsSupportsLocale("pt", 0))
        assertFalse(ttsSupportsLocale("pt-BR", 0))
    }

    @Test fun localVoiceWinsButUserCanExplicitlyChooseNetworkVoice() {
        val local = voice("local", "pt-BR")
        val network = voice("network", "pt-BR", true)
        assertEquals(SpeechVoiceChoice.Named(local), chooseSpeechVoice("pt-BR", null, listOf(network, local), 2))
        assertEquals(SpeechVoiceChoice.Named(network), chooseSpeechVoice("pt-BR", "network", listOf(network, local), 2))
    }

    @Test fun missingSavedVoiceDoesNotSwitchToNetworkWithoutChoice() {
        assertEquals(SpeechVoiceChoice.ExplicitNetworkVoiceRequired,
            chooseSpeechVoice("pt-BR", "removed", listOf(voice("network", "pt-BR", true)), 2))
    }

    @Test fun aSavedForeignVoiceCannotOverrideCurrentLanguage() {
        val english = voice("en", "en-US")
        val brazilian = voice("br", "pt-BR")
        assertEquals(SpeechVoiceChoice.Named(brazilian), chooseSpeechVoice("pt-BR", "en", listOf(english, brazilian), 2))
        assertEquals(SpeechVoiceChoice.LanguageUnavailable, chooseSpeechVoice("", null, emptyList(), 2))
    }
    @Test fun enumeratedBrazilianVoiceWorksEvenWhenTheEngineReportsOnlyGenericLanguageSupport() {
        val brazilian = voice("samsung-br", "pt-BR")
        assertEquals(SpeechVoiceChoice.Named(brazilian),
            chooseSpeechVoice("pt-BR", null, listOf(brazilian), 0))
        assertEquals(SpeechVoiceChoice.Named(brazilian),
            chooseSpeechVoice("pt_BR", null, listOf(brazilian), 0))
        assertFalse(ttsSupportsLocale("pt-BR", 0))
    }

}
