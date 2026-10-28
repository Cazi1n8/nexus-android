package br.com.nexus.assistant.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Measures this app only. No usage-stats permission, device inventory, upload or private-content log. */
class StorageRepository(context: Context) {
    private val application = context.applicationContext

    suspend fun measure(): StorageSnapshot = withContext(Dispatchers.IO) {
        val scanner = StorageFileScanner()
        val files = scanner.scan(application.dataDir)
        val info = application.applicationInfo
        var errors = 0
        var apkBytes = 0L
        var bundledModels = 0L
        var apkFiles = 0
        val packages = (listOfNotNull(info.sourceDir) + info.splitSourceDirs.orEmpty()).distinct()
        for (path in packages) {
            try {
                val file = File(path)
                check(file.isFile)
                apkBytes += file.length()
                apkFiles++
                ZipFile(file).use { archive ->
                    val entries = archive.entries()
                    while (entries.hasMoreElements()) {
                        val item = entries.nextElement()
                        if (!item.isDirectory && item.name.startsWith("assets/models/") && item.name.endsWith(".gguf")) {
                            bundledModels += item.size.coerceAtLeast(0)
                        }
                    }
                }
            } catch (_: Exception) { errors++ }
        }
        val natives = info.nativeLibraryDir?.let { scanner.scan(File(it)) }
        errors += natives?.unreadableItems ?: 0
        val system = try {
            val manager = application.getSystemService(StorageStatsManager::class.java)
            val stats = manager?.queryStatsForUid(info.storageUuid ?: StorageManager.UUID_DEFAULT, Process.myUid())
            stats?.let { SystemStorageTotals(it.appBytes.coerceAtLeast(0), it.dataBytes.coerceAtLeast(0), it.cacheBytes.coerceAtLeast(0)) }
        } catch (_: Exception) { null }
        StorageSnapshot(
            measuredAtMillis = System.currentTimeMillis(), privateFiles = files, system = system,
            apkBytes = apkBytes, apkFiles = apkFiles, extractedNativeBytes = natives?.totalBytes ?: 0,
            bundledGenerativeModelBytes = bundledModels, packageReadErrors = errors,
            freeDataBytes = application.filesDir.usableSpace.coerceAtLeast(0),
        )
    }

    /** Invoke only after the app has cancelled and awaited its active cache-file consumers. */
    suspend fun clearTemporaryCache(): StorageCleanupResult = withContext(Dispatchers.IO) {
        StorageFileScanner().clearCacheDirectory(application.cacheDir)
    }
}
