package br.com.nexus.assistant.device

import br.com.nexus.assistant.data.local.LocalModelCatalog
import br.com.nexus.assistant.data.local.LocalModelProfile
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Sparse files exercise actual metadata reads without downloading model weights. */
class DeviceModelInventoryTest {
    @Test fun aCompletePartialIsAvailableForPreparationButIsNotAnInstalledModel() {
        withDirectory { directory ->
            sparse(File(directory, "${LocalModelCatalog.compact.fileName}.part"), LocalModelCatalog.compact.bytes)
            val inventory = readLocalModelFileInventory(directory)
            assertTrue(inventory.installedProfiles.isEmpty())
            assertEquals(LocalModelCatalog.compact.bytes, inventory.downloadedBytes[LocalModelProfile.COMPACT])
        }
    }

    @Test fun anExactFinalFileTakesPrecedenceOverAnOldPartial() {
        withDirectory { directory ->
            sparse(File(directory, LocalModelCatalog.compact.fileName), LocalModelCatalog.compact.bytes)
            sparse(File(directory, "${LocalModelCatalog.compact.fileName}.part"), 4_096)
            val inventory = readLocalModelFileInventory(directory)
            assertEquals(setOf(LocalModelProfile.COMPACT), inventory.installedProfiles)
            assertEquals(LocalModelCatalog.compact.bytes, inventory.downloadedBytes[LocalModelProfile.COMPACT])
        }
    }

    @Test fun incompleteFinalDoesNotHideAValidPartialForEitherProfile() {
        withDirectory { directory ->
            sparse(File(directory, LocalModelCatalog.compact.fileName), 4_096)
            sparse(File(directory, "${LocalModelCatalog.compact.fileName}.part"), 8_192)
            sparse(File(directory, "${LocalModelCatalog.balanced.fileName}.part"), LocalModelCatalog.balanced.bytes)
            val inventory = readLocalModelFileInventory(directory)
            assertTrue(inventory.installedProfiles.isEmpty())
            assertEquals(8_192L, inventory.downloadedBytes[LocalModelProfile.COMPACT])
            assertEquals(LocalModelCatalog.balanced.bytes, inventory.downloadedBytes[LocalModelProfile.BALANCED])
        }
    }

    @Test fun emptyOversizedAndDirectoryCandidatesCannotReduceRequiredStorage() {
        withDirectory { directory ->
            val partial = File(directory, "${LocalModelCatalog.compact.fileName}.part")
            val finalFile = File(directory, LocalModelCatalog.compact.fileName)
            for (size in listOf(0L, LocalModelCatalog.compact.bytes + 1)) {
                sparse(partial, size)
                sparse(finalFile, size)
                val inventory = readLocalModelFileInventory(directory)
                assertTrue(inventory.installedProfiles.isEmpty())
                assertTrue(inventory.downloadedBytes.isEmpty())
            }
            check(partial.delete())
            check(finalFile.delete())
            check(partial.mkdir())
            check(finalFile.mkdir())
            val inventory = readLocalModelFileInventory(directory)
            assertTrue(inventory.installedProfiles.isEmpty())
            assertTrue(inventory.downloadedBytes.isEmpty())
        }
    }

    @Test fun symbolicLinksAreNotCountedAsPrivateFinalOrPartialFiles() {
        withDirectory { directory ->
            val target = File(directory, "outside-model").apply { writeText("fixture") }
            Files.createSymbolicLink(File(directory, LocalModelCatalog.compact.fileName).toPath(), target.toPath())
            Files.createSymbolicLink(File(directory, "${LocalModelCatalog.compact.fileName}.part").toPath(), target.toPath())
            val inventory = readLocalModelFileInventory(directory)
            assertTrue(inventory.installedProfiles.isEmpty())
            assertTrue(inventory.downloadedBytes.isEmpty())
        }
    }

    private fun sparse(file: File, size: Long) = RandomAccessFile(file, "rw").use { it.setLength(size) }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("nexus-model-inventory-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
