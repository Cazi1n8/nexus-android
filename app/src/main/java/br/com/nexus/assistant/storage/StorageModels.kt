package br.com.nexus.assistant.storage

/** The OS data count includes cache. Keep this invariant explicit in the UI and arithmetic. */
data class SystemStorageTotals(val appBytes: Long, val dataBytesIncludingCache: Long, val cacheBytes: Long) {
    val dataBytesExcludingCache: Long get() = (dataBytesIncludingCache - cacheBytes).coerceAtLeast(0)
    val totalBytes: Long get() = appBytes + dataBytesIncludingCache
}

data class StorageSnapshot(
    val measuredAtMillis: Long,
    val privateFiles: StorageFileScan,
    val system: SystemStorageTotals?,
    val apkBytes: Long,
    val apkFiles: Int,
    val extractedNativeBytes: Long,
    val bundledGenerativeModelBytes: Long,
    val packageReadErrors: Int,
    val freeDataBytes: Long,
)
