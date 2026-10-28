package br.com.nexus.assistant

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.device.vision.LocalObjectClassifier
import br.com.nexus.assistant.device.vision.LocalTextReader
import br.com.nexus.assistant.extensions.ExtensionCatalog
import br.com.nexus.assistant.extensions.ExtensionDataFiles
import br.com.nexus.assistant.extensions.ExtensionPackId
import br.com.nexus.assistant.extensions.ExtensionPackStatus
import br.com.nexus.assistant.extensions.ExtensionRepository
import br.com.nexus.assistant.extensions.ExtensionUnavailableException
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit network test for a fresh disposable CORE emulator. Downloads data from
 * the real pinned public sources; never loads downloaded executable code.
 * The deterministic Range exercise seeds a partial from a previously verified
 * download. It does not claim that a live user cancellation was exercised.
 */
@RunWith(AndroidJUnit4::class)
class ExtensionDataSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test(timeout = 540_000L)
    fun optionalDataDownloadsRunNativeReadersAndCanBeRemovedWithoutAffectingCore() = runBlocking<Unit> {
        assumeTrue("The extension proof requires the CORE build", BuildConfig.CORE_INSTALLATION)
        assumeTrue("Explicit network opt-in is required", InstrumentationRegistry.getArguments()
            .getString("nexusExtensionSmoke") == "true")
        val emulator = shell("getprop ro.kernel.qemu").trim() == "1" ||
            shell("getprop ro.boot.qemu").trim() == "1"
        assertTrue("Never run this destructive fixture test on a physical device", emulator)

        val report = JSONObject().put("schemaVersion", 1).put("executionStatus", "started")
            .put("versionName", BuildConfig.VERSION_NAME)
            .put("scope", "Fresh emulator; real HTTPS pinned data, private-file native classifier/OCR, verified-prefix HTTP resume and removal. Not live cancellation, real camera, general image understanding or Play Protect approval.")
        val repository = ExtensionRepository.get(context)
        val files = ExtensionCatalog.packs.flatMap { it.files }
        val marker = File(context.filesDir, "ci-extension-smoke-user-marker.txt")
        var cleanupAllowed = false
        var classifier: LocalObjectClassifier? = null
        var reader: LocalTextReader? = null
        var photo: File? = null
        var textImage: File? = null
        try {
            // Existing CORE preferences/Room are allowed. Optional data, partials
            // and our marker must be absent, so no installed user pack is erased.
            files.forEach { spec ->
                val file = ExtensionDataFiles.destination(context.filesDir, spec)
                assertFalse("Use a fresh emulator without optional data", file.exists())
                assertFalse("Use a fresh emulator without partial downloads", File(file.parentFile, "${file.name}.part").exists())
                assertTrue("Optional data must not remain bundled in CORE",
                    runCatching { context.assets.open(spec.assetPath).use { } }.isFailure)
            }
            assertFalse("Use a fresh emulator without our synthetic marker", marker.exists())
            cleanupAllowed = true
            marker.writeText("Synthetic user content retained while optional packs are removed.")
            val handBefore = handHash()
            repository.refresh()
            ExtensionCatalog.packs.forEach { pack ->
                assertEquals(ExtensionPackStatus.ABSENT, repository.states.value.getValue(pack.id).status)
                withTimeout(180_000L) { repository.download(pack.id) }
                waitReady(repository, pack.id)
            }
            files.forEach { spec ->
                assertTrue("Installed optional data must match its pinned size/SHA-256",
                    ExtensionDataFiles.verified(ExtensionDataFiles.destination(context.filesDir, spec), spec))
            }
            report.put("optionalDataBytes", ExtensionCatalog.packs.sumOf { it.bytes })
                .put("realHttpsDownloadsVerified", true)

            val beforeReuse = files.associate { spec ->
                val file = ExtensionDataFiles.destination(context.filesDir, spec)
                file.absolutePath to (file.lastModified() to file.length())
            }
            repository.refresh()
            ExtensionCatalog.packs.forEach { repository.download(it.id); waitReady(repository, it.id) }
            beforeReuse.forEach { (path, metadata) ->
                assertEquals("A valid installed file should be reused", metadata, File(path).lastModified() to File(path).length())
            }
            report.put("verifiedFilesReused", true)

            // A real reader must retain the verified pack until its native model
            // is released. The explicit lease also tests the public guard.
            repository.acquire(ExtensionPackId.VISION_OBJECTS).use {
                try { repository.remove(ExtensionPackId.VISION_OBJECTS); fail("Active users must block deletion") }
                catch (_: ExtensionUnavailableException) { }
            }
            assertTrue(repository.verifiedPrivateFile(ExtensionCatalog.objects.files.single()) != null)
            report.put("activeLeaseBlockedRemoval", true)

            val burger = File.createTempFile("extension-burger-", ".jpg", context.cacheDir)
            photo = burger
            instrumentation.context.assets.open("vision/burger.jpg").use { input ->
                burger.outputStream().use { output -> input.copyTo(output) }
            }
            val objectReader = LocalObjectClassifier(context)
            classifier = objectReader
            val observation = withTimeout(60_000L) { objectReader.classify(burger) }
            assertEquals("cheeseburger", observation.candidates.firstOrNull()?.label)
            assertTrue((observation.candidates.firstOrNull()?.score ?: 0f) >= .45f)
            try { repository.remove(ExtensionPackId.VISION_OBJECTS); fail("The real native classifier must retain its model lease") }
            catch (_: ExtensionUnavailableException) { }
            report.put("privateClassifierProcessedFixture", true)
            objectReader.close()
            classifier = null

            val printed = createTextImage()
            textImage = printed
            val textReader = LocalTextReader(context)
            reader = textReader
            val text = withTimeout(60_000L) { textReader.read(printed) }
            assertTrue("Actual private OCR models must read the printed fixture", text.text.contains("NEXUS", true))
            assertTrue(text.text.contains("42"))
            assertTrue(text.blocks.isNotEmpty())
            report.put("privateOcrReadSyntheticText", true)
            textReader.close()
            reader = null

            val objectSpec = ExtensionCatalog.objects.files.single()
            val objectFile = ExtensionDataFiles.destination(context.filesDir, objectSpec)
            val prefix = objectFile.inputStream().use { input -> ByteArray(128 * 1024).also { bytes ->
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(bytes, offset, bytes.size - offset)
                    check(count > 0)
                    offset += count
                }
            } }
            removeAfterReaderClose(repository, ExtensionPackId.VISION_OBJECTS)
            File(objectFile.parentFile, "${objectFile.name}.part").writeBytes(prefix)
            repository.refresh()
            assertEquals(ExtensionPackStatus.PAUSED, repository.states.value.getValue(ExtensionPackId.VISION_OBJECTS).status)
            withTimeout(180_000L) { repository.download(ExtensionPackId.VISION_OBJECTS) }
            waitReady(repository, ExtensionPackId.VISION_OBJECTS)
            assertTrue(ExtensionDataFiles.verified(objectFile, objectSpec))
            assertFalse(File(objectFile.parentFile, "${objectFile.name}.part").exists())
            report.put("realHttpsPartialResumeVerified", true)
                .put("resumeFixture", "128 KiB prefix copied from the previously SHA-256-verified real download; HTTPS continuation validates the complete pinned file. A server may legally restart with HTTP 200; this fixture does not assert the status code.")

            ExtensionCatalog.packs.forEach { removeAfterReaderClose(repository, it.id) }
            repository.refresh()
            files.forEach { spec ->
                val file = ExtensionDataFiles.destination(context.filesDir, spec)
                assertFalse(file.exists())
                assertFalse(File(file.parentFile, "${file.name}.part").exists())
            }
            assertEquals(handBefore, handHash())
            assertEquals("Synthetic user content retained while optional packs are removed.", marker.readText())
            report.put("optionalFilesRemoved", true).put("handModelPreserved", true)
                .put("syntheticUserDataPreserved", true).put("executionStatus", "passed")
        } catch (failure: Throwable) {
            report.put("executionStatus", "failed").put("failureType", failure.javaClass.simpleName)
            throw failure
        } finally {
            classifier?.close()
            reader?.close()
            photo?.delete()
            textImage?.delete()
            if (cleanupAllowed) {
                ExtensionCatalog.packs.forEach { pack -> runCatching { removeAfterReaderClose(repository, pack.id) } }
                marker.delete()
            }
            val payload = report.toString(2)
            try {
                File(context.filesDir, "ci-reports/extension-data.json").apply {
                    parentFile?.mkdirs(); writeText(payload, Charsets.UTF_8)
                }
            } finally {
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("nexus_extension_report", Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                })
            }
        }
    }

    private suspend fun waitReady(repository: ExtensionRepository, id: ExtensionPackId) = withTimeout(5_000L) {
        while (repository.states.value.getValue(id).status != ExtensionPackStatus.READY) delay(25)
    }

    private suspend fun removeAfterReaderClose(repository: ExtensionRepository, id: ExtensionPackId) = withTimeout(5_000L) {
        while (true) {
            try { repository.remove(id); break }
            catch (_: ExtensionUnavailableException) { delay(25) }
        }
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private fun handHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open("hand_landmarker.task").use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    private fun createTextImage(): File {
        val bitmap = Bitmap.createBitmap(1400, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 96f; typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        canvas.drawText("NEXUS 42", 100f, 170f, paint)
        paint.textSize = 64f
        canvas.drawText("Leitura local de texto", 100f, 300f, paint)
        val file = File.createTempFile("extension-ocr-", ".png", context.cacheDir)
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        return file
    }
}
