package br.com.nexus.assistant.data.local

import br.com.nexus.assistant.data.AiCoordinator
import br.com.nexus.assistant.data.AiException
import br.com.nexus.assistant.data.AiMessage
import br.com.nexus.assistant.data.AiMode
import br.com.nexus.assistant.data.AiProvider
import br.com.nexus.assistant.data.NexusSettings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class LocalModelTest {
    @Test fun catalogPinsRevisionSizeAndHash() {
        val model = LocalModelCatalog.compact
        assertEquals(491_400_032L, model.bytes)
        assertEquals("74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db", model.sha256)
        assertTrue(model.downloadUrl.contains("/9217f5db79a29953eb74d5343926648285ec7e67/"))
        assertTrue(model.downloadUrl.startsWith("https://"))
        val balanced = LocalModelCatalog.forProfile(LocalModelProfile.BALANCED)
        assertEquals(1_117_320_736L, balanced.bytes)
        assertEquals("6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e", balanced.sha256)
        assertTrue(balanced.downloadUrl.contains("/91cad51170dc346986eccefdc2dd33a9da36ead9/"))
        // Each profile owns a distinct file, so deleting one cannot remove the other.
        assertEquals(2, LocalModelProfile.entries.map { LocalModelCatalog.forProfile(it).fileName }.toSet().size)
    }

    @Test fun integrityRejectsSameLengthCorruptionAndPartialFile() {
        val file = File.createTempFile("nexus-model", ".gguf")
        val spec = LocalModelSpec("test.gguf", "test", "https://example.org/model", 3,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        try {
            file.writeText("abc")
            assertTrue(ModelIntegrity.verify(file, spec))
            file.writeText("abd")
            assertFalse(ModelIntegrity.verify(file, spec))
            file.writeText("ab")
            assertFalse(ModelIntegrity.verify(file, spec))
        } finally { file.delete() }
    }

    @Test fun verifierChecksCancellation() {
        val file = File.createTempFile("nexus-cancel", ".gguf")
        file.writeText("abc")
        try {
            val spec = LocalModelSpec("test", "test", "https://example.org", 3, "irrelevant")
            assertThrows(CancellationException::class.java) {
                ModelIntegrity.verify(file, spec) { throw CancellationException("cancel") }
            }
        } finally { file.delete() }
    }

    @Test fun localContextRetainsUnicodeLastInputAndHasBoundedHistory() {
        val messages = (1..20).map { AiMessage(if (it % 2 == 0) "user" else "assistant", "Mensagem $it") }
            .dropLast(1) + AiMessage("user", "Olá, NEXUS! 🧠")
        val json = JSONArray(LocalChatContext.build(NexusSettings(), messages, emptyList()))
        assertTrue(json.length() <= 9)
        assertEquals("system", json.getJSONObject(0).getString("role"))
        assertEquals("Olá, NEXUS! 🧠", json.getJSONObject(json.length() - 1).getString("content"))
        assertFalse(json.toString().contains("Mensagem 1\""))
    }

    @Test fun localImageFailsInsteadOfPretendingToSee() {
        assertThrows(AiException::class.java) {
            LocalChatContext.build(NexusSettings(), listOf(AiMessage("user", "O que é?", "/image.jpg")), emptyList())
        }
    }

    @Test fun earlierImageIsMarkedUnavailableAndTextConversationCanContinue() {
        val json = LocalChatContext.build(NexusSettings(), listOf(
            AiMessage("user", "Imagem", "/image.jpg"), AiMessage("assistant", "Texto"), AiMessage("user", "Olá"),
        ), emptyList())
        assertTrue(json.contains("Imagem anterior indisponível"))
    }

    @Test fun authorizedMemoryUsesRelevantEntriesFirst() {
        val memories = listOf("Eu gosto de futebol", "Estudo circuitos eletrônicos", "Meu nome é Ana")
        assertEquals("Estudo circuitos eletrônicos", LocalChatContext.relevantMemories("Ajude com meu circuito eletrônico", memories).first())
    }

    @Test fun ordinaryMemoryAndSchoolQueriesDoNotReceiveToolExamples() {
        for (query in listOf("Meu objeto preferido é um cubo ciano.", "Explique a fotossíntese.",
            "Explique como abrir as configurações.", "Não abra as configurações.")) {
            val system = JSONArray(LocalChatContext.build(NexusSettings(), listOf(AiMessage("user", query)), emptyList()))
                .getJSONObject(0).getString("content")
            assertFalse(query, system.contains("nexus_action"))
        }
    }

    @Test fun explicitSettingsRequestGetsOnlyTheConfirmedActionSchema() {
        val system = JSONArray(LocalChatContext.build(NexusSettings(),
            listOf(AiMessage("user", "NEXUS, poderia abrir as configurações?")), emptyList()))
            .getJSONObject(0).getString("content")
        assertTrue(system.contains("open_settings"))
        assertTrue(system.contains("confirmação"))
        assertFalse(system.contains("open_app"))
    }

    @Test fun smallRuntimeBudgetBoundsHistoryWithoutTruncatingLatestUserInput() {
        val messages = (1..12).map { AiMessage(if (it % 2 == 0) "user" else "assistant", "Mensagem $it") }
        val context = JSONArray(LocalChatContext.build(NexusSettings(), messages, emptyList(), contextTokens = 512))
        assertTrue(context.length() <= 5)
        assertEquals("Mensagem 12", context.getJSONObject(context.length() - 1).getString("content"))
    }

    @Test fun defaultCoordinatorDoesNotSendTokenOrUseRemoteProvider() = runTest {
        var localCalls = 0
        var remoteCalls = 0
        val local = object : AiProvider {
            override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String {
                localCalls++
                assertEquals("", token)
                return "local-test-provider"
            }
        }
        val remote = object : AiProvider {
            override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String {
                remoteCalls++
                return "custom-test-provider"
            }
        }
        val provider = AiCoordinator(local, remote)
        assertEquals(AiMode.NEXUS_LOCAL, NexusSettings().aiMode)
        assertEquals("local-test-provider", provider.complete(NexusSettings(), "private-token", emptyList(), emptyList()))
        assertEquals(1, localCalls)
        assertEquals(0, remoteCalls)
        assertEquals("custom-test-provider", provider.complete(NexusSettings(aiMode = AiMode.CUSTOM), "custom-token", emptyList(), emptyList()))
        assertEquals(1, localCalls)
        assertEquals(1, remoteCalls)
    }
}
