package br.com.nexus.assistant.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLanguageTest {
    @Test fun brazilianPortugueseNeverSelectsForeignVoice() {
        assertFalse(voiceMatchesLanguage("ja-JP", "pt-BR"))
        assertFalse(voiceMatchesLanguage("en-US", "pt-BR"))
        assertFalse(voiceMatchesLanguage("pt-PT", "pt-BR"))
        assertFalse(voiceMatchesLanguage("pt", "pt-BR"))
        assertTrue(voiceMatchesLanguage("pt-BR", "pt-BR"))
    }

    @Test fun explicitLanguageChangeAllowsOnlyTheNewLanguage() {
        assertTrue(voiceMatchesLanguage("en-US", "en-US"))
        assertFalse(voiceMatchesLanguage("pt-BR", "en-US"))
    }

    @Test fun localeTagsAreCanonicalizedAndEmptyRequestNeverMatches() {
        assertTrue(voiceMatchesLanguage("pt-br", "pt-BR"))
        assertFalse(voiceMatchesLanguage("", "pt-BR"))
        assertFalse(voiceMatchesLanguage("pt-BR", ""))
    }
}
