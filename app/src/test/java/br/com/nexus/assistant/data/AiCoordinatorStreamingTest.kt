package br.com.nexus.assistant.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiCoordinatorStreamingTest {
    private val history = listOf(AiMessage("user", "Explique programação"))
    private fun unexpected() = object : AiProvider {
        override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String = error("Provider must not run")
    }

    @Test fun localCompletionNeverBecomesArtificialStreamingOrReceivesRemoteCredential() = runBlocking {
        val local = object : AiProvider {
            override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String {
                assertEquals("", token)
                assertEquals(history, messages)
                return "Conteúdo completo"
            }
        }
        val deltas = mutableListOf<String>()
        val result = AiCoordinator(local, unexpected()).stream(NexusSettings(), "private", history, emptyList(), deltas::add)
        assertEquals("Conteúdo completo", result.text)
        assertTrue(deltas.isEmpty())
        assertNull(result.firstTokenMs)
    }

    @Test fun forwardsEachActualRemoteDeltaExactlyOnceAndPreservesFinalMetadata() = runBlocking {
        val remote = object : StreamingAiProvider {
            override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String = error("Use real stream")
            override suspend fun stream(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>, onDelta: suspend (String) -> Unit): AiStreamResult {
                assertEquals("session", token)
                onDelta("Uma "); onDelta("resposta.")
                return AiStreamResult("Uma resposta.", "stop", 12, 45, 100, 4)
            }
        }
        val deltas = mutableListOf<String>()
        val result = AiCoordinator(unexpected(), remote).stream(NexusSettings(aiMode = AiMode.CUSTOM), "session", history, emptyList(), deltas::add)
        assertEquals(listOf("Uma ", "resposta."), deltas)
        assertEquals(deltas.joinToString(""), result.text)
        assertEquals(12L, result.firstTokenMs)
        assertEquals(100, result.inputTokens)
    }

    @Test fun arithmeticWorksWithoutModelOrGatewayEvenThroughStreamingEntryPoint() = runBlocking {
        val deltas = mutableListOf<String>()
        val result = AiCoordinator(unexpected(), unexpected()).stream(NexusSettings(), "", listOf(AiMessage("user", "50 + 50 + 50 × 50")), emptyList(), deltas::add)
        assertTrue(result.text.endsWith(" = 2600"))
        assertEquals("tool", result.finishReason)
        assertTrue(deltas.isEmpty())
    }

    @Test fun localModeRejectsExplicitSearchRatherThanForwardingPrivateConversation() = runBlocking {
        try {
            AiCoordinator(unexpected(), unexpected()).streamWithSearch(NexusSettings(), "private", history, emptyList(), "query") { error("No deltas") }
            fail("Search must require explicit remote mode")
        } catch (failure: AiException) { assertTrue(failure.message!!.contains("modo local")) }
    }
}
