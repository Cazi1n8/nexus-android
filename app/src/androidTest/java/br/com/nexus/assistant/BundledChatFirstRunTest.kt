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
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.data.AiMode
import br.com.nexus.assistant.data.MessageEntity
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
import org.json.JSONArray
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
        var activeModel: NexusViewModel? = null
        try {
            report.put("stage", "reset_application")
            lateinit var model: NexusViewModel
            compose.runOnUiThread { model = br.com.nexus.assistant.device.voice.SharedNexusModel.get(compose.activity) }
            activeModel = model
            lateinit var reset: Job
            compose.runOnUiThread { reset = model.deleteAllData() }
            runBlocking { reset.join() }
            // An old manually selected heavier profile must not change the base bundled model.
            report.put("stage", "restore_manual_profile_preference")
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
            report.put("stage", "open_chat_destination")
            compose.waitUntil(10_000) { compose.onAllNodesWithText("EXPLORE O NEXUS").fetchSemanticsNodes().isNotEmpty() }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            // A touch can be consumed to settle a scroll. Invoke the real
            // accessibility click action, then prove navigation completed.
            compose.onNode(hasText("Chat IA") and hasClickAction()).performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick) { action -> check(action()) }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Enviar mensagem").fetchSemanticsNodes().isNotEmpty()
            }
            report.put("stage", "prepare_bundled_model")
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
            report.put("stage", "generate_native_reply")
            val prompt = "Responda somente com a palavra azul."
            compose.onNode(hasSetTextAction()).performTextInput(prompt)
            val responseStarted = SystemClock.elapsedRealtime()
            val reply = sendAndAwaitAssistant(model, report, "native_reply", prompt, 240_000)
            assertTrue("The local model must generate a nonempty real answer", reply.content.isNotBlank())
            val snapshot = runBlocking { NexusDatabase.getInstance(context).dao().allMessages() }
            assertTrue("The actual ViewModel response must be persisted in Room", snapshot.any { it.id == reply.id && it.content == reply.content })
            report.put("persistedAssistantMessage", true).put("reply", JSONObject()
                .put("answer", reply.content).put("milliseconds", SystemClock.elapsedRealtime() - responseStarted)
                .put("matchedExpectedWord", reply.content.trim().trimEnd('.', '!').equals("azul", ignoreCase = true)))

            report.put("stage", "calculate_in_chat")
            compose.onNode(hasSetTextAction()).performTextInput("10+10÷10×10")
            val mathAnswer = sendAndAwaitAssistant(model, report, "arithmetic", "10+10÷10×10", 20_000).content
            assertEquals("The actual Chat route must use correct arithmetic precedence", "20",
                mathAnswer.lineSequence().first().substringAfterLast('=').trim())
            report.put("mathCorrect", true).put("mathAnswer", mathAnswer)

            // A synthetic image exercises actual attachment import and packaged offline OCR.
            // It does not represent or claim general image/scene understanding.
            report.put("stage", "import_synthetic_image")
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
            report.put("stage", "read_image_text_in_chat")
            compose.onNode(hasSetTextAction()).performTextInput("Leia esse texto")
            val imageAnswer = sendAndAwaitAssistant(model, report, "image_ocr", "Leia esse texto", 60_000).content
            assertTrue("The local Chat must return a real OCR transcription", imageAnswer.contains("OCR"))
            assertTrue("The packaged OCR must read the synthetic image", imageAnswer.contains("NEXUS", ignoreCase = true))
            val afterImage = runBlocking { NexusDatabase.getInstance(context).dao().allMessages() }
            assertTrue("The imported image must belong to the persisted user message",
                afterImage.any { it.role == "user" && it.content == "Leia esse texto" && it.imagePath != null })
            report.put("imageTextReadLocally", true).put("imageTextAnswer", imageAnswer)

            report.put("stage", "recreate_activity")
            compose.activityRule.scenario.recreate()
            compose.runOnUiThread { model = br.com.nexus.assistant.device.voice.SharedNexusModel.get(compose.activity) }
            activeModel = model
            compose.waitUntil(10_000) { !model.settings.value.automaticProfile }
            assertEquals(AiMode.NEXUS_LOCAL, model.settings.value.aiMode)
            assertEquals(LocalModelProfile.COMPACT, model.effectiveLocalProfile.value)
            assertEquals(spec.bytes, privateModel.length())
            report.put("profileStableAfterRecreation", true)
            val after = network(context)
            report.put("networkAvailableAfter", after.first).put("activeNetworkPresentAfter", after.second)
            assertFalse("The proof requires internet to remain disabled", after.first)
            report.put("stage", "completed").put("executionStatus", "passed")
                .put("semanticAssessment", "Generated answer retained; this does not certify general model intelligence.")
        } catch (failure: Throwable) {
            report.put("executionStatus", "failed").put("failureType", failure.javaClass.simpleName)
                .put("lastModelStatus", activeModel?.localModelState?.value?.status?.name ?: JSONObject.NULL)
                .put("lastModelBytes", activeModel?.localModelState?.value?.downloadedBytes ?: JSONObject.NULL)
                .put("lastModelMessage", activeModel?.localModelState?.value?.message ?: JSONObject.NULL)
                .put("modelPreparationActive", activeModel?.localModelDownloadActive?.value ?: JSONObject.NULL)
                .put("viewModelError", activeModel?.error?.value ?: JSONObject.NULL)
                .put("lastBusy", activeModel?.busy?.value ?: JSONObject.NULL)
                .put("lastSelectedConversation", activeModel?.selected?.value ?: JSONObject.NULL)
                .put("lastUserMessageCount", activeModel?.messages?.value?.count { it.role == "user" } ?: JSONObject.NULL)
                .put("lastAssistantMessageCount", activeModel?.messages?.value?.count { it.role == "assistant" } ?: JSONObject.NULL)
            runCatching {
                val tree = compose.onRoot(useUnmergedTree = true).printToString().take(24_000)
                report.put("semanticsOnFailure", tree)
                // sendStatus reaches the saved `am instrument` output, unlike logcat-only diagnostics.
                InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                    putString("nexusBundledFailureStage", report.optString("stage"))
                    putString("nexusBundledFailureSemantics", tree)
                })
            }
            throw failure
        } finally {
            reportFile.parentFile?.mkdirs()
            reportFile.writeText(report.toString(2), Charsets.UTF_8)
        }
    }

    /** Use the UI action, prove acceptance, and stop promptly when the ViewModel reports failure. */
    private fun sendAndAwaitAssistant(
        model: NexusViewModel,
        report: JSONObject,
        name: String,
        expectedPrompt: String,
        timeoutMillis: Long,
    ): MessageEntity {
        val beforeUsers = model.messages.value.count { it.role == "user" }
        val beforeAssistants = model.messages.value.count { it.role == "assistant" }
        val observation = JSONObject().put("name", name)
        val observations = report.optJSONArray("sendObservations") ?: JSONArray().also { report.put("sendObservations", it) }
        observations.put(observation)
        compose.onNode(hasSetTextAction() and hasText(expectedPrompt)).assertExists()
        compose.onNodeWithContentDescription("Enviar mensagem").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { action -> check(action()) }
        fun draftAccepted(): Boolean = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
            .fetchSemanticsNodes().singleOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text?.isBlank() == true
        try {
            compose.waitUntil(10_000) {
                model.busy.value || model.error.value != null ||
                    model.messages.value.count { it.role == "user" } > beforeUsers || draftAccepted()
            }
            observation.put("acceptanceObserved", true).put("draftCleared", draftAccepted())
                .put("busyAfterSend", model.busy.value)
                .put("hasNewUserMessage", model.messages.value.count { it.role == "user" } > beforeUsers)
                .put("hasUserMessage", model.messages.value.any { it.role == "user" })
                .put("selectedConversationAfterSend", model.selected.value ?: JSONObject.NULL)
                .put("errorAfterSend", model.error.value ?: JSONObject.NULL)
            assertNull("Chat rejected $name: ${model.error.value}", model.error.value)
            compose.waitUntil(timeoutMillis) { model.error.value != null || !model.busy.value }
            assertNull("Chat failed $name: ${model.error.value}", model.error.value)
            // Room's observer may publish just after busy clears. A stopped or
            // rejected response must not consume the full native-generation timeout.
            compose.waitUntil(5_000) { model.messages.value.count { it.role == "assistant" } > beforeAssistants }
            observation.put("assistantPersisted", true)
            return model.messages.value.last { it.role == "assistant" }
        } finally {
            observation.put("busyAtExit", model.busy.value)
                .put("selectedConversationAtExit", model.selected.value ?: JSONObject.NULL)
                .put("userMessageCountAtExit", model.messages.value.count { it.role == "user" })
                .put("assistantMessageCountAtExit", model.messages.value.count { it.role == "assistant" })
                .put("errorAtExit", model.error.value ?: JSONObject.NULL)
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
