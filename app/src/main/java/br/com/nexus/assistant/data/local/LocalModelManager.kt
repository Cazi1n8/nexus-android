package br.com.nexus.assistant.data.local

import br.com.nexus.assistant.data.AiException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Fetches only the explicitly chosen public model. Conversations never enter this request. */
class LocalModelManager(
    private val directory: File,
    val spec: LocalModelSpec = LocalModelCatalog.compact,
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(false)
        .build(),
    private val responseTimeoutMillis: Long = 30_000,
) {
    init {
        require(spec.fileName.isNotBlank() && spec.fileName !in setOf(".", "..") &&
            !spec.fileName.contains('/') && !spec.fileName.contains('\\')) { "Nome inválido do modelo local." }
        require(spec.bytes > 0) { "Tamanho inválido do modelo local." }
        require(responseTimeoutMillis > 0) { "Tempo de conexão inválido." }
    }

    // Retain custom TLS/proxy settings used by controlled HTTPS tests, while
    // preventing a supplied client from disabling production time/security bounds.
    private val client = client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(false)
        .connectTimeout(boundedTimeout(client.connectTimeoutMillis, 20_000), TimeUnit.MILLISECONDS)
        .readTimeout(boundedTimeout(client.readTimeoutMillis, 20_000), TimeUnit.MILLISECONDS)
        .callTimeout(boundedTimeout(client.callTimeoutMillis, 30 * 60 * 1000), TimeUnit.MILLISECONDS)
        .build()
    private val mutex = Mutex()
    private val _state = MutableStateFlow(LocalModelState(totalBytes = spec.bytes))
    val state = _state.asStateFlow()
    private val destination get() = File(directory, spec.fileName)
    private val partial get() = File(directory, "${spec.fileName}.part")

    suspend fun refresh() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val coroutine = currentCoroutineContext()
            try {
                ensureDirectory()
                if (destination.exists()) {
                    if (verify(destination) { coroutine.ensureActive() }) {
                        partial.delete()
                        update(LocalModelStatus.READY, spec.bytes, verified = spec.bytes)
                        return@withContext
                    }
                    check(destination.delete() || !destination.exists())
                    publishAvailable("O modelo instalado estava incompleto ou corrompido. Baixe-o novamente.")
                } else {
                    // A process restart must preserve the prefix for HTTP Range.
                    val count = partialSize()
                    if (count > 0) publishAvailable("Download interrompido. A parte recebida foi preservada para continuar.")
                    else update(LocalModelStatus.ABSENT)
                }
            } catch (cancelled: CancellationException) {
                publishAvailable("Verificação pausada. Nenhum arquivo completo foi aceito sem SHA-256.")
                throw cancelled
            } catch (failure: Exception) {
                publishFailure(failure.message ?: "Não foi possível verificar o modelo local.")
            }
        }
    }

    /** Caller obtains the user's explicit download/resume authorization before invoking this. */
    suspend fun download() = mutex.withLock {
        if (_state.value.status == LocalModelStatus.READY && destination.isFile) return@withLock
        withContext(Dispatchers.IO) {
            val coroutine = currentCoroutineContext()
            var call: Call? = null
            try {
                ensureDirectory()
                if (destination.exists()) {
                    if (verify(destination) { coroutine.ensureActive() }) {
                        partial.delete()
                        update(LocalModelStatus.READY, spec.bytes, verified = spec.bytes)
                        return@withContext
                    }
                    check(destination.delete() || !destination.exists())
                }
                val offset = partialSize()
                if (offset == spec.bytes) {
                    completePartial { coroutine.ensureActive() }
                    return@withContext
                }
                val needed = spec.bytes - offset + STORAGE_MARGIN
                check(directory.usableSpace >= needed) {
                    "Libere pelo menos ${(needed + 999_999) / 1_000_000} MB no armazenamento interno para continuar o download."
                }
                update(LocalModelStatus.CONNECTING, offset,
                    if (offset > 0) "Conectando para continuar a partir dos dados já recebidos…" else "Conectando ao servidor do modelo…")
                val request = Request.Builder().url(spec.downloadUrl)
                    .header("Accept-Encoding", "identity")
                    .apply { if (offset > 0) header("Range", "bytes=$offset-") }
                    .build()
                check(request.url.isHttps) { "O download do modelo exige HTTPS." }
                val downloadCall = client.newCall(request)
                call = downloadCall
                // Cancel both the header wait and a blocked body read on user pause.
                val cancellation = CoroutineScope(coroutine).launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { downloadCall.cancel() }
                }
                try {
                    val response = try {
                        withTimeout(responseTimeoutMillis.coerceAtMost(45_000)) { awaitResponse(downloadCall) }
                    } catch (timeout: TimeoutCancellationException) {
                        downloadCall.cancel()
                        throw IOException("O servidor não respondeu a tempo. Verifique a conexão e tente continuar o download.", timeout)
                    }
                    response.use {
                        if (response.code !in setOf(200, 206)) {
                            throw IOException("Não foi possível baixar o modelo (HTTP ${response.code}). Os dados já recebidos foram preservados.")
                        }
                        if (!response.request.url.isHttps) throw IOException("O servidor tentou usar uma conexão insegura.")
                        val body = response.body ?: throw IOException("O servidor não enviou o modelo.")
                        val resumed = response.code == 206
                        val contentBytes = if (resumed) validateRange(response, offset) else spec.bytes
                        val declared = body.contentLength()
                        if (declared >= 0 && declared != contentBytes) {
                            throw IOException("O servidor informou um tamanho inesperado para o arquivo. Tente continuar mais tarde.")
                        }
                        val start = if (resumed) offset else 0L
                        val restartMessage = if (offset > 0 && !resumed) {
                            "Este servidor não aceitou a retomada; o arquivo está sendo recebido novamente desde o início."
                        } else null
                        receiveBody(body.byteStream(), downloadCall, start, contentBytes, restartMessage) {
                            coroutine.ensureActive()
                        }
                    }
                } finally {
                    cancellation.cancel()
                }
                coroutine.ensureActive()
                completePartial { coroutine.ensureActive() }
            } catch (cancelled: CancellationException) {
                publishAvailable("Download pausado. Os dados recebidos foram preservados; você pode continuar depois.")
                throw cancelled
            } catch (failure: Exception) {
                if (!coroutine.isActive) {
                    publishAvailable("Download pausado. Os dados recebidos foram preservados.")
                    coroutine.ensureActive()
                }
                val message = if (failure is SocketTimeoutException) {
                    "A conexão ficou sem enviar dados por tempo demais. Verifique a rede e continue o download; a parte recebida foi preservada."
                } else failure.message ?: "Falha no download. Verifique a conexão e tente continuar."
                publishFailure(message)
                throw AiException(_state.value.message.orEmpty(), failure)
            } finally {
                call?.cancel()
            }
        }
    }

    /** Reads only the immutable model's exact byte budget, without waiting for HTTP EOF. */
    private fun receiveBody(
        input: java.io.InputStream,
        call: Call,
        offset: Long,
        contentBytes: Long,
        message: String?,
        checkCancellation: () -> Unit,
    ) {
        var received = offset
        val expectedEnd = offset + contentBytes
        val started = System.nanoTime()
        var notifiedAt = started
        var notifiedBytes = offset
        var output: FileOutputStream? = null
        update(LocalModelStatus.DOWNLOADING, received, message)
        try {
            val buffer = ByteArray(64 * 1024)
            while (received < expectedEnd) {
                checkCancellation()
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), expectedEnd - received).toInt())
                if (count < 0) throw IOException("A conexão terminou antes de receber todo o modelo. Continue o download para aproveitar os dados já recebidos.")
                if (count == 0) continue
                // Delay truncating a rejected Range prefix until a new byte arrives.
                if (output == null) output = FileOutputStream(partial, offset > 0)
                output.write(buffer, 0, count)
                received += count
                val now = System.nanoTime()
                val elapsed = (now - started).coerceAtLeast(1)
                if (notifiedBytes == offset || now - notifiedAt >= PROGRESS_INTERVAL_NANOS ||
                    received - notifiedBytes >= 256 * 1024 || received == expectedEnd) {
                    update(LocalModelStatus.DOWNLOADING, received, message,
                        speed = ((received - offset).toDouble() * 1_000_000_000 / elapsed).toLong().coerceAtLeast(0))
                    notifiedAt = now
                    notifiedBytes = received
                }
            }
            checkCancellation()
        } finally {
            // A chunked/unknown-length response may keep the connection open even
            // after the entire pinned file arrived. Cancel before close, then hash
            // the private file; never wait for a trailer or an extra read at 100%.
            call.cancel()
            try { output?.close() } finally { runCatching { input.close() } }
        }
        if (received != spec.bytes) {
            throw IOException("O servidor enviou apenas parte do restante. Continue o download para completar o arquivo.")
        }
    }

    private fun validateRange(response: Response, offset: Long): Long {
        val value = response.header("Content-Range")
            ?: throw IOException("O servidor não confirmou o intervalo da retomada.")
        val match = CONTENT_RANGE.matchEntire(value.trim())
            ?: throw IOException("O servidor retornou um intervalo inválido para a retomada.")
        val start = match.groupValues[1].toLongOrNull()
        val end = match.groupValues[2].toLongOrNull()
        val total = match.groupValues[3].toLongOrNull()
        if (start != offset || end == null || end < offset || end >= spec.bytes || total != spec.bytes) {
            throw IOException("O intervalo recebido não corresponde ao modelo selecionado. Os dados anteriores foram preservados.")
        }
        return end - offset + 1
    }

    private fun completePartial(checkCancellation: () -> Unit) {
        if (partial.length() != spec.bytes) throw IOException("O modelo ainda não foi recebido por completo. Continue o download.")
        if (!verify(partial, checkCancellation)) {
            check(partial.delete() || !partial.exists()) { "Não foi possível descartar o modelo corrompido." }
            throw IOException("A verificação SHA-256 do modelo falhou. O arquivo foi descartado; baixe-o novamente.")
        }
        checkCancellation()
        check(partial.renameTo(destination)) { "Não foi possível concluir o download no armazenamento interno." }
        update(LocalModelStatus.READY, spec.bytes, verified = spec.bytes)
    }

    private fun verify(file: File, checkCancellation: () -> Unit): Boolean {
        update(LocalModelStatus.VERIFYING, file.length())
        var notifiedAt = System.nanoTime()
        return ModelIntegrity.verifyWithProgress(file, spec, checkCancellation) { count ->
            val now = System.nanoTime()
            if (count == 0L || count == spec.bytes || now - notifiedAt >= PROGRESS_INTERVAL_NANOS) {
                update(LocalModelStatus.VERIFYING, file.length(), verified = count)
                notifiedAt = now
            }
        }
    }

    private fun ensureDirectory() {
        check(directory.isDirectory || directory.mkdirs()) { "Não foi possível criar a pasta privada do modelo." }
    }

    private fun partialSize(): Long {
        if (!partial.exists()) return 0
        check(partial.isFile) { "O arquivo parcial do modelo é inválido." }
        val count = partial.length()
        if (count > spec.bytes) {
            check(partial.delete() || !partial.exists())
            throw IOException("O download parcial tinha tamanho inválido e foi descartado. Baixe o modelo novamente.")
        }
        return count
    }

    private fun availableBytes(): Long = when {
        destination.isFile && destination.length() in 1..spec.bytes -> destination.length()
        partial.isFile && partial.length() in 1..spec.bytes -> partial.length()
        else -> 0L
    }

    private fun publishAvailable(message: String) {
        val bytes = availableBytes()
        update(if (bytes > 0) LocalModelStatus.PAUSED else LocalModelStatus.ABSENT, bytes, message)
    }

    private fun publishFailure(message: String) {
        val bytes = availableBytes()
        update(if (bytes > 0) LocalModelStatus.PAUSED else LocalModelStatus.FAILED, bytes, message)
    }

    /** Caller releases the inference runtime before removing a memory-mapped model. */
    suspend fun delete() = mutex.withLock {
        withContext(Dispatchers.IO) {
            check(!destination.exists() || destination.delete()) { "Não foi possível remover o modelo local." }
            check(!partial.exists() || partial.delete()) { "Não foi possível remover o download incompleto." }
            update(LocalModelStatus.ABSENT)
        }
    }

    fun requireReadyFile(): File {
        if (_state.value.status != LocalModelStatus.READY || !destination.isFile) {
            throw AiException("Baixe e verifique o modelo em NEXUS AI para conversar sem servidor ou chave.")
        }
        return destination
    }

    private fun update(status: LocalModelStatus, bytes: Long = 0, message: String? = null, speed: Long = 0, verified: Long = 0) {
        _state.value = LocalModelState(status, bytes, spec.bytes, message, speed, verified)
    }

    private suspend fun awaitResponse(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    companion object {
        private const val STORAGE_MARGIN = 64L * 1024 * 1024
        private const val PROGRESS_INTERVAL_NANOS = 250_000_000L
        private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
        private fun boundedTimeout(value: Int, maximum: Int): Long =
            if (value <= 0) maximum.toLong() else value.coerceAtMost(maximum).toLong()
    }
}
