package br.com.nexus.assistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
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
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[NexusViewModel::class.java] }
        lateinit var reset: kotlinx.coroutines.Job
        compose.runOnUiThread { reset = model.deleteAllData() }
        runBlocking { reset.join() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("EXPLORE O NEXUS").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun sendingWithoutAnAiConnectionKeepsTheDraftAndDoesNotInventAnAnswer() {
        compose.onNodeWithText("Chat IA").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Quanto é 2 + 2?")
        compose.onNodeWithContentDescription("Enviar mensagem").performClick()
        compose.onNode(hasSetTextAction()).assert(hasText("Quanto é 2 + 2?"))
        compose.onNodeWithText("0 mensagens • contexto limitado").assertIsDisplayed()
        compose.onNodeWithText("O que vamos explorar?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Conectar uma IA").performScrollTo().assertIsDisplayed()
    }

    @Test fun anUnsavedSessionTokenIsNotRestoredWhenTheActivityIsRecreated() {
        compose.onNodeWithContentDescription("Configurações").performClick()
        compose.onNodeWithText("Atualizar token de sessão").performScrollTo()
        compose.onNode(isToggleable() and hasAnySibling(hasText("Atualizar token de sessão"))).performClick()
        compose.onNode(hasSetTextAction() and hasText("Token do seu servidor NEXUS")).performTextInput("unsaved-token-for-ui-test")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Token do seu servidor NEXUS").assertDoesNotExist()
        compose.onAllNodesWithText("unsaved-token-for-ui-test", substring = true).assertCountEquals(0)
    }
}
