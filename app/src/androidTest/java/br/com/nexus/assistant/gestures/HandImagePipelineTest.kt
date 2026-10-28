package br.com.nexus.assistant.gestures

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

/** These assertions need Android graphics/native MediaPipe; JVM stubs cannot prove them. */
class HandImagePipelineTest {
    private val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK, Color.YELLOW)

    private fun rgba(width: Int, height: Int, rowPadding: Int = 0, values: IntArray? = null): ByteBuffer {
        val stride = width * 4 + rowPadding
        val buffer = ByteBuffer.allocateDirect(stride * height)
        repeat(height) { row ->
            repeat(width) { column ->
                val color = values?.get(row * width + column) ?: Color.BLACK
                buffer.put(Color.red(color).toByte()).put(Color.green(color).toByte())
                    .put(Color.blue(color).toByte()).put(Color.alpha(color).toByte())
            }
            repeat(rowPadding) { buffer.put(0x7f.toByte()) }
        }
        buffer.rewind()
        return buffer
    }

    @Test fun paddedRgbaRowsAndAllCameraRotationsPreservePixelCoordinates() {
        val input = rgba(3, 2, 4, colors)
        val expected = mapOf(
            0 to listOf(0, 1, 2, 3, 4, 5),
            90 to listOf(3, 0, 4, 1, 5, 2),
            180 to listOf(5, 4, 3, 2, 1, 0),
            270 to listOf(2, 5, 1, 4, 0, 3),
        )
        ReusableHandImage().use { images ->
            expected.forEach { (rotation, indices) ->
                val bitmap = images.readRgba(3, 2, 16, input, rotation)
                val width = if (rotation % 180 == 0) 3 else 2
                assertEquals(width, bitmap.width)
                assertEquals(6 / width, bitmap.height)
                indices.forEachIndexed { pixel, source ->
                    assertEquals("rotation$rotation pixel$pixel", colors[source], bitmap.getPixel(pixel % width, pixel / width))
                }
            }
        }
    }

    @Test fun bufferBackedMpImageClosePreservesOwnedBitmapForNextFrame() {
        val input = rgba(3, 2, values = colors)
        var bitmap: Bitmap? = null
        ReusableHandImage().use { images ->
            repeat(30) {
                val next = images.readRgba(3, 2, 12, input, 90)
                if (bitmap != null) assertSame(bitmap, next)
                bitmap = next
                images.buildImage(next).close()
                assertFalse("MPImage.close must not recycle the reusable bitmap", next.isRecycled)
                assertEquals(Color.WHITE, next.getPixel(0, 0))
            }
            assertEquals("Only source and rotated bitmap should be allocated", 2, images.bitmapAllocationCount)
        }
        assertTrue("Session close must release owned bitmaps", bitmap!!.isRecycled)
    }

    @Test fun reusedBufferRunsMultipleActualVideoInferencesAndClosesCleanly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = rgba(320, 240)
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setRunningMode(RunningMode.VIDEO).setNumHands(1).build()
        HandLandmarker.createFromOptions(context, options).use { detector ->
            ReusableHandImage().use { images ->
                repeat(4) { frame ->
                    val bitmap = images.readRgba(320, 240, 1280, input, 90)
                    images.buildImage(bitmap).use { image ->
                        assertTrue(detector.detectForVideo(image, frame * 100L).landmarks().isEmpty())
                    }
                    assertFalse(bitmap.isRecycled)
                }
                assertEquals(2, images.bitmapAllocationCount)
            }
        }
    }

    @Test fun bitmapPreparationBenchmarkReportsActualAllocationsAndTimesWithoutClaimingPhoneLatency() {
        val width = 320
        val height = 240
        val input = rgba(width, height)
        val matrix = Matrix().apply { postRotate(90f) }
        val oldTimes = mutableListOf<Long>()
        val newTimes = mutableListOf<Long>()
        val measured = 60
        repeat(measured + 10) { index ->
            input.rewind()
            val start = SystemClock.elapsedRealtimeNanos()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(input)
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false)
            BitmapImageBuilder(rotated).build().close()
            bitmap.recycle()
            if (index >= 10) oldTimes += SystemClock.elapsedRealtimeNanos() - start
        }
        ReusableHandImage().use { images ->
            repeat(measured + 10) { index ->
                val start = SystemClock.elapsedRealtimeNanos()
                val bitmap = images.readRgba(width, height, width * 4, input, 90)
                images.buildImage(bitmap).close()
                if (index >= 10) newTimes += SystemClock.elapsedRealtimeNanos() - start
            }
            assertEquals(2, images.bitmapAllocationCount)
            fun medianNs(values: List<Long>) = values.sorted()[values.size / 2]
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("nexus_hand_image_benchmark",
                    "{\"scope\":\"Android RGBA preparation only, not whole camera pipeline\",\"width\":$width,\"height\":$height," +
                        "\"samples\":$measured,\"before_bitmap_allocations_including_warmup\":${2 * (measured + 10)}," +
                        "\"after_bitmap_allocations_including_warmup\":${images.bitmapAllocationCount}," +
                        "\"before_median_ns\":${medianNs(oldTimes)},\"after_median_ns\":${medianNs(newTimes)}}")
            })
        }
    }
}
