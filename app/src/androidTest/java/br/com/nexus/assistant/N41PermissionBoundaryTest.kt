package br.com.nexus.assistant

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.nexus.assistant.device.voice.SharedNexusModel
import br.com.nexus.assistant.device.voice.VoiceSessionRuntime
import br.com.nexus.assistant.gestures.global.GlobalMouseRuntime
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inspects the actual installed package and opens the real mouse destination.
 * Requires the ordinary fresh-emulator installation with sensor permissions denied.
 * Does not grant/revoke permissions, connect accessibility, start services or claim
 * physical mouse operation/Play Protect approval.
 */
@RunWith(AndroidJUnit4::class)
class N41PermissionBoundaryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetApplication() {
        lateinit var reset: Job
        compose.runOnUiThread { reset = SharedNexusModel.get(compose.activity).deleteAllData() }
        runBlocking { reset.join() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("EXPLORE O NEXUS").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test fun installedServicesExposeOnlyTheRequiredGestureAndForegroundCapabilities() {
        val context = compose.activity
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_SERVICES or PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)
        val services = installed.services.orEmpty().associateBy { it.name }
        val camera = requireNotNull(services["${context.packageName}.gestures.global.GlobalMouseCameraService"])
        val microphone = requireNotNull(services["${context.packageName}.device.voice.BackgroundVoiceService"])
        for (service in listOf(camera, microphone)) {
            assertFalse("A foreground-control service must not accept external starts", service.exported)
            assertEquals("Leaving the activity task must not itself stop an authorized service", 0,
                service.flags and ServiceInfo.FLAG_STOP_WITH_TASK)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA, camera.foregroundServiceType)
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, microphone.foregroundServiceType)
        }

        val accessibilityName = "${context.packageName}.gestures.global.NexusAccessibilityService"
        val accessibility = requireNotNull(services[accessibilityName])
        assertTrue("The system must be able to bind the explicitly enabled service", accessibility.exported)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, accessibility.permission)
        assertTrue("The installed service must include Android accessibility metadata",
            (accessibility.metaData?.getInt("android.accessibilityservice", 0) ?: 0) != 0)

        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val descriptor = requireNotNull(manager.installedAccessibilityServiceList.firstOrNull {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name == accessibilityName
        }) { "The Android accessibility catalogue did not contain the installed NEXUS service" }
        assertEquals("Gesture control must not request screen, keyboard or screenshot capabilities",
            AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES, descriptor.capabilities)
        assertEquals("No accessibility event collection is necessary for cursor control", 0, descriptor.eventTypes)
        assertEquals("No window-retrieval or key-filtering flags are necessary", 0, descriptor.flags)
        @Suppress("DEPRECATION")
        assertFalse(descriptor.canRetrieveWindowContent)
        if (Build.VERSION.SDK_INT >= 31) assertFalse(descriptor.isAccessibilityTool)

        val declared = installed.requestedPermissions.orEmpty().toSet()
        val forbidden = setOf(
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS",
            "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS",
            "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG",
            "android.permission.READ_PHONE_STATE", "android.permission.READ_PHONE_NUMBERS",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
        )
        assertTrue("The installed manifest unexpectedly requested broad permissions: ${declared.intersect(forbidden)}",
            declared.intersect(forbidden).isEmpty())
    }

    @Test fun openingMouseShowsTheControlsWithoutRequestingSensorsOrStartingASession() {
        val context = compose.activity
        val sensors = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        sensors.forEach { permission ->
            assertEquals("Run this proof on a fresh emulator without sensor grants", PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(context, permission))
        }
        assertFalse(GlobalMouseRuntime.state.value.active)
        assertFalse(VoiceSessionRuntime.state.value.active)

        compose.onNode(hasText("Mouse virtual") and hasClickAction()).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { click -> check(click()) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Mouse Virtual").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Mouse Virtual").assertIsDisplayed()
        compose.onNodeWithText("Autorizo esta sessão de câmera e controle por gestos em outros aplicativos.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ativar mouse interno").performScrollTo().assertExists()
        compose.onNodeWithText("Câmera interna desligada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PARAR mouse").assertDoesNotExist()

        sensors.forEach { permission ->
            assertEquals("Merely entering Mouse must leave sensor authorization unchanged", PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(context, permission))
        }
        assertFalse("The global camera service must wait for explicit activation", GlobalMouseRuntime.state.value.active)
        assertFalse("The microphone session must remain independent and off", VoiceSessionRuntime.state.value.active)
        compose.runOnUiThread {
            assertTrue("No automatic system permission dialog should cover the controls", context.hasWindowFocus())
        }
    }
}
