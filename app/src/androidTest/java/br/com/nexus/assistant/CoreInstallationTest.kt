package br.com.nexus.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.device.voice.SharedNexusModel
import br.com.nexus.assistant.device.voice.VoiceSessionRuntime
import br.com.nexus.assistant.extensions.ExtensionPackStatus
import br.com.nexus.assistant.extensions.ExtensionRepository
import br.com.nexus.assistant.gestures.global.GlobalMouseRuntime
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in CORE installation test, including the minified coreTest build.
 * Use a fresh disposable emulator: no permissions are granted, revoked or bypassed here.
 * A blank frame proves packaged tracking execution, not real hands or global gestures.
 */
@RunWith(AndroidJUnit4::class)
class CoreInstallationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 120_000L)
    fun coreKeepsTrackingOfflineAndOpensStorageWithoutStartingOptionalResources() {
        assumeTrue("Dedicated CORE proof requires an installation without optional assets", BuildConfig.CORE_INSTALLATION)
        assumeTrue("Opt in with nexusCoreSmoke=true on a fresh disposable emulator",
            InstrumentationRegistry.getArguments().getString("nexusCoreSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reportFile = File(context.filesDir, "ci-reports/core-installation.json")
        val report = JSONObject().put("schemaVersion", 1).put("executionStatus", "started")
            .put("versionName", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE)
            .put("coreInstallation", BuildConfig.CORE_INSTALLATION)
            .put("scope", "Fresh Android CORE assets, actual MediaPipe blank frame, extensions/storage UI and permission/resource state. Not real camera, hand gestures, voice or Play Protect approval.")
        try {
            val assetPaths = mutableListOf<String>()
            fun visitAssets(parent: String) {
                context.assets.list(parent).orEmpty().forEach { name ->
                    val path = if (parent.isEmpty()) name else "$parent/$name"
                    if (context.assets.list(path).orEmpty().isEmpty()) assetPaths += path else visitAssets(path)
                }
            }
            visitAssets("")
            assertFalse("CORE must not contain large generative model weights", assetPaths.any {
                it.endsWith(".gguf", ignoreCase = true) || it.endsWith(".onnx", ignoreCase = true)
            })
            assertFalse("OCR data must be optional", assetPaths.any { it.endsWith(".traineddata", ignoreCase = true) })
            assertFalse("Object classification data must be optional", "vision/efficientnet_lite0_int8.tflite" in assetPaths)
            assertTrue("The mouse's hand model remains inside the CORE APK", "hand_landmarker.task" in assetPaths)
            report.put("generativeWeightsAbsent", true).put("optionalVisionAssetsAbsent", true)

            val digest = MessageDigest.getInstance("SHA-256")
            var handBytes = 0L
            context.assets.open("hand_landmarker.task").buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    handBytes += count
                    digest.update(buffer, 0, count)
                }
            }
            val handHash = digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            assertEquals(7_819_105L, handBytes)
            assertEquals("fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1", handHash)
            report.put("handModelBytes", handBytes).put("handModelSha256", handHash)

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
                .setRunningMode(RunningMode.VIDEO).setNumHands(1).build()
            val tracker = HandLandmarker.createFromOptions(context, options)
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val image = BitmapImageBuilder(bitmap).build()
            try {
                assertTrue("A blank frame must not invent a hand", tracker.detectForVideo(image, 1_000L).landmarks().isEmpty())
                report.put("realHandTrackerProcessedBlankFrame", true)
            } finally {
                image.close()
                bitmap.recycle()
                tracker.close()
            }

            val sensorPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            sensorPermissions.forEach { permission ->
                assertEquals("Use a fresh emulator without sensor permissions for this proof", PackageManager.PERMISSION_DENIED,
                    ContextCompat.checkSelfPermission(context, permission))
            }
            val declared = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions.orEmpty()
            assertFalse("Storage totals must not require observing other apps", "android.permission.PACKAGE_USAGE_STATS" in declared)

            lateinit var model: NexusViewModel
            compose.runOnUiThread { model = SharedNexusModel.get(context) }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("EXPLORE O NEXUS").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Central de extensões").performScrollTo().performClick()
            compose.onNodeWithText("CENTRAL DE EXTENSÕES").assertIsDisplayed()
            compose.onNodeWithText("NEXUS AI").assertIsDisplayed()
            compose.onNodeWithContentDescription("Voltar").performClick()
            compose.onNodeWithText("Armazenamento").performScrollTo().performClick()
            compose.onNodeWithText("Central de armazenamento").assertIsDisplayed()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Arquivos privados medidos").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Arquivos privados medidos").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Modelos de IA offline", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Cache temporário:", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Gerenciar modelos de IA").performScrollTo().performClick()
            compose.onNodeWithText("MODELOS DE IA OPCIONAIS").assertIsDisplayed()
            report.put("extensionsUiOpened", true).put("storageMeasurementRendered", true).put("manageModelsButtonNavigated", true)
            compose.onNodeWithContentDescription("Voltar").performClick()
            compose.onNodeWithContentDescription("Voltar").performClick()
            compose.onNodeWithText("Mouse virtual").performScrollTo().performClick()
            val cameraSupported = model.devicePolicy.value.handTrackingSupported
            if (cameraSupported) compose.onNodeWithText("Autorizar câmera").assertIsDisplayed()
            else compose.onNodeWithText("Continuar com as funções disponíveis").assertIsDisplayed()
            report.put("mouseScreenWaitsForCameraConsent", cameraSupported)
                .put("mouseScreenReportsUnsupportedHardware", !cameraSupported)

            assertFalse("Opening these screens must not download AI weights", model.localModelDownloadActive.value)
            assertFalse("Opening the extension catalogue must not download data", ExtensionRepository.get(context).states.value
                .values.any { it.status == ExtensionPackStatus.DOWNLOADING })
            assertFalse("The microphone session must remain off", VoiceSessionRuntime.state.value.active)
            assertFalse("The global camera session must remain off", GlobalMouseRuntime.state.value.active)
            sensorPermissions.forEach { permission ->
                assertEquals(PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, permission))
            }
            report.put("automaticModelDownloadStarted", false).put("automaticExtensionDownloadStarted", false)
                .put("sensorPermissionsUnchanged", true).put("backgroundServicesActive", false)
                .put("testedSensorPermissions", JSONArray(sensorPermissions))
                .put("executionStatus", "passed")
        } catch (failure: Throwable) {
            report.put("executionStatus", "failed").put("failureType", failure.javaClass.simpleName)
            throw failure
        } finally {
            val payload = report.toString(2)
            try {
                reportFile.parentFile?.mkdirs()
                reportFile.writeText(payload, Charsets.UTF_8)
            } finally {
                // Test fixtures only. The minified non-debuggable APK cannot
                // expose its private report through adb run-as.
                val encoded = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                    putString("nexus_core_report", encoded)
                })
            }
        }
    }
}
