package br.com.nexus.assistant

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetApplication() {
        lateinit var model: NexusViewModel
        lateinit var reset: Job
        compose.runOnUiThread {
            model = br.com.nexus.assistant.device.voice.SharedNexusModel.get(compose.activity)
            reset = model.deleteAllData()
        }
        runBlocking { reset.join() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("EXPLORE O NEXUS").fetchSemanticsNodes().isNotEmpty()
        }
        // Finish the entrance animation before injecting a touch into a moving card.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test fun galleryAndQuestionWorkWithoutActivatingSensorsOrSendingAnImage() {
        val cameraPermission = ContextCompat.checkSelfPermission(compose.activity, Manifest.permission.CAMERA)
        val microphonePermission = ContextCompat.checkSelfPermission(compose.activity, Manifest.permission.RECORD_AUDIO)

        // Use the real accessibility click action; a touch during scroll settling
        // can be consumed to stop the scroll instead of opening the destination.
        compose.onNode(hasText("NEXUS Vision") and hasClickAction()).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { action -> check(action()) }
        // Navigation completes asynchronously; await the destination, not a fixed delay.
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Escolher imagem", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            compose.onRoot(useUnmergedTree = true).printToLog("NEXUS_VISION_TEST")
            throw failure
        }
        compose.onNodeWithText("Escolher imagem", substring = true).performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Ler texto no aparelho").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Encerrar câmera").assertDoesNotExist()
        compose.onNodeWithText("Parar escuta", substring = true).assertDoesNotExist()

        val question = "Leia este rótulo, por favor."
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement(question)
        compose.onNodeWithText("Levar imagem e pergunta ao Chat").performScrollTo().assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasSetTextAction() and hasText(question)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction() and hasText(question)).performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Ler texto no aparelho").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Encerrar câmera").assertDoesNotExist()
        compose.onNodeWithText("Parar escuta", substring = true).assertDoesNotExist()

        assertEquals(cameraPermission, ContextCompat.checkSelfPermission(compose.activity, Manifest.permission.CAMERA))
        assertEquals(microphonePermission, ContextCompat.checkSelfPermission(compose.activity, Manifest.permission.RECORD_AUDIO))
    }
}
