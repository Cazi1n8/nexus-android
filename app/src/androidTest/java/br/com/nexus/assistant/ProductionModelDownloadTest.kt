package br.com.nexus.assistant

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.data.local.LocalModelCatalog
import br.com.nexus.assistant.data.local.LocalModelManager
import br.com.nexus.assistant.data.local.LocalModelStatus
import br.com.nexus.assistant.data.local.NexusNativeBridge
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit CI opt-in: Android itself downloads the two public pinned GGUFs with
 * the production manager. No model is pushed through ADB or bundled in the APK.
 * The normal instrumented suite skips this approximately 1.6 GB network test.
 */
@RunWith(AndroidJUnit4::class)
class ProductionModelDownloadTest {
    @Test(timeout = 1_200_000L)
    fun bothProductionModelsDownloadAndCompactCanInfer() = runBlocking<Unit> {
        assumeTrue("Opt-in production download needs nexusDownloadSmoke=true.",
            InstrumentationRegistry.getArguments().getString("nexusDownloadSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "ci-production-downloads")
        val reportFile = File(context.filesDir, "ci-reports/production-download-observations.json")
        val models = JSONArray()
        val report = JSONObject()
            .put("schemaVersion", 1)
            .put("executionStatus", "started")
            .put("androidApi", Build.VERSION.SDK_INT)
            .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("downloadSource", "LocalModelManager.download over Android HTTPS")
            .put("models", models)
            .put("scope", "Real app downloader and compact inference; no physical sensors or general quality certification.")
        fun saveReport() {
            reportFile.parentFile?.mkdirs()
            reportFile.writeText(report.toString(2), Charsets.UTF_8)
        }
        var handle = 0L
        try {
            // Only this test's fixed private directory can be removed; a previous
            // successful fixture must not make a new download appear to succeed.
            check(directory.canonicalFile.parentFile == context.filesDir.canonicalFile)
            check(!directory.exists() || directory.deleteRecursively())
            check(directory.mkdirs())
            report.put("initialFreeBytes", directory.usableSpace)
            saveReport()
            withTimeout(1_140_000L) {
                for ((profile, spec) in listOf("compact" to LocalModelCatalog.compact,
                    "balanced" to LocalModelCatalog.balanced)) {
                    val item = JSONObject().put("profile", profile).put("expectedBytes", spec.bytes)
                        .put("downloadReturned", false)
                    val states = JSONArray()
                    item.put("statesObserved", states)
                    models.put(item)
                    report.put("stage", "download_$profile")
                    saveReport()
                    val manager = LocalModelManager(directory, spec)
                    manager.refresh()
                    assertEquals("A new CI run must begin without an installed fixture", LocalModelStatus.ABSENT, manager.state.value.status)
                    val monitor = launch {
                        var previous: LocalModelStatus? = null
                        manager.state.collect { state ->
                            if (state.status != previous) {
                                states.put(state.status.name)
                                previous = state.status
                            }
                            item.put("lastDownloadedBytes", state.downloadedBytes)
                                .put("lastVerifiedBytes", state.verifiedBytes)
                                .put("terminalState", state.status.name)
                        }
                    }
                    try {
                        val started = SystemClock.elapsedRealtime()
                        // Calling this suspend function through its normal return
                        // proves that neither EOF nor a watcher keeps it at 100%.
                        withTimeout(540_000L) { manager.download() }
                        item.put("downloadMilliseconds", SystemClock.elapsedRealtime() - started)
                            .put("downloadReturned", true)
                            .put("terminalState", manager.state.value.status.name)
                        assertEquals(LocalModelStatus.READY, manager.state.value.status)
                        assertEquals(spec.bytes, manager.state.value.downloadedBytes)
                        assertEquals(spec.bytes, manager.state.value.verifiedBytes)
                        val file = manager.requireReadyFile()
                        assertEquals(spec.bytes, file.length())
                        val digest = MessageDigest.getInstance("SHA-256")
                        file.inputStream().buffered().use { input ->
                            val buffer = ByteArray(1024 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                digest.update(buffer, 0, count)
                            }
                        }
                        val actualHash = digest.digest().joinToString("") {
                            (it.toInt() and 255).toString(16).padStart(2, '0')
                        }
                        item.put("bytes", file.length()).put("sha256", actualHash)
                        assertEquals(spec.sha256, actualHash)
                        assertFalse(File(directory, "${spec.fileName}.part").exists())
                        manager.refresh()
                        item.put("refreshState", manager.state.value.status.name)
                        assertEquals(LocalModelStatus.READY, manager.state.value.status)
                        saveReport()
                        if (profile == "compact") {
                            report.put("stage", "compact_inference")
                            assertTrue(NexusNativeBridge.supports64BitProcess)
                            assertTrue(NexusNativeBridge.available)
                            handle = NexusNativeBridge.load(file.absolutePath, nCtx = 512, threads = 1)
                            assertTrue(handle != 0L)
                            val messages = JSONArray()
                                .put(JSONObject().put("role", "system").put("content", "Você é NEXUS. Seja breve."))
                                .put(JSONObject().put("role", "user").put("content", "Quanto é 17 mais 25? Responda só com o número."))
                            val inferenceStarted = SystemClock.elapsedRealtime()
                            val bytes = NexusNativeBridge.generate(handle, messages.toString(), 16)
                            val answer = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().trim()
                            assertTrue("The downloaded model must actually generate text", answer.isNotBlank())
                            report.put("inference", JSONObject().put("answer", answer)
                                .put("milliseconds", SystemClock.elapsedRealtime() - inferenceStarted)
                                .put("arithmeticMatchedExpected42", answer.trimEnd('.', '!') == "42"))
                            // Preserve the generated answer even if a small model
                            // makes a content error; transport success is separate.
                            NexusNativeBridge.close(handle)
                            handle = 0L
                            saveReport()
                        }
                    } finally {
                        monitor.cancelAndJoin()
                        item.put("terminalState", manager.state.value.status.name)
                            .put("lastDownloadedBytes", manager.state.value.downloadedBytes)
                            .put("lastVerifiedBytes", manager.state.value.verifiedBytes)
                        saveReport()
                    }
                }
            }
            report.put("stage", "complete").put("executionStatus", "passed")
        } catch (failure: Throwable) {
            report.put("executionStatus", "failed").put("failureType", failure.javaClass.simpleName)
            throw failure
        } finally {
            if (handle != 0L) NexusNativeBridge.close(handle)
            saveReport()
            // The runner collects evidence then removes only ci-production-downloads.
        }
    }
}
