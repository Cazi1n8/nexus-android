package br.com.nexus.assistant.data.local

/**
 * llama.cpp CPU bridge. Call load/generate/close on Dispatchers.IO, never on the
 * main thread. cancel is lock-free with respect to inference and may be called
 * from a coroutine cancellation handler. The downloaded GGUF is not in the APK.
 * All chat bytes use ordinary UTF-8, including accents, emoji and supplementary
 * characters; JNI's modified UTF-8 string conversion is deliberately avoided.
 */
object NexusNativeBridge {
    val supports64BitProcess: Boolean by lazy {
        runCatching { android.os.Process.is64Bit() }.getOrDefault(false)
    }
    private val loadResult: Result<Unit> by lazy {
        runCatching { System.loadLibrary("nexus_llama") }
    }

    val available: Boolean get() = supports64BitProcess && loadResult.isSuccess

    fun load(path: String, nCtx: Int = 1024, threads: Int = 2): Long {
        ensureAvailable()
        return nativeLoad(path.toByteArray(Charsets.UTF_8), nCtx, threads)
    }

    /** chatJson is a JSON array of {role, content}, with one optional first system message. */
    fun generate(handle: Long, chatJson: String, maxTokens: Int): ByteArray {
        ensureAvailable()
        return nativeGenerate(handle, chatJson.toByteArray(Charsets.UTF_8), maxTokens)
    }

    fun cancel(handle: Long) { if (available && handle != 0L) nativeCancel(handle) }
    fun close(handle: Long) { if (available && handle != 0L) nativeClose(handle) }

    private fun ensureAvailable() {
        if (!supports64BitProcess) throw IllegalStateException("A IA local requer Android com processo de 64 bits. Neste aparelho, use um provedor personalizado nas Configurações.")
        if (!available) throw IllegalStateException("A IA local não está disponível para a arquitetura deste aparelho. Instale a versão compatível do NEXUS.")
    }

    private external fun nativeLoad(pathUtf8: ByteArray, nCtx: Int, threads: Int): Long
    private external fun nativeGenerate(handle: Long, chatJsonUtf8: ByteArray, maxTokens: Int): ByteArray
    private external fun nativeCancel(handle: Long)
    private external fun nativeClose(handle: Long)
}
