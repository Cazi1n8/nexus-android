package br.com.nexus.assistant.device.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.SystemClock
import androidx.exifinterface.media.ExifInterface
import br.com.nexus.assistant.extensions.ExtensionCatalog
import br.com.nexus.assistant.extensions.ExtensionPackId
import br.com.nexus.assistant.extensions.ExtensionRepository
import br.com.nexus.assistant.extensions.ExtensionUnavailableException
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class LocalVisionException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Real bundled image classifier. Every engine operation happens on a serial worker. */
class LocalObjectClassifier(context: Context) : AutoCloseable {
    private val application = context.applicationContext
    private val closed = AtomicBoolean(false)
    private var engine: ImageClassifier? = null
    private var modelLease: AutoCloseable? = null
    private var mappedModel: ByteBuffer? = null

    suspend fun classify(file: File): ImageObservation = withContext(WORKER) {
        ensureOpen(); ensureActive()
        val source = file.canonicalFile
        if (listOf(application.filesDir, application.cacheDir).none { source.toPath().startsWith(it.canonicalFile.toPath()) }) {
            throw LocalVisionException("A visão local aceita somente imagens importadas ou capturadas pelo NEXUS.")
        }
        if (!source.isFile || source.length() !in 1..20L * 1024 * 1024) {
            throw LocalVisionException("A imagem não está disponível ou ultrapassa 20 MB.")
        }
        try {
            val bitmap = decode(source)
            try { classifyOnWorker(bitmap) } finally { bitmap.recycle() }
        } catch (failure: OutOfMemoryError) {
            throw LocalVisionException("Falta memória para abrir esta imagem. Use uma foto menor.", failure)
        }
    }

    /** The caller retains the bitmap until this suspending operation returns. */
    suspend fun classify(bitmap: Bitmap): ImageObservation = withContext(WORKER) {
        ensureOpen(); ensureActive()
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            throw LocalVisionException("O quadro de câmera não está disponível.")
        }
        classifyOnWorker(bitmap)
    }

    private fun classifyOnWorker(bitmap: Bitmap): ImageObservation {
        val start = SystemClock.elapsedRealtime()
        if (!informative(bitmap)) return ImageObservation(emptyList(), bitmap.width, bitmap.height, 0, lowDetail = true)
        try {
            val classifier = engine ?: run {
                val packages = ExtensionRepository.get(application)
                val lease = packages.acquire(ExtensionPackId.VISION_OBJECTS)
                try {
                    val data = packages.verifiedPrivateFile(ExtensionCatalog.objects.files.single())
                    val options = BaseOptions.builder()
                    val buffer = if (data != null) data.inputStream().channel.use {
                        it.map(FileChannel.MapMode.READ_ONLY, 0, data.length())
                    } else null
                    if (buffer != null) options.setModelAssetBuffer(buffer)
                    else { verifyBundledModel(); options.setModelAssetPath(MODEL_ASSET) }
                    ImageClassifier.createFromOptions(application,
                    ImageClassifier.ImageClassifierOptions.builder()
                    .setBaseOptions(options.build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setMaxResults(3)
                    .setScoreThreshold(.20f)
                    .build()).also { engine = it; modelLease = lease; mappedModel = buffer }
                } catch (failure: Throwable) { lease.close(); throw failure }
            }
            ensureOpen()
            val input = BitmapImageBuilder(bitmap).build()
            val result = try { classifier.classify(input) } finally { input.close() }
            ensureOpen()
            val candidates = result.classificationResult().classifications().flatMap { it.categories() }
                .filter { it.score().isFinite() && it.score() in .20f..1f && it.categoryName().isNotBlank() }
                .sortedByDescending { it.score() }.take(3).map { ObjectCandidate(it.categoryName(), it.score()) }
            return ImageObservation(candidates, bitmap.width, bitmap.height, SystemClock.elapsedRealtime() - start)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: ExtensionUnavailableException) {
            throw LocalVisionException(failure.message ?: "Instale o pacote de objetos na Central de Extensões.", failure)
        } catch (failure: LocalVisionException) { throw failure
        } catch (failure: LinkageError) {
            throw LocalVisionException("A classificação de objetos não está disponível nesta arquitetura.", failure)
        } catch (failure: OutOfMemoryError) {
            throw LocalVisionException("Falta memória para avaliar esta imagem. Use uma foto menor ou feche outros aplicativos.", failure)
        } catch (failure: Exception) {
            throw LocalVisionException("Não foi possível classificar objetos nesta imagem. Confira o modelo incluído e tente novamente.", failure)
        }
    }

    private fun verifyBundledModel() {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val source = try { application.assets.open(MODEL_ASSET) } catch (missing: IOException) {
            throw LocalVisionException("Instale NEXUS Vision · objetos na Central de Extensões para classificar imagens localmente.", missing)
        }
        source.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                ensureOpen()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MODEL_BYTES) throw LocalVisionException("O modelo de objetos tem tamanho inválido.")
                digest.update(buffer, 0, count)
            }
        }
        val sha = digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        if (total != MODEL_BYTES || sha != MODEL_SHA256) throw LocalVisionException("O modelo de objetos está ausente ou corrompido. Confira o pacote na Central de Extensões.")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        EXECUTOR.execute {
            val closing = engine
            engine = null
            try { runCatching { closing?.close() } } finally {
                mappedModel = null
                modelLease?.close()
                modelLease = null
            }
        }
    }
    private fun ensureOpen() { if (closed.get()) throw CancellationException("A sessão de visão foi encerrada.") }

    private fun decode(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth !in 1..65_535 || bounds.outHeight !in 1..65_535) throw LocalVisionException("Formato de imagem inválido.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val original = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw LocalVisionException("Não foi possível abrir esta imagem.")
        val transform = Matrix()
        try {
            when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> transform.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> transform.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> transform.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { transform.postRotate(90f); transform.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { transform.postRotate(270f); transform.postScale(-1f, 1f) }
            }
            return if (transform.isIdentity) original else Bitmap.createBitmap(original, 0, 0, original.width, original.height, transform, false).also {
                if (it !== original) original.recycle()
            }
        } catch (failure: Throwable) { original.recycle(); throw failure }
    }

    companion object {
        const val MODEL_ASSET = "vision/efficientnet_lite0_int8.tflite"
        const val MODEL_BYTES = 5_434_517L
        const val MODEL_SHA256 = "bc2ffe19c1118de0c0c2a9088992da5589722656e0fba81421385300a4a34b16"
        private val EXECUTOR = Executors.newSingleThreadExecutor { task -> Thread(task, "nexus-object-classifier").apply { isDaemon = true } }
        private val WORKER = EXECUTOR.asCoroutineDispatcher()
        /** Prevent confident ImageNet hallucinations on fully blank/covered frames. */
        private fun informative(bitmap: Bitmap): Boolean {
            var minimum = 255; var maximum = 0
            var sum = 0.0; var squared = 0.0; var count = 0
            for (y in 0 until 16) for (x in 0 until 16) {
                val pixel = bitmap.getPixel((x * bitmap.width / 16).coerceAtMost(bitmap.width - 1), (y * bitmap.height / 16).coerceAtMost(bitmap.height - 1))
                val light = (((pixel shr 16) and 255) * 299 + ((pixel shr 8) and 255) * 587 + (pixel and 255) * 114) / 1000
                minimum = minOf(minimum, light); maximum = maxOf(maximum, light)
                sum += light; squared += light.toDouble() * light; count++
            }
            return maximum - minimum >= 20 && squared / count - (sum / count) * (sum / count) >= 30
        }
    }
}
