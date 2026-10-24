package br.com.nexus.assistant.data.local

import br.com.nexus.assistant.data.AiException
import br.com.nexus.assistant.data.AiMessage
import br.com.nexus.assistant.data.AiProvider
import br.com.nexus.assistant.data.NexusSettings
import br.com.nexus.assistant.device.DevicePolicy
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** CPU inference through the real llama.cpp JNI; no remote fallback or canned answer. */
class LocalAiProvider(
    private val models: LocalModelManager,
    private val resourcePolicy: () -> DevicePolicy? = { null },
) : AiProvider, AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "nexus-local-ai").apply { isDaemon = true } }
    private val mutex = Mutex()
    private val handle = AtomicLong(0)
    private val epoch = AtomicLong(0)
    private val closed = AtomicBoolean(false)

    override suspend fun complete(
        settings: NexusSettings,
        token: String,
        messages: List<AiMessage>,
        memories: List<String>,
    ): String = mutex.withLock {
        val policy = resourcePolicy()
        if (policy != null && !policy.localAiSupported) throw AiException(policy.reason)
        val contextTokens = policy?.contextTokens ?: 1024
        val threads = policy?.inferenceThreads ?: 2
        val outputTokens = policy?.maxOutputTokens ?: 192
        val path = models.requireReadyFile().absolutePath
        val chat = LocalChatContext.build(settings, messages, memories, contextTokens)
        val requestEpoch = epoch.get()
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                epoch.incrementAndGet()
                // Closing invalidates the native handle even if cancellation races with
                // entering generate(). A later generation must load a fresh engine.
                closeNative()
            }
            try {
                worker.execute {
                    try {
                        if (!continuation.isActive) return@execute
                        if (closed.get() || requestEpoch != epoch.get()) throw CancellationException("A sessão local foi interrompida.")
                        var native = handle.get()
                        if (native == 0L) {
                            native = NexusNativeBridge.load(path, nCtx = contextTokens, threads = threads)
                            handle.set(native)
                        }
                        // Loading is blocking; cancellation can occur before there is a handle.
                        if (!continuation.isActive || closed.get() || requestEpoch != epoch.get()) {
                            closeNative()
                            throw CancellationException("A sessão local foi interrompida.")
                        }
                        val answer = NexusNativeBridge.generate(native, chat, maxTokens = outputTokens).toString(Charsets.UTF_8).trim()
                        if (requestEpoch != epoch.get()) throw CancellationException("A sessão local foi interrompida.")
                        if (answer.isBlank()) throw AiException("O modelo local não gerou texto. Tente uma pergunta mais curta.")
                        if (continuation.isActive) continuation.resume(answer)
                    } catch (failure: Throwable) {
                        if (failure !is CancellationException) closeNative()
                        if (!continuation.isActive) return@execute
                        val translated = when (failure) {
                            is CancellationException -> failure
                            is UnsatisfiedLinkError -> AiException("Este APK não contém o runtime local compatível com o processador do aparelho.", failure)
                            is OutOfMemoryError -> AiException("Não há RAM livre suficiente para carregar o modelo local. Feche outros aplicativos e tente novamente.", failure)
                            is Exception -> AiException(failure.message ?: "Falha na inferência local.", failure)
                            else -> failure
                        }
                        continuation.resumeWithException(translated)
                    }
                }
            } catch (rejected: RejectedExecutionException) {
                continuation.resumeWithException(AiException("A sessão de IA local foi encerrada.", rejected))
            }
        }
    }

    /** Queue the release after any active generation has observed cancellation. */
    fun release() {
        epoch.incrementAndGet()
        closeNative()
        if (!closed.get()) runCatching { worker.execute { closeNative() } }
    }

    suspend fun releaseAndAwait() {
        epoch.incrementAndGet()
        closeNative()
        if (closed.get()) return
        suspendCancellableCoroutine<Unit> { continuation ->
            try {
                worker.execute {
                    closeNative()
                    if (continuation.isActive) continuation.resume(Unit)
                }
            } catch (failure: RejectedExecutionException) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private fun closeNative() { handle.getAndSet(0).takeIf { it != 0L }?.let { runCatching { NexusNativeBridge.close(it) } } }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        epoch.incrementAndGet()
        closeNative()
        runCatching { worker.execute { closeNative() } }
        worker.shutdown()
    }
}
