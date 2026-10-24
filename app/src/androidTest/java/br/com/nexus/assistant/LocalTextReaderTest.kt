package br.com.nexus.assistant

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.device.vision.LocalTextException
import br.com.nexus.assistant.device.vision.LocalTextReader
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Executes bundled Tesseract and models. No camera, microphone, remote OCR or prewritten result. */
@RunWith(AndroidJUnit4::class)
class LocalTextReaderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 120_000L)
    fun bundledPortugueseAndEnglishModelsReadPrintedText() = runBlocking {
        val file = createTextImage()
        val reader = LocalTextReader(context)
        try {
            val result = reader.read(file)
            val normalized = result.text.uppercase()
            assertTrue("Expected NEXUS in real OCR output: ${result.text}", normalized.contains("NEXUS"))
            assertTrue("Expected the printed number in real OCR output: ${result.text}", normalized.contains("42"))
            assertEquals(file.absolutePath, result.sourcePath)
            assertEquals(1400, result.processedWidth)
            assertEquals(500, result.processedHeight)
            assertTrue(result.elapsedMs >= 0L)
            assertFalse(result.truncated)
            assertTrue("Native layout data must accompany recognized text", result.blocks.isNotEmpty())
            assertTrue(result.blocks.any { it.bounds != null })
        } finally {
            reader.close()
            file.delete()
        }
    }

    @Test(timeout = 120_000L)
    fun boundedDecodeHonorsExifRotationAndBlankImageReturnsNoText() = runBlocking {
        val file = File.createTempFile("ocr-rotated-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(2600, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
        finally { bitmap.recycle() }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val reader = LocalTextReader(context)
        try {
            val result = reader.read(file)
            assertEquals(600, result.processedWidth)
            assertEquals(1300, result.processedHeight)
            assertTrue("A blank photo must not produce fabricated text", result.text.isBlank())
            assertTrue(result.blocks.isEmpty())
        } finally {
            reader.close()
            file.delete()
        }
    }

    @Test(timeout = 30_000L)
    fun rejectsOutsidePrivateFilesAndOversizedInput() = runBlocking {
        val reader = LocalTextReader(context)
        val oversized = File.createTempFile("ocr-too-large-", ".png", context.cacheDir)
        try {
            RandomAccessFile(oversized, "rw").use { it.setLength(20L * 1024 * 1024 + 1) }
            try {
                reader.read(oversized)
                fail("Oversized private images must be rejected before decoding")
            } catch (failure: LocalTextException) {
                assertTrue(failure.message.orEmpty().contains("20 MB"))
            }
            try {
                reader.read(File("/system/build.prop"))
                fail("External paths must not be read by OCR")
            } catch (failure: LocalTextException) {
                assertTrue(failure.message.orEmpty().contains("somente imagens"))
            }
        } finally {
            reader.close()
            oversized.delete()
        }
    }

    @Test(timeout = 30_000L)
    fun closedReaderDoesNotStartRecognition() = runBlocking {
        val reader = LocalTextReader(context)
        reader.close()
        try {
            reader.read(File(context.cacheDir, "not-created.png"))
            fail("A closed session must reject work even before opening an image")
        } catch (_: CancellationException) { }
    }

    private fun createTextImage(): File {
        val bitmap = Bitmap.createBitmap(1400, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 96f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        canvas.drawText("NEXUS 42", 100f, 170f, paint)
        paint.textSize = 64f
        canvas.drawText("Leitura local de texto", 100f, 300f, paint)
        val file = File.createTempFile("ocr-text-", ".png", context.cacheDir)
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        return file
    }
}
