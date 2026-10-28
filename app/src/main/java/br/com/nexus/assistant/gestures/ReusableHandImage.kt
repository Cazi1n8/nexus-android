package br.com.nexus.assistant.gestures

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import java.nio.ByteBuffer

/** Owned exclusively by the inference executor. Reuses its RGBA/rotation storage.
 * buildImage uses a separately owned direct RGBA buffer. Closing a bitmap-backed
 * MPImage would recycle our reusable bitmap (SDK0.10.35 BitmapImageContainer), so
 * BitmapImageBuilder must NOT wrap read()'s output. detectForVideo is synchronous;
 * close that buffer-backed MPImage before the next call. No camera plane escapes.
 */
internal class ReusableHandImage : AutoCloseable {
    private var source: Bitmap? = null
    private var rotated: Bitmap? = null
    private var packed: ByteBuffer? = null
    private var inferenceBuffer: ByteBuffer? = null
    internal var bitmapAllocationCount = 0; private set
    private val matrix = Matrix()
    private val canvas = Canvas()

    fun read(proxy: ImageProxy): Bitmap {
        val plane = proxy.planes.first()
        require(plane.pixelStride == 4) { "The hand analyzer requires RGBA_8888 input" }
        return readRgba(proxy.width, proxy.height, plane.rowStride, plane.buffer, proxy.imageInfo.rotationDegrees)
    }

    internal fun readRgba(width: Int, height: Int, rowStride: Int, input: ByteBuffer, degrees: Int): Bitmap {
        require(width > 0 && height > 0 && rowStride >= width * 4)
        require(input.limit() >= (height - 1) * rowStride + width * 4)
        var bitmap = source
        if (bitmap == null || bitmap.width != width || bitmap.height != height) {
            source?.recycle()
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmapAllocationCount++
            source = bitmap
        }
        input.rewind()
        val rowBytes = width * 4
        if (rowStride == rowBytes) {
            bitmap.copyPixelsFromBuffer(input)
        } else {
            // CameraX vendors can pad RGBA rows. Never draw that padding as pixels.
            val size = rowBytes * height
            if (packed?.capacity() != size) packed = ByteBuffer.allocateDirect(size)
            val output = packed!!
            output.clear()
            val originalLimit = input.limit()
            for (row in 0 until height) {
                val start = row * rowStride
                input.limit(start + rowBytes)
                input.position(start)
                output.put(input)
            }
            input.limit(originalLimit)
            output.rewind()
            bitmap.copyPixelsFromBuffer(output)
        }
        if (degrees == 0) return bitmap
        require(degrees == 90 || degrees == 180 || degrees == 270)
        val outputWidth = if (degrees == 180) width else height
        val outputHeight = if (degrees == 180) height else width
        var output = rotated
        if (output == null || output.width != outputWidth || output.height != outputHeight) {
            canvas.setBitmap(null)
            rotated?.recycle()
            output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            bitmapAllocationCount++
            rotated = output
        }
        matrix.reset()
        matrix.postRotate(degrees.toFloat())
        when (degrees) {
            90 -> matrix.postTranslate(height.toFloat(), 0f)
            180 -> matrix.postTranslate(width.toFloat(), height.toFloat())
            270 -> matrix.postTranslate(0f, width.toFloat())
        }
        canvas.setBitmap(output)
        canvas.drawBitmap(bitmap, matrix, null)
        return output
    }

    fun buildImage(bitmap: Bitmap): MPImage {
        val bytes = bitmap.width * bitmap.height * 4
        if (inferenceBuffer?.capacity() != bytes) inferenceBuffer = ByteBuffer.allocateDirect(bytes)
        val buffer = inferenceBuffer!!
        buffer.clear()
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
        return ByteBufferImageBuilder(buffer, bitmap.width, bitmap.height, MPImage.IMAGE_FORMAT_RGBA).build()
    }

    override fun close() {
        canvas.setBitmap(null)
        source?.recycle(); source = null
        rotated?.recycle(); rotated = null
        packed = null
        inferenceBuffer = null
    }
}
