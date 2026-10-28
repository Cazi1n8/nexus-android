package br.com.nexus.assistant.device.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.SystemClock
import androidx.exifinterface.media.ExifInterface
import br.com.nexus.assistant.extensions.ExtensionPackId
import br.com.nexus.assistant.extensions.ExtensionRepository
import br.com.nexus.assistant.extensions.ExtensionUnavailableException
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class OcrBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class OcrBlock(val text: String, val lines: List<String>, val bounds: OcrBounds?)

/** Coordinates refer to the bounded, EXIF-oriented image, not to a camera preview. */
data class OcrResult(
    val sourcePath: String,
    val text: String,
    val blocks: List<OcrBlock>,
    val processedWidth: Int,
    val processedHeight: Int,
    val elapsedMs: Long,
    val truncated: Boolean = false,
)

class LocalTextException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Offline Portuguese/English OCR with bundled, checksum-pinned tessdata_fast models.
 * This reader never starts a sensor or opens a network connection.
 *
 * All instances share one worker, including private-model preparation. Tesseract's
 * getUTF8Text() is blocking and cannot be interrupted by stop(). Cancellation or
 * close discards the result; an already-started native read finishes on the worker
 * before its engine/bitmap are released. A subsequent read cannot run in parallel.
 */
class LocalTextReader(context: Context) : AutoCloseable {
    private val application = context.applicationContext
    private val closed = AtomicBoolean(false)

