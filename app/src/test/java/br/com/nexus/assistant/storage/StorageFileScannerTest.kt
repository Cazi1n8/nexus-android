package br.com.nexus.assistant.storage

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageFileScannerTest {
    private fun withRoot(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("nexus-storage-test-")
        try { block(root)
        } finally { Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }

    private fun write(root: Path, name: String, bytes: Int): Path = root.resolve(name).also {
        Files.createDirectories(it.parent)
        Files.write(it, ByteArray(bytes) { 42 })
    }

    @Test fun separatesModelDownloadExtensionHistoryAndBothCacheRoots() = withRoot { root ->
        write(root, "files/models/qwen.gguf", 101)
        write(root, "files/models/qwen.gguf.part", 11)
        write(root, "files/extensions/vision/object.tflite", 13)
        write(root, "files/ocr/tessdata/por.traineddata", 17)
        write(root, "files/chat-images/photo.jpg", 19)
        write(root, "databases/nexus.db", 23)
        write(root, "databases/nexus.db-wal", 29)
        write(root, "files/datastore/nexus.preferences_pb", 31)
        write(root, "shared_prefs/nexus_gestures.xml", 37)
        write(root, "no_backup/private", 41)
        write(root, "cache/nexus-import-1.tmp", 43)
        write(root, "code_cache/compiler", 47)
        val scan = StorageFileScanner().scan(root.toFile())
        assertEquals(101, scan.bytes(StorageCategory.MODELS))
        assertEquals(11, scan.bytes(StorageCategory.PARTIAL_MODELS))
        assertEquals(30, scan.bytes(StorageCategory.EXTENSION_PACKS))
        assertEquals(19, scan.bytes(StorageCategory.IMAGES))
        assertEquals(52, scan.bytes(StorageCategory.DATABASES))
        assertEquals(68, scan.bytes(StorageCategory.PREFERENCES))
        assertEquals(41, scan.bytes(StorageCategory.OTHER_DATA))
        assertEquals(90, scan.cacheBytes)
        assertEquals(412, scan.totalBytes)
        assertEquals(322, scan.dataBytesExcludingCache)
        assertEquals(0, scan.unreadableItems)
    }

    @Test fun countsAHardLinkedFileOnce() = withRoot { root ->
        val first = write(root, "files/models/a.gguf", 89)
        Files.createLink(root.resolve("files/models/b.gguf"), first)
        val scan = StorageFileScanner().scan(root.toFile())
        assertEquals(89, scan.totalBytes)
        assertEquals(1, scan.buckets.sumOf { it.files })
    }

    @Test fun ignoresSymlinksToFilesAndDirectoriesOutsideTheMeasuredRoot() = withRoot { root ->
        val measured = root.resolve("app"); Files.createDirectories(measured)
        val outside = write(root, "outside/private.txt", 1000)
        write(measured, "cache/temp", 7)
        Files.createSymbolicLink(measured.resolve("cache/file-link"), outside)
        Files.createSymbolicLink(measured.resolve("cache/directory-link"), outside.parent)
        val scan = StorageFileScanner().scan(measured.toFile())
        assertEquals(7, scan.totalBytes)
        assertEquals(2, scan.skippedSymlinks)
        assertEquals(1000, Files.size(outside))
    }

    @Test fun cleanupPreservesModelsHistoryPreferencesAndCodeCache() = withRoot { root ->
        val model = write(root, "files/models/a.gguf", 17)
        val database = write(root, "databases/nexus.db", 19)
        val preference = write(root, "shared_prefs/settings.xml", 23)
        val code = write(root, "code_cache/generated", 29)
        val temporary = write(root, "cache/images/photo.jpg", 31)
        val result = StorageFileScanner().clearCacheDirectory(root.resolve("cache").toFile())
        assertEquals(31, result.removedBytes)
        assertEquals(1, result.removedFiles)
        assertEquals(0, result.failures)
        assertFalse(Files.exists(temporary))
        assertTrue(Files.isDirectory(root.resolve("cache")))
        for (path in listOf(model, database, preference, code)) assertTrue(Files.isRegularFile(path))
    }

    @Test fun cleanupNeverFollowsLinksAndKeepsTheirTargets() = withRoot { root ->
        val outside = write(root, "files/models/keep.gguf", 53)
        Files.createDirectories(root.resolve("cache"))
        Files.createSymbolicLink(root.resolve("cache/model-link"), outside)
        val result = StorageFileScanner().clearCacheDirectory(root.resolve("cache").toFile())
        assertEquals(0, result.removedBytes)
        assertEquals(1, result.skippedSymlinks)
        assertEquals(53, Files.size(outside))
        assertTrue(Files.isSymbolicLink(root.resolve("cache/model-link")))
    }

    @Test fun cleanupDoesNotCountHardLinkBytesTwice() = withRoot { root ->
        val first = write(root, "cache/a", 59)
        Files.createLink(root.resolve("cache/b"), first)
        val result = StorageFileScanner().clearCacheDirectory(root.resolve("cache").toFile())
        assertEquals(59, result.removedBytes)
        assertEquals(2, result.removedFiles)
    }

    @Test fun missingDirectoryHasZeroUsageAndCleanupIsHarmless() = withRoot { root ->
        val missing = root.resolve("not-created").toFile()
        assertEquals(0, StorageFileScanner().scan(missing).totalBytes)
        assertEquals(StorageCleanupResult(0, 0, 0, 0), StorageFileScanner().clearCacheDirectory(missing))
    }

    @Test fun androidTotalsIncludeCacheExactlyOnce() {
        val totals = SystemStorageTotals(appBytes = 1_900_000_000, dataBytesIncludingCache = 511_510_000, cacheBytes = 13_510_000)
        assertEquals(498_000_000, totals.dataBytesExcludingCache)
        assertEquals(2_411_510_000, totals.totalBytes)
        assertEquals(totals.totalBytes, totals.appBytes + totals.dataBytesExcludingCache + totals.cacheBytes)
    }
}
