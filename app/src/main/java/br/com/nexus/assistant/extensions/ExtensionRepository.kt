package br.com.nexus.assistant.extensions

import android.content.Context
import br.com.nexus.assistant.data.local.LocalModelManager
import br.com.nexus.assistant.data.local.LocalModelStatus
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExtensionPackStatus { CHECKING, ABSENT, DOWNLOADING, VERIFYING, READY, BUNDLED, PAUSED, FAILED }

data class ExtensionPackState(
    val status: ExtensionPackStatus = ExtensionPackStatus.CHECKING,
    val installedBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val message: String? = null,
)

/**
 * Optional model data only. The existing bounded HTTPS/SHA downloader is reused;
 * opening a feature or refreshing this inventory never starts a network request.
 */
class ExtensionRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val managers = ExtensionCatalog.packs.flatMap { it.files }.associateWith { file ->
        LocalModelManager(ExtensionDataFiles.destination(app.filesDir, file).parentFile!!, file.model)
    }
    private val bundled = ConcurrentHashMap<ExtensionPackId, Boolean>()
    private val operations = ConcurrentHashMap<ExtensionPackId, Job>()
    private val access = PackAccess()
    private val _states = MutableStateFlow(ExtensionCatalog.packs.associate { it.id to ExtensionPackState(totalBytes = it.bytes) })
    val states = _states.asStateFlow()

    init {
        managers.values.forEach { manager -> scope.launch { manager.state.collect { publish() } } }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        for (pack in ExtensionCatalog.packs) {
            for (file in pack.files) {
                ExtensionDataFiles.destination(app.filesDir, file)
                managers.getValue(file).refresh()
            }
            bundled[pack.id] = pack.files.all(::verifiedAsset)
        }
        publish()
    }

    /** Invoke only after explicit download confirmation in the extension center. */
    suspend fun download(id: ExtensionPackId) = coroutineScope {
        val guard = access.mutate(id)
        val job = currentCoroutineContext()[Job]!!
        try {
            check(operations.putIfAbsent(id, job) == null) { "O pacote já tem uma operação em andamento." }
            for (file in ExtensionCatalog.pack(id).files) {
                ExtensionDataFiles.destination(app.filesDir, file)
                val manager = managers.getValue(file)
                manager.download()
                val result = manager.state.value
                if (result.status != LocalModelStatus.READY) {
                    throw ExtensionUnavailableException(result.message
                        ?: "O download não foi concluído e verificado. Tente novamente pela Central de Extensões.")
                }
            }
        } finally {
            operations.remove(id, job)
            guard.close()
            publish()
        }
    }

    fun cancel(id: ExtensionPackId) { operations[id]?.cancel(CancellationException("Download pausado pelo usuário.")) }

    suspend fun remove(id: ExtensionPackId) = withContext(Dispatchers.IO) {
        val guard = access.mutate(id)
        try {
            for (file in ExtensionCatalog.pack(id).files) {
                ExtensionDataFiles.destination(app.filesDir, file)
                managers.getValue(file).delete()
            }
        } finally { guard.close(); publish() }
    }

    /** A classifier or OCR engine retains this lease until it releases its model. */
    fun acquire(id: ExtensionPackId): AutoCloseable = access.acquire(id)

    /** Blocking data resolution belongs on the vision worker, never the UI thread. */
    fun verifiedPrivateFile(file: ExtensionDataFile): File? {
        val result = ExtensionDataFiles.destination(app.filesDir, file)
        return result.takeIf { ExtensionDataFiles.verified(it, file) }
    }

    private fun verifiedAsset(file: ExtensionDataFile): Boolean = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        app.assets.open(file.assetPath).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > file.model.bytes) return false
                digest.update(buffer, 0, count)
            }
        }
        total == file.model.bytes && digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == file.model.sha256
    }.getOrDefault(false)

    private fun publish() {
        _states.value = ExtensionCatalog.packs.associate { pack ->
            val values = pack.files.map { managers.getValue(it).state.value }
            val status = when {
                values.any { it.status in setOf(LocalModelStatus.CONNECTING, LocalModelStatus.DOWNLOADING, LocalModelStatus.COPYING) } -> ExtensionPackStatus.DOWNLOADING
                values.any { it.status == LocalModelStatus.VERIFYING } -> ExtensionPackStatus.VERIFYING
                values.all { it.status == LocalModelStatus.READY } -> ExtensionPackStatus.READY
                bundled[pack.id] == true -> ExtensionPackStatus.BUNDLED
                values.any { it.status == LocalModelStatus.PAUSED } -> ExtensionPackStatus.PAUSED
                values.any { it.status == LocalModelStatus.FAILED } -> ExtensionPackStatus.FAILED
                values.any { it.status == LocalModelStatus.CHECKING } -> ExtensionPackStatus.CHECKING
                else -> ExtensionPackStatus.ABSENT
            }
            val installed = pack.files.sumOf { file ->
                runCatching { ExtensionDataFiles.destination(app.filesDir, file).takeIf(File::isFile)?.length() ?: 0 }.getOrDefault(0)
            }
            pack.id to ExtensionPackState(status, installed, values.sumOf { it.downloadedBytes }, pack.bytes,
                values.firstOrNull { it.status in setOf(LocalModelStatus.FAILED, LocalModelStatus.PAUSED) }?.message)
        }
    }

    companion object {
        @Volatile private var instance: ExtensionRepository? = null
        fun get(context: Context): ExtensionRepository = instance ?: synchronized(this) {
            instance ?: ExtensionRepository(context).also { instance = it }
        }
    }
}

