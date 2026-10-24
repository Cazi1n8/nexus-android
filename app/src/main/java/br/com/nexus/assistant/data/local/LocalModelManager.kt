package br.com.nexus.assistant.data.local

import br.com.nexus.assistant.data.AiException
import br.com.nexus.assistant.data.local.ModelIntegrity.toHex
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** No conversation content is sent by this downloader; only the chosen public GGUF is fetched. */
class LocalModelManager(
    private val directory: File,
    val spec: LocalModelSpec = LocalModelCatalog.compact,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(false)
        .build(),
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(LocalModelState(totalBytes = spec.bytes))
    val state = _state.asStateFlow()
    private val destination get() = File(directory, spec.fileName)
    private val partial get() = File(directory, "${spec.fileName}.part")

    suspend fun refresh() = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                directory.mkdirs()
                partial.delete() // An interrupted download is never accepted as a model.
                if (!destination.exists()) {
                    update(LocalModelStatus.ABSENT)
                } else {
                    update(LocalModelStatus.VERIFYING, destination.length())
                    val context = currentCoroutineContext()
                    if (ModelIntegrity.verify(destination, spec) { context.ensureActive() }) {
                        update(LocalModelStatus.READY, spec.bytes)
                    } else {
                        check(destination.delete() || !destination.exists())
                        update(LocalModelStatus.FAILED, message = "O modelo local estava incompleto ou corrompido. Baixe-o novamente.")
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                update(LocalModelStatus.FAILED, message = failure.message ?: "Não foi possível verificar o modelo local.")
            }
        }
    }

    /** Must only be invoked after the user explicitly chooses the download in the UI. */
    suspend fun download() = mutex.withLock {
        if (_state.value.status == LocalModelStatus.READY) return@withLock
        withContext(Dispatchers.IO) {
            val coroutine = currentCoroutineContext()
            var call: Call? = null
            try {
                check(directory.isDirectory || directory.mkdirs()) { "Não foi possível criar a pasta privada do modelo." }
                partial.delete()
                check(directory.usableSpace >= spec.bytes + 64L * 1024 * 1024) {
                    val requiredMb = (spec.bytes + 64L * 1024 * 1024 + 999_999) / 1_000_000
                    "Libere pelo menos $requiredMb MB no armazenamento interno para baixar este modelo."
                }
                update(LocalModelStatus.DOWNLOADING)
                val request = Request.Builder().url(spec.downloadUrl).header("Accept-Encoding", "identity").build()
                check(request.url.isHttps) { "O download do modelo exige HTTPS." }
                val downloadCall = client.newCall(request)
                call = downloadCall
                // Cancellation must interrupt both a queued request and a blocking stream read.
                val cancellation = CoroutineScope(coroutine).launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { downloadCall.cancel() }
                }
                try {
                    awaitResponse(downloadCall).use { response ->
                        if (!response.isSuccessful) throw IOException("Não foi possível baixar o modelo (HTTP ${response.code}). Tente novamente.")
                        if (!response.request.url.isHttps) throw IOException("O servidor tentou usar uma conexão insegura.")
                        val body = response.body ?: throw IOException("O servidor não enviou o modelo.")
                        val declared = body.contentLength()
                        if (declared > spec.bytes) throw IOException("O arquivo recebido tem tamanho inesperado.")
                        val digest = MessageDigest.getInstance("SHA-256")
                        var received = 0L
                        body.byteStream().buffered().use { input ->
                            FileOutputStream(partial).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var notified = 0L
                                while (true) {
                                    coroutine.ensureActive()
                                    val count = input.read(buffer)
                                    if (count == -1) break
                                    received += count
                                    if (received > spec.bytes) throw IOException("O arquivo recebido excedeu o tamanho esperado.")
                                    output.write(buffer, 0, count)
                                    digest.update(buffer, 0, count)
                                    if (received - notified >= 256 * 1024) {
                                        update(LocalModelStatus.DOWNLOADING, received)
                                        notified = received
                                    }
                                }
                                output.fd.sync()
                            }
                        }
                        coroutine.ensureActive()
                        update(LocalModelStatus.VERIFYING, received)
                        if (received != spec.bytes || digest.digest().toHex() != spec.sha256) {
                            throw IOException("A verificação SHA-256 do modelo falhou. O arquivo foi descartado; tente novamente.")
                        }
                        coroutine.ensureActive()
                        check(partial.renameTo(destination)) { "Não foi possível concluir o download no armazenamento interno." }
                        update(LocalModelStatus.READY, received)
                    }
                } finally { cancellation.cancel() }
            } catch (cancelled: CancellationException) {
                update(LocalModelStatus.ABSENT, message = "Download cancelado. Nenhuma conversa foi enviada.")
                throw cancelled
            } catch (failure: Exception) {
                if (!coroutine.isActive) update(LocalModelStatus.ABSENT, message = "Download cancelado.")
                coroutine.ensureActive()
                update(LocalModelStatus.FAILED, message = failure.message ?: "Falha no download. Verifique a conexão e tente novamente.")
                throw AiException(_state.value.message.orEmpty(), failure)
            } finally {
                call?.cancel()
                partial.delete()
            }
        }
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

    private fun update(status: LocalModelStatus, bytes: Long = 0, message: String? = null) {
        _state.value = LocalModelState(status, bytes, spec.bytes, message)
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
}
