package br.com.nexus.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.data.AiMode
import br.com.nexus.assistant.data.NexusDatabase
import br.com.nexus.assistant.data.NexusSettings
import br.com.nexus.assistant.data.SettingsRepository
import br.com.nexus.assistant.data.local.LocalModelCatalog
import br.com.nexus.assistant.data.local.LocalModelProfile
import br.com.nexus.assistant.data.local.LocalModelSource
import br.com.nexus.assistant.data.local.LocalModelStatus
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in end-to-end proof: no fixture transfer and no direct native/model-manager call. */
@RunWith(AndroidJUnit4::class)
class BundledChatFirstRunTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 600_000L)
    fun firstChatPreparesBundledModelAndRespondsOffline() {
        assumeTrue("Requires the full APK and an explicitly authorized disposable-emulator run.",
            BuildConfig.BUNDLED_MODEL && InstrumentationRegistry.getArguments().getString("nexusBundledChatSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reportFile = File(context.filesDir, "ci-reports/bundled-chat-observations.json")
        val report = JSONObject().put("schemaVersion", 1).put("executionStatus", "started")
            .put("bundledModel", BuildConfig.BUNDLED_MODEL)
            .put("versionName", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE)
            .put("androidApi", Build.VERSION.SDK_INT)
            .put("scope", "Actual Chat UI, ViewModel asset preparation, local generation and Room history; no camera or microphone proof.")
        try {
            lateinit var model: NexusViewModel
            compose.runOnUiThread { model = ViewModelProvider(compose.activity)[NexusViewModel::class.java] }
            lateinit var reset: Job
            compose.runOnUiThread { reset = model.deleteAllData() }
            runBlocking { reset.join() }
            // An old manually selected heavier profile must not change the base bundled model.
            runBlocking { SettingsRepository(context).save(NexusSettings(
                aiMode = AiMode.NEXUS_LOCAL, automaticProfile = false, localModelProfile = LocalModelProfile.BALANCED)) }
            compose.waitUntil(10_000) { !model.settings.value.automaticProfile }
            assertTrue("The emulator must satisfy the real app memory/ABI policy", model.devicePolicy.value.localAiSupported)
            assertEquals(LocalModelProfile.COMPACT, model.effectiveLocalProfile.value)
            val before = network(context)
            report.put("networkAvailableBefore", before.first).put("activeNetworkPresentBefore", before.second)
            assertFalse("The proof requires internet disabled before opening Chat", before.first)
            val spec = LocalModelCatalog.compact
            val privateModel = File(context.filesDir, "models/${spec.fileName}")
            assertFalse("Reset must remove any previously prepared private model", privateModel.exists())
            val preparationStarted = SystemClock.elapsedRealtime()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("EXPLORE O NEXUS").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Chat IA").performScrollTo().performClick()
            compose.waitUntil(300_000) {
                model.localModelState.value.status == LocalModelStatus.READY && !model.localModelDownloadActive.value
            }
            report.put("preparationMilliseconds", SystemClock.elapsedRealtime() - preparationStarted)
                .put("modelPreparedByViewModel", true)
            assertEquals(LocalModelSource.BUNDLED, model.localModelState.value.source)
            assertEquals(spec.bytes, privateModel.length())
            val actualSha = sha256(privateModel)
            assertEquals(spec.sha256, actualSha)
            report.put("modelBytes", privateModel.length()).put("modelSha256", actualSha)
                .put("provider", model.settings.value.aiMode.name).put("modelProfile", model.effectiveLocalProfile.value.name)
                .put("modelSource", model.localModelState.value.source.name)
            // Deliberately non-arithmetic: this must reach the language model, not the calculator tool.
            val prompt = "Responda somente com a palavra azul."
            compose.onNode(hasSetTextAction()).performTextInput(prompt)
            val responseStarted = SystemClock.elapsedRealtime()
            compose.onNodeWithContentDescription("Enviar mensagem").performClick()
            compose.waitUntil(240_000) { !model.busy.value && model.messages.value.any { it.role == "assistant" } }
            val reply = model.messages.value.last { it.role == "assistant" }
            assertTrue("The local model must generate a nonempty real answer", reply.content.isNotBlank())
            val snapshot = runBlocking { NexusDatabase.getInstance(context).dao().allMessages() }
            assertTrue("The actual ViewModel response must be persisted in Room", snapshot.any { it.id == reply.id && it.content == reply.content })
            report.put("persistedAssistantMessage", true).put("reply", JSONObject()
                .put("answer", reply.content).put("milliseconds", SystemClock.elapsedRealtime() - responseStarted)
                .put("matchedExpectedWord", reply.content.trim().trimEnd('.', '!').equals("azul", ignoreCase = true)))

            val beforeMath = model.messages.value.count { it.role == "assistant" }
            compose.onNode(hasSetTextAction()).performTextInput("10+10÷10×10")
            compose.onNodeWithContentDescription("Enviar mensagem").performClick()
            compose.waitUntil(20_000) {
                !model.busy.value && model.messages.value.count { it.role == "assistant" } > beforeMath
            }
            val mathAnswer = model.messages.value.last { it.role == "assistant" }.content
            assertEquals("The actual Chat route must use correct arithmetic precedence", "20",
                mathAnswer.lineSequence().first().substringAfterLast('=').trim())
            report.put("mathCorrect", true).put("mathAnswer", mathAnswer)

            // A synthetic image exercises actual attachment import and packaged offline OCR.
            // It does not represent or claim general image/scene understanding.
            val syntheticImage = File(context.cacheDir, "bundled-chat-ocr-fixture.jpg")
            val bitmap = Bitmap.createBitmap(640, 200, Bitmap.Config.ARGB_8888)
            try {
                Canvas(bitmap).apply {
                    drawColor(Color.WHITE)
                    drawText("OLA NEXUS 2026", 20f, 120f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK; textSize = 60f; typeface = Typeface.DEFAULT_BOLD
                    })
                }
                syntheticImage.outputStream().use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
                }
            } finally { bitmap.recycle() }
            compose.runOnUiThread { model.importCapturedImage(syntheticImage) }
            compose.waitUntil(20_000) { !model.importing.value && model.attachment.value != null }
            val beforeImage = model.messages.value.count { it.role == "assistant" }
            compose.onNode(hasSetTextAction()).performTextInput("Leia esse texto")
            compose.onNodeWithContentDescription("Enviar mensagem").performClick()
            compose.waitUntil(60_000) {
                !model.busy.value && model.messages.value.count { it.role == "assistant" } > beforeImage
            }
            val imageAnswer = model.messages.value.last { it.role == "assistant" }.content
            assertTrue("The local Chat must return a real OCR transcription", imageAnswer.contains("OCR"))
            assertTrue("The packaged OCR must read the synthetic image", imageAnswer.contains("NEXUS", ignoreCase = true))
            val afterImage = runBlocking { NexusDatabase.getInstance(context).dao().allMessages() }
            assertTrue("The imported image must belong to the persisted user message",
                afterImage.any { it.role == "user" && it.content == "Leia esse texto" && it.imagePath != null })
            report.put("imageTextReadLocally", true).put("imageTextAnswer", imageAnswer)

            compose.activityRule.scenario.recreate()
            compose.runOnUiThread { model = ViewModelProvider(compose.activity)[NexusViewModel::class.java] }
            compose.waitUntil(10_000) { !model.settings.value.automaticProfile }
            assertEquals(AiMode.NEXUS_LOCAL, model.settings.value.aiMode)
            assertEquals(LocalModelProfile.COMPACT, model.effectiveLocalProfile.value)
            assertEquals(spec.bytes, privateModel.length())
            report.put("profileStableAfterRecreation", true)
            val after = network(context)
            report.put("networkAvailableAfter", after.first).put("activeNetworkPresentAfter", after.second)
            assertFalse("The proof requires internet to remain disabled", after.first)
            report.put("executionStatus", "passed").put("semanticAssessment", "Generated answer retained; this does not certify general model intelligence.")
        } catch (failure: Throwable) {
            report.put("executionStatus", "failed").put("failureType", failure.javaClass.simpleName)
            throw failure
        } finally {
            reportFile.parentFile?.mkdirs()
            reportFile.writeText(report.toString(2), Charsets.UTF_8)
        }
    }

    /** Validated internet and active network presence are recorded separately. */
    private fun network(context: Context): Pair<Boolean, Boolean> {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = connectivity.activeNetwork
        val caps = connectivity.getNetworkCapabilities(active)
        return (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) to (active != null)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
}
