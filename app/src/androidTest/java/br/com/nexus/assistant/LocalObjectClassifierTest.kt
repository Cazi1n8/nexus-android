package br.com.nexus.assistant

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.device.vision.LocalObjectClassifier
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a pinned official photograph; this is object classification, not full scene understanding. */
@RunWith(AndroidJUnit4::class)
class LocalObjectClassifierTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test(timeout = 60_000L)
    fun realBundledModelRecognizesOfficialFoodImageLocally() = runBlocking<Unit> {
        val photo = File.createTempFile("object-burger-", ".jpg", context.cacheDir)
        try {
            instrumentation.context.assets.open("vision/burger.jpg").use { input -> photo.outputStream().use { input.copyTo(it) } }
            LocalObjectClassifier(context).use { classifier ->
                val observation = classifier.classify(photo)
                assertEquals("The official burger fixture must be recognized by the actual model", "cheeseburger", observation.candidates.firstOrNull()?.label)
                assertTrue("The test photo must produce a useful score", (observation.candidates.firstOrNull()?.score ?: 0f) >= .45f)
                assertTrue(observation.candidates.all { it.score in .20f..1f })
                assertTrue(observation.width > 0 && observation.height > 0)
            }
        } finally { photo.delete() }
    }

    @Test(timeout = 30_000L)
    fun blankFrameDoesNotInventObjectsAndClosedReaderCannotRestart() = runBlocking<Unit> {
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val classifier = LocalObjectClassifier(context)
        try {
            val observation = classifier.classify(bitmap)
            assertTrue(observation.lowDetail)
            assertTrue(observation.candidates.isEmpty())
            classifier.close()
            try { classifier.classify(bitmap); fail("Closed classifier must not start again") }
            catch (_: kotlinx.coroutines.CancellationException) { }
        } finally { classifier.close(); bitmap.recycle() }
    }
}
