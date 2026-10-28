package br.com.nexus.assistant.storage

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

enum class StorageCategory(val title: String, val explanation: String) {
    MODELS("Modelos de IA offline", "Pesos já baixados ou copiados para os dados privados."),
    PARTIAL_MODELS("Downloads de IA incompletos", "Partes preservadas para continuar o download, sem contar como modelo pronto."),
    EXTENSION_PACKS("Pacotes de extensões", "Arquivos opcionais de visão e leitura de texto, incluindo o OCR das versões anteriores."),
    IMAGES("Imagens das conversas", "Fotos e anexos mantidos no armazenamento privado."),
    DATABASES("Conversas e histórico", "Banco local, mensagens, memórias e arquivos auxiliares do banco."),
    PREFERENCES("Configurações e progresso", "Preferências, gestos e progresso salvos no aparelho."),
    OTHER_DATA("Outros dados privados", "Demais arquivos internos que não são cache nem pacotes reconhecidos."),
    CACHE("Cache temporário", "Arquivos temporários do NEXUS; modelos e conversas ficam fora desta pasta."),
    CODE_CACHE("Cache de código", "Cache do Android separado do cache temporário; não é apagado por esta tela."),
}

data class StorageBucket(val category: StorageCategory, val bytes: Long, val files: Int)

data class StorageFileScan(
    val buckets: List<StorageBucket>,
    val unreadableItems: Int,
    val skippedSymlinks: Int,
) {
    val totalBytes: Long get() = buckets.sumOf { it.bytes }
    fun bytes(category: StorageCategory): Long = buckets.firstOrNull { it.category == category }?.bytes ?: 0L
    val cacheBytes: Long get() = bytes(StorageCategory.CACHE) + bytes(StorageCategory.CODE_CACHE)
    val dataBytesExcludingCache: Long get() = totalBytes - cacheBytes
}

data class StorageCleanupResult(
    /** Distinct logical file bytes removed, not a guarantee of reclaimed filesystem blocks. */
    val removedBytes: Long,
    val removedFiles: Int,
    val failures: Int,
    val skippedSymlinks: Int,
)

/** Reads file metadata, never content. Symlinks are not followed and hard links count once. */
class StorageFileScanner {
    fun scan(root: File): StorageFileScan {
        val base = root.toPath().toAbsolutePath().normalize()
        val bytes = StorageCategory.entries.associateWith { 0L }.toMutableMap()
        val counts = StorageCategory.entries.associateWith { 0 }.toMutableMap()
        val seenFiles = HashSet<Any>()
        var failures = 0
        var links = 0
        if (!Files.exists(base, LinkOption.NOFOLLOW_LINKS)) {
            return StorageFileScan(StorageCategory.entries.map { StorageBucket(it, 0, 0) }, 0, 0)
        }
        try {
            Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    if (attributes.isSymbolicLink) { links++; return FileVisitResult.CONTINUE }
                    if (!attributes.isRegularFile) return FileVisitResult.CONTINUE
                    val identity = attributes.fileKey() ?: file.toAbsolutePath().normalize()
                    if (!seenFiles.add(identity)) return FileVisitResult.CONTINUE
                    val category = classifyPrivatePath(base.relativize(file))
                    bytes[category] = bytes.getValue(category) + attributes.size().coerceAtLeast(0)
                    counts[category] = counts.getValue(category) + 1
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                    failures++
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
                    if (error != null) failures++
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (_: IOException) { failures++
        } catch (_: SecurityException) { failures++ }
        return StorageFileScan(StorageCategory.entries.map { StorageBucket(it, bytes.getValue(it), counts.getValue(it)) }, failures, links)
    }

    /** Caller must stop active imports/captures first. Only the explicitly supplied cache root is touched. */
    fun clearCacheDirectory(cacheRoot: File): StorageCleanupResult {
        val base = cacheRoot.toPath().toAbsolutePath().normalize()
        var removed = 0L
        var files = 0
        var failures = 0
        var links = 0
        val seenFiles = HashSet<Any>()
        if (!Files.exists(base, LinkOption.NOFOLLOW_LINKS)) return StorageCleanupResult(0, 0, 0, 0)
        try {
            Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    if (attributes.isSymbolicLink) { links++; return FileVisitResult.CONTINUE }
                    if (!attributes.isRegularFile) return FileVisitResult.CONTINUE
                    try {
                        if (Files.deleteIfExists(file)) {
                            if (seenFiles.add(attributes.fileKey() ?: file.toAbsolutePath().normalize())) {
                                removed += attributes.size().coerceAtLeast(0)
                            }
                            files++
                        }
                    } catch (_: IOException) { failures++
                    } catch (_: SecurityException) { failures++ }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                    failures++
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
                    if (error != null) failures++
                    if (directory != base) {
                        try { Files.deleteIfExists(directory)
                        } catch (_: IOException) { failures++
                        } catch (_: SecurityException) { failures++ }
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (_: IOException) { failures++
        } catch (_: SecurityException) { failures++ }
        return StorageCleanupResult(removed, files, failures, links)
    }

    companion object {
        fun classifyPrivatePath(relative: Path): StorageCategory {
            val parts = relative.map { it.toString() }
            val first = parts.firstOrNull().orEmpty()
            val second = parts.getOrNull(1).orEmpty()
            return when {
                first == "cache" -> StorageCategory.CACHE
                first == "code_cache" -> StorageCategory.CODE_CACHE
                first == "databases" -> StorageCategory.DATABASES
                first == "shared_prefs" || first == "files" && second == "datastore" -> StorageCategory.PREFERENCES
                first == "files" && second == "models" -> if (parts.last().lowercase(Locale.ROOT).endsWith(".part")) {
                    StorageCategory.PARTIAL_MODELS
                } else StorageCategory.MODELS
                first == "files" && second in setOf("extensions", "ocr") -> StorageCategory.EXTENSION_PACKS
                first == "files" && second == "chat-images" -> StorageCategory.IMAGES
                else -> StorageCategory.OTHER_DATA
            }
        }
    }
}
