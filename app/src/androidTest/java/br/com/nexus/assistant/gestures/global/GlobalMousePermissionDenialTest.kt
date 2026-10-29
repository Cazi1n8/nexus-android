package br.com.nexus.assistant.gestures.global

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.BuildConfig
import br.com.nexus.assistant.MainActivity
import br.com.nexus.assistant.device.voice.VoiceSessionRuntime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit opt-in on a disposable CORE emulator only. No permission is granted,
 * no accessibility setting is changed, and no synthetic landmarks enter the app.
 * This exercises a real Service refusal, not positive global-mouse operation.
 */
@RunWith(AndroidJUnit4::class)
class GlobalMousePermissionDenialTest {
    @Test(timeout = 30_000L)
    fun cameraDeniedStartStopsTheRealServiceWithoutNotificationsOrAutomaticRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Enable nexusGlobalBoundarySmoke=true on a fresh disposable CORE emulator",
            BuildConfig.CORE_INSTALLATION &&
                InstrumentationRegistry.getArguments().getString("nexusGlobalBoundarySmoke") == "true")
        val qemu = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("getprop ro.kernel.qemu")
        ).bufferedReader().use { it.readText().trim() }
        assumeTrue("This negative-control proof must never run on a physical/user device", qemu == "1")
        val context = instrumentation.targetContext
        assertEquals("Use a fresh emulator; this test does not revoke an existing permission",
            PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
        assertFalse("Do not replace an existing mouse session", GlobalMouseRuntime.state.value.active)
        assertFalse("Voice is independent and must already be inactive", VoiceSessionRuntime.state.value.active)
        assertFalse("This proof does not enable accessibility", NexusAccessibilityService.connected.value)
        assertFalse("An older service must not still be stopping", serviceRunning(context))
        // Remove only inactive, synthetic runtime metadata so a previous error
        // cannot make the new asynchronous command appear to have completed.
        GlobalMouseRuntime.update(GlobalMouseState())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    // A visible test activity issues the same real service command.
                    // Denied CAMERA must stop it before notification/model/CameraX work.
                    val started = activity.startService(Intent(activity, GlobalMouseCameraService::class.java)
                        .setAction(GlobalMouseCameraService.ACTION_START)
                        .putExtra(GlobalMouseCameraService.EXTRA_START_GENERATION, GlobalMouseRuntime.stopGeneration))
                    assertEquals(GlobalMouseCameraService::class.java.name, started?.className)
                }
                eventually {
                    GlobalMouseRuntime.state.value.error?.contains("permissão da câmera") == true &&
                        !serviceRunning(context)
                }
                assertFalse(GlobalMouseRuntime.state.value.active)
                assertFalse(GlobalMouseRuntime.state.value.cameraReady)
                assertEquals(PackageManager.PERMISSION_DENIED,
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
                assertTrue("A refused start must not create a persistent camera notification",
                    context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == 2001 })
                // A second idle turn checks the actual non-restarting stop state.
                instrumentation.waitForIdleSync()
                assertFalse(serviceRunning(context))
                assertFalse(VoiceSessionRuntime.state.value.active)
                val report = JSONObject().put("schemaVersion", 1).put("executionStatus", "passed")
                    .put("scope", "Fresh emulator, real Service camera-denial path only; not physical hands, global input or Play Protect.")
                    .put("androidApi", Build.VERSION.SDK_INT).put("cameraPermissionRemainedDenied", true)
                    .put("realServiceRejectedDeniedCamera", true).put("serviceStopped", true)
                    .put("persistentCameraNotificationAbsent", true).put("voiceStayedInactive", true)
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("nexus_global_boundary_report", Base64.encodeToString(
                        report.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                })
            } finally {
                context.stopService(Intent(context, GlobalMouseCameraService::class.java))
                // Only this synthetic session's transient metadata is cleared.
                GlobalMouseRuntime.update(GlobalMouseState())
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun serviceRunning(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java).getRunningServices(100)
            .any { it.service.className == GlobalMouseCameraService::class.java.name }

    private fun eventually(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 5_000L
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(10)
        assertTrue("The real Service did not complete its denied-camera stop in 5 seconds", condition())
    }
}
