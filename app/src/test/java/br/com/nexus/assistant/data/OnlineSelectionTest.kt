package br.com.nexus.assistant.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OnlineSelectionTest {
    private fun unexpected() = object : AiProvider {
        override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String =
            error("The selected mode must not call another provider")
    }

    @Test fun newInstallationSelectsOnlineWithoutAuthorizingDataOrChangingAnExistingMode() {
        assertEquals(AiMode.NEXUS_ONLINE, NexusSettings().aiMode)
        val fresh = decodeNexusSettings(emptyPreferences())
        assertEquals(AiMode.NEXUS_ONLINE, fresh.aiMode)
        assertFalse(fresh.onlineConsent)
        assertFalse(fresh.remoteConsent)
        assertEquals(AiMode.NEXUS_LOCAL, decodeNexusSettings(mutablePreferencesOf(stringPreferencesKey("ai_mode") to "NEXUS_LOCAL")).aiMode)
        val advanced = decodeNexusSettings(mutablePreferencesOf(stringPreferencesKey("ai_mode") to "CUSTOM", booleanPreferencesKey("remote_consent") to true))
        assertEquals(AiMode.CUSTOM, advanced.aiMode)
        assertTrue(advanced.remoteConsent)
        assertFalse(advanced.onlineConsent)
    }

    @Test fun integratedConsentRoundTripsSeparatelyFromAdvancedConsent() {
        val preferences = mutablePreferencesOf()
        encodeNexusSettings(preferences, NexusSettings(onlineConsent = true, remoteConsent = false))
        val actual = decodeNexusSettings(preferences)
        assertTrue(actual.onlineConsent)
        assertFalse(actual.remoteConsent)
    }

    @Test fun integratedModeGetsOwnProviderWithoutAdvancedCredentialOrOfflineFallback() = runBlocking {
        val question = listOf(AiMessage("user", "Explique o circuito desta foto", "image.jpg"))
        val online = object : AiProvider {
            override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String {
                assertEquals("", token)
                assertEquals(question, messages)
                assertEquals(listOf("Preferência autorizada"), memories)
                return "Controlled transport fixture, not a real model evaluation"
            }
        }
        val answer = AiCoordinator(unexpected(), unexpected(), online).complete(NexusSettings(onlineConsent = true), "advanced-private", question, listOf("Preferência autorizada"))
        assertTrue(answer.startsWith("Controlled"))
    }

    @Test fun missingServiceNeverInventsAResponseOrForwardsToAnotherProvider() = runBlocking {
        try {
            AiCoordinator(unexpected(), unexpected()).complete(NexusSettings(), "private", listOf(AiMessage("user", "Crie um texto")), emptyList())
            fail("Unconfigured integrated service must be explicit")
        } catch (failure: AiException) {
            assertTrue(failure.message!!.contains("não foi configurado"))
        }
    }
}
