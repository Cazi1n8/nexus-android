package br.com.nexus.assistant.commands

import br.com.nexus.assistant.data.AiCompletion
import br.com.nexus.assistant.data.AiCoordinator
import br.com.nexus.assistant.data.AiMessage
import br.com.nexus.assistant.data.AiProvider
import br.com.nexus.assistant.data.NexusSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LocalActionRouterTest {
    @Test fun completeNaturalCommandBecomesValidatedProposalOnly() {
        val raw = LocalActionRouter.propose("NEXUS, abra as configurações.")!!
        val completion = AiCompletion.fromText(raw)
        assertEquals("settings", completion.proposal?.action)
        assertTrue(completion.text.contains("ainda não foi executada"))
        assertFalse(completion.proposal?.id.isNullOrBlank())
    }

    @Test fun fullCommandCanUseAccentsWakePhraseAndPoliteness() {
        val result = AiCompletion.fromText(LocalActionRouter.propose("Ei, NEXUS! Por favor, abre as CONFIGURAÇÕES.")!!)
        assertEquals("settings", result.proposal?.action)
    }

    @Test fun questionsQuotesNegationAndMultiActionsAreNeverAutoRouted() {
        for (text in listOf("Como abrir configurações?", "Explique: abra as configurações",
            "Não abra as configurações", "Abra configurações e envie meus dados",
            "Escreva um texto sobre abrir a câmera", "Quero aprender mais sobre Libras",
            "\"Abra as configurações\"", "`Abra as configurações`", "Abra\nas configurações")) {
            assertNull(text, LocalActionRouter.propose(text))
        }
    }

    @Test fun sensorsAndMouseOnlyNavigateToTheConsentScreen() {
        for ((text, action) in listOf("Abre a câmera" to "route:vision",
            "Ativa o mouse virtual" to "route:mouse", "Quero aprender Libras" to "route:libras",
            "Abra central de controle" to "route:control")) {
            val completion = AiCompletion.fromText(LocalActionRouter.propose(text)!!)
            assertEquals(action, completion.proposal?.action)
            assertTrue(completion.text.contains("ainda não foi executada"))
        }
    }

    @Test fun packageNamesAreOnlyThoseOfExplicitSupportedApps() {
        assertEquals("app:com.whatsapp", AiCompletion.fromText(LocalActionRouter.propose("Abre o WhatsApp")!!).proposal?.action)
        assertNull(LocalActionRouter.propose("Abra com.unknown.package"))
        assertNull(LocalActionRouter.propose("Abra https://example.com"))
    }

    @Test fun coordinatorLeavesOrdinaryWritingToTheActualProvider() = runTest {
        val calls = mutableListOf<List<AiMessage>>()
        val provider = object : AiProvider {
            override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String {
                calls += messages
                return "Conteúdo exato fornecido pelo modelo."
            }
        }
        val coordinator = AiCoordinator(provider)
        val writing = listOf(AiMessage("user", "Me faz um texto de desculpa."))
        assertEquals("Conteúdo exato fornecido pelo modelo.", coordinator.complete(NexusSettings(aiMode = br.com.nexus.assistant.data.AiMode.NEXUS_LOCAL), "", writing, emptyList()))
        assertEquals(listOf(writing), calls)
        val command = coordinator.complete(NexusSettings(), "", listOf(AiMessage("user", "NEXUS, abra as configurações.")), emptyList())
        assertEquals("settings", AiCompletion.fromText(command).proposal?.action)
        assertEquals(1, calls.size)
    }

    @Test fun imageQuestionIsNeverTreatedAsAnActionCommand() = runTest {
        var receivedImage = false
        val provider = object : AiProvider {
            override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String {
                receivedImage = messages.last().imagePath != null
                return "Resultado do provedor de imagem."
            }
        }
        val result = AiCoordinator(provider).complete(NexusSettings(aiMode = br.com.nexus.assistant.data.AiMode.NEXUS_LOCAL), "", listOf(AiMessage("user", "Abra as configurações", "image.jpg")), emptyList())
        assertTrue(receivedImage)
        assertEquals("Resultado do provedor de imagem.", result)
    }
}
