package br.com.nexus.assistant

import android.graphics.Bitmap
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.data.local.NexusNativeBridge
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Library/model execution without camera permission. It does not certify real-hand detection. */
@RunWith(AndroidJUnit4::class)
class NativeCapabilitiesSmokeTest {
    @Test fun packagedHandTrackerProcessesABlankVideoFrame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setRunningMode(RunningMode.VIDEO).setNumHands(2).build()
        val tracker = HandLandmarker.createFromOptions(context, options)
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val image = BitmapImageBuilder(bitmap).build()
        try {
            assertTrue(tracker.detectForVideo(image, 1_000L).landmarks().isEmpty())
        } finally {
            image.close()
            bitmap.recycle()
            tracker.close()
        }
    }

    @Test fun localAiJniLoadsOnlyInSupportedProcessAndRejectsMissingWeights() {
        assertEquals(Process.is64Bit(), NexusNativeBridge.supports64BitProcess)
        if (!Process.is64Bit()) {
            assertFalse(NexusNativeBridge.available)
            return
        }
        assertTrue("The packaged 64-bit JNI library must load", NexusNativeBridge.available)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missing = java.io.File(context.cacheDir, "intentionally-absent-model.gguf")
        missing.delete()
        try {
            val handle = NexusNativeBridge.load(missing.absolutePath, 512, 1)
            NexusNativeBridge.close(handle)
            fail("Missing weights must never produce a simulated model")
        } catch (_: IllegalArgumentException) { }
    }
}
