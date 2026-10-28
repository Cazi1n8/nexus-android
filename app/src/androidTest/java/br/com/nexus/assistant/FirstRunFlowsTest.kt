package br.com.nexus.assistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunFlowsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetApplication() {
        lateinit var model: NexusViewModel
        compose.runOnUiThread { model = br.com.nexus.assistant.device.voice.SharedNexusModel.get(compose.activity) }
        lateinit var reset: kotlinx.coroutines.Job
        compose.runOnUiThread { reset = model.deleteAllData() }
        runBlocking { reset.join() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("EXPLORE O NEXUS").fetchSemanticsNodes().isNotEmpty() }
        // Finish the entrance animation before selecting a card in the scroll.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test fun sendingWithoutALocalModelKeepsTheDraftAndDoesNotInventAnAnswer() {
        // A touch can merely stop a settling scroll. Invoke the actual card's
        // accessibility action, as in the Vision and bundled-chat UI proofs.
        compose.onNode(hasText("Chat IA") and hasClickAction()).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { action -> check(action()) }
        // Navigation may still be composing the incoming destination after the
        // Home tile click. Wait for the actual editor/send semantics, rather
        // than sleeping or assuming that the click completed the transition.
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1 &&
                compose.onAllNodesWithContentDescription("Enviar mensagem").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("O que vamos explorar?").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Quanto é 2 + 2?")
        compose.onNodeWithContentDescription("Enviar mensagem").performClick()
        compose.onNode(hasSetTextAction()).assert(hasText("Quanto é 2 + 2?"))
        compose.onNodeWithText("0 mensagens salvas • contexto por tokens").assertIsDisplayed()
        compose.onNodeWithText("O que vamos explorar?").assertIsDisplayed()
        // CORE guides the user to an explicit, optional model download. The
        // failed send must not manufacture an assistant answer or start one.
        compose.onNodeWithText("Gerenciar modelo offline opcional").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { action -> check(action()) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("MODELOS DE IA OPCIONAIS").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("MODELOS DE IA OPCIONAIS").assertIsDisplayed()
    }

    @Test fun anUnsavedSessionTokenIsNotRestoredWhenTheActivityIsRecreated() {
        compose.onNodeWithContentDescription("Configurações").performClick()
        compose.onNodeWithText("NEXUS AI · local", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Provedor personalizado").performClick()
        compose.onNodeWithText("Atualizar token de sessão").performScrollTo()
        compose.onNodeWithContentDescription("Atualizar token de sessão").performClick()
        compose.onNode(hasSetTextAction() and hasText("Token do seu servidor NEXUS")).performTextInput("unsaved-token-for-ui-test")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Token do seu servidor NEXUS").assertDoesNotExist()
        compose.onAllNodesWithText("unsaved-token-for-ui-test", substring = true).assertCountEquals(0)
    }
}
