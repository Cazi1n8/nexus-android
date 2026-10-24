package br.com.nexus.assistant

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
            model = ViewModelProvider(compose.activity)[NexusViewModel::class.java]
            reset = model.deleteAllData()
        }
        runBlocking { reset.join() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("EXPLORE O NEXUS").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun galleryAndQuestionWorkWithoutActivatingSensorsOrSendingAnImage() {
        val cameraPermission = ContextCompat.checkSelfPermission(compose.activity, Manifest.permission.CAMERA)
        val microphonePermission = ContextCompat.checkSelfPermission(compose.activity, Manifest.permission.RECORD_AUDIO)

        compose.onNodeWithText("NEXUS Vision").performScrollTo().performClick()
        compose.onNodeWithText("Escolher imagem", substring = true).performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Ler texto no aparelho").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Encerrar câmera").assertDoesNotExist()
        compose.onNodeWithText("Parar escuta", substring = true).assertDoesNotExist()

        val question = "Leia este rótulo, por favor."
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement(question)
        compose.onNodeWithText("Analisar com a IA").performScrollTo().assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText(question)).performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Ler texto no aparelho").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Encerrar câmera").assertDoesNotExist()
        compose.onNodeWithText("Parar escuta", substring = true).assertDoesNotExist()

        assertEquals(cameraPermission, ContextCompat.checkSelfPermission(compose.activity, Manifest.permission.CAMERA))
        assertEquals(microphonePermission, ContextCompat.checkSelfPermission(compose.activity, Manifest.permission.RECORD_AUDIO))
    }
}