    suspend fun read(file: File): OcrResult = withContext(OCR_WORKER) {
        ensureOpen()
        ensureActive()
        val startedAt = SystemClock.elapsedRealtime()
        val source = validateSource(file)
        var bitmap: Bitmap? = null
        var engine: TessBaseAPI? = null
        var modelLease: AutoCloseable? = null
        try {
            modelLease = ExtensionRepository.get(application).acquire(ExtensionPackId.VISION_OCR)
            val modelDirectory = prepareModels()
            ensureActive()
            ensureOpen()
            bitmap = decodeOriented(source)
            ensureActive()
            ensureOpen()
            engine = TessBaseAPI()
            if (!engine.init(modelDirectory.absolutePath + File.separator, "por+eng", TessBaseAPI.OEM_LSTM_ONLY)) {
                throw LocalTextException("Não foi possível iniciar os modelos de texto em português e inglês. Tente novamente ou reinstale o NEXUS após exportar seus dados.")
            }
            engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            engine.setImage(bitmap)
            val text = engine.getUTF8Text().orEmpty()
            // Never publish a cancelled or closed session, even if native work finished.
            ensureActive()
            ensureOpen()
            result(file.absolutePath, text, engine, bitmap, SystemClock.elapsedRealtime() - startedAt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: LocalTextException) {
            throw failure
        } catch (failure: ExtensionUnavailableException) {
            throw LocalTextException(failure.message ?: "Confira o pacote OCR na Central de Extensões.", failure)
        } catch (failure: OutOfMemoryError) {
            throw LocalTextException("Não há memória livre suficiente para ler esta imagem. Feche outros aplicativos ou use uma foto menor.", failure)
        } catch (failure: LinkageError) {
            throw LocalTextException("O leitor de texto local não está disponível para a arquitetura deste aparelho.", failure)
        } catch (failure: Exception) {
            throw LocalTextException("Não foi possível ler o texto desta imagem. Tente uma foto nítida, bem iluminada e com as letras maiores.", failure)
        } finally {
            // Access and release the native instance only on the worker that owns it.
            try { engine?.recycle() } finally {
                try { bitmap?.recycle() } finally { modelLease?.close() }
            }
        }
    }

    /** Suppresses results/new reads. Native memory is released by its owning worker. */
    override fun close() { closed.set(true) }

    private fun ensureOpen() {
        if (closed.get()) throw CancellationException("A sessão de leitura de texto foi encerrada.")
    }

    private fun validateSource(file: File): File {
        val source = file.canonicalFile
        val allowed = listOf(application.filesDir, application.cacheDir).any { directory ->
            source.toPath().startsWith(directory.canonicalFile.toPath())
        }
        if (!allowed) throw LocalTextException("A leitura local aceita somente imagens importadas ou capturadas pelo NEXUS.")
        if (!source.isFile || source.length() !in 1..MAX_FILE_BYTES) {
            throw LocalTextException("A imagem não está disponível ou ultrapassa o limite de 20 MB.")
        }
        return source
    }

    /** Reusable private copy for Tesseract, accepted only with the exact bundled hashes. */
    private fun prepareModels(): File {
        val root = File(application.filesDir, "ocr")
        val tessdata = File(root, "tessdata")
        if (!tessdata.mkdirs() && !tessdata.isDirectory) throw LocalTextException("Não há espaço para preparar o leitor local de texto.")
        if (!tessdata.canonicalFile.toPath().startsWith(application.filesDir.canonicalFile.toPath()) ||
            Files.isSymbolicLink(root.toPath()) || Files.isSymbolicLink(tessdata.toPath())) {
            throw LocalTextException("O diretório privado do leitor de texto é inválido.")
        }
        for (model in MODELS) {
            ensureOpen()
            val destination = File(tessdata, "${model.language}.traineddata")
            if (Files.isSymbolicLink(destination.toPath())) throw LocalTextException("O arquivo do modelo local é inválido.")
            if (verified(destination, model)) continue
            val temporary = File.createTempFile("ocr-", ".part", tessdata)
            try {
                val asset = try { application.assets.open("ocr/${model.language}.traineddata") } catch (missing: IOException) {
                    throw LocalTextException("Instale NEXUS Vision · leitura de texto na Central de Extensões para usar OCR offline.", missing)
                }
                asset.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var size = 0L
                        while (true) {
                            ensureOpen()
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            if (size > model.bytes) throw LocalTextException("O modelo local de texto possui tamanho inválido.")
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                if (!verified(temporary, model)) throw LocalTextException("O modelo local de texto está incompleto ou corrompido. Reinstale o aplicativo após exportar seus dados.")
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
        return root
    }

    private fun verified(file: File, model: Model): Boolean {
        if (!file.isFile || file.length() != model.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == model.sha256
    }

    private fun decodeOriented(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth !in 1..65_535 || bounds.outHeight !in 1..65_535) {
            throw LocalTextException("A imagem não possui um formato ou tamanho compatível com este Android.")
        }
        var sample = 1
        while (sampledEdge(bounds.outWidth, sample) > MAX_EDGE || sampledEdge(bounds.outHeight, sample) > MAX_EDGE ||
            sampledEdge(bounds.outWidth, sample).toLong() * sampledEdge(bounds.outHeight, sample) > MAX_PIXELS) {
            sample *= 2
        }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw LocalTextException("Não foi possível abrir a imagem para ler o texto.")
        try {
            val orientation = runCatching {
                ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = orientationMatrix(orientation)
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    private fun result(sourcePath: String, raw: String, engine: TessBaseAPI, bitmap: Bitmap, elapsedMs: Long): OcrResult {
        var truncated = raw.length > MAX_TEXT_CHARACTERS
        val blocks = mutableListOf<OcrBlock>()
        val iterator = engine.getResultIterator()
        if (iterator != null) {
            try {
                iterator.begin()
                do {
                    if (blocks.size >= MAX_BLOCKS) { truncated = true; break }
                    val blockText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_BLOCK).orEmpty().trim()
                    if (blockText.isBlank()) continue
                    val lines = blockText.lines()
                    if (blockText.length > MAX_BLOCK_TEXT || lines.size > MAX_LINES_PER_BLOCK) truncated = true
                    val rect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_BLOCK)
                    blocks += OcrBlock(blockText.take(MAX_BLOCK_TEXT), lines.take(MAX_LINES_PER_BLOCK).map {
                        if (it.length > MAX_LINE_TEXT) truncated = true
                        it.take(MAX_LINE_TEXT)
                    }, rect?.let { OcrBounds(it.left, it.top, it.right, it.bottom) })
                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_BLOCK))
            } finally {
                iterator.delete()
            }
        }
        return OcrResult(sourcePath, raw.take(MAX_TEXT_CHARACTERS).trim(), blocks,
            bitmap.width, bitmap.height, elapsedMs, truncated)
    }

    companion object {
        private const val MAX_FILE_BYTES = 20L * 1024 * 1024
        private const val MAX_EDGE = 2048
        private const val MAX_PIXELS = 4_194_304L
        private const val MAX_TEXT_CHARACTERS = 30_000
        private const val MAX_BLOCKS = 150
        private const val MAX_BLOCK_TEXT = 3_000
        private const val MAX_LINES_PER_BLOCK = 100
        private const val MAX_LINE_TEXT = 1_000
        private val OCR_WORKER = Executors.newSingleThreadExecutor { work -> Thread(work, "nexus-local-ocr").apply { isDaemon = true } }.asCoroutineDispatcher()
        private data class Model(val language: String, val bytes: Long, val sha256: String)
        private val MODELS = listOf(
            Model("por", 1_982_756L, "c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb"),
            Model("eng", 4_113_088L, "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"),
        )
        private fun sampledEdge(edge: Int, sample: Int) = (edge + sample - 1) / sample

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
}
