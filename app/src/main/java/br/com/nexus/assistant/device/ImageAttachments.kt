package br.com.nexus.assistant.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

object ImageAttachments {
    private const val MAX_SOURCE_BYTES = 20L * 1024 * 1024
    private const val MAX_OUTPUT_BYTES = 2L * 1024 * 1024
    private const val MAX_EDGE = 1280

    /** Copies a picker/camera image into a private, bounded JPEG and removes location metadata. */
    suspend fun copyAndCompress(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)
        if (mime != null && !mime.startsWith("image/")) {
            throw AttachmentException("Selecione um arquivo de imagem.")
        }
        val source = File.createTempFile("nexus-import-", ".tmp", context.cacheDir)
        var output: File? = null
        var bitmap: Bitmap? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { target ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        if (total > MAX_SOURCE_BYTES) {
                            throw AttachmentException("A imagem é muito grande. Escolha um arquivo de até 20 MB.")
                        }
                        target.write(buffer, 0, count)
                    }
                }
            } ?: throw AttachmentException("Não foi possível abrir a imagem selecionada.")

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 65535 || bounds.outHeight > 65535) {
                throw AttachmentException("Formato de imagem inválido ou não compatível com este Android.")
            }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
            var current = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: throw AttachmentException("Não foi possível decodificar a imagem.")
            bitmap = current

            val orientation = runCatching {
                ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = orientationMatrix(orientation)
            if (!matrix.isIdentity) {
                val oriented = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
                if (oriented !== current) current.recycle()
                current = oriented
                bitmap = current
            }
            val edge = max(current.width, current.height)
            if (edge > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / edge
                val resized = Bitmap.createScaledBitmap(
                    current,
                    (current.width * scale).roundToInt().coerceAtLeast(1),
                    (current.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
                if (resized !== current) current.recycle()
                current = resized
                bitmap = current
            }
            val targetFile = File.createTempFile("nexus-image-", ".jpg", context.cacheDir)
            output = targetFile
            var quality = 85
            do {
                ensureActive()
                targetFile.outputStream().use { target ->
                    if (!current.compress(Bitmap.CompressFormat.JPEG, quality, target)) {
                        throw AttachmentException("Não foi possível preparar a imagem para envio.")
                    }
                }
                quality -= 10
            } while (targetFile.length() > MAX_OUTPUT_BYTES && quality >= 45)
            if (targetFile.length() > MAX_OUTPUT_BYTES) {
                throw AttachmentException("A imagem continua muito grande após a compressão. Escolha uma imagem menor.")
            }
            ensureActive()
            targetFile
        } catch (error: Exception) {
            output?.delete()
            if (error is CancellationException) throw error
            if (error is AttachmentException) throw error
            throw AttachmentException("Não foi possível importar a imagem. Verifique o arquivo e o espaço disponível.", error)
        } finally {
            source.delete()
            bitmap?.recycle()
        }
    }

    private fun orientationMatrix(orientation: Int) = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { setRotate(180f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
}

class AttachmentException(message: String, cause: Throwable? = null) : IOException(message, cause)
