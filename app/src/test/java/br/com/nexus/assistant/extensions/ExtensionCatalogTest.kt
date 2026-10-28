package br.com.nexus.assistant.extensions

import br.com.nexus.assistant.data.local.LocalModelSpec
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExtensionCatalogTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun optionalCatalogContainsOnlyPinnedDataAndKeepsHandTrackingOut() {
        assertEquals(5_434_517L, ExtensionCatalog.objects.bytes)
        assertEquals(6_095_844L, ExtensionCatalog.ocr.bytes)
        assertEquals(11_530_361L, ExtensionCatalog.packs.sumOf { it.bytes })
        val all = ExtensionCatalog.packs.flatMap { it.files }
        assertEquals(all.size, all.map { it.assetPath }.distinct().size)
        all.forEach {
            assertTrue(it.model.downloadUrl.startsWith("https://"))
            assertTrue(it.model.sha256.matches(Regex("[a-f0-9]{64}")))
            assertTrue(it.model.fileName.endsWith(".tflite") || it.model.fileName.endsWith(".traineddata"))
            assertFalse(it.model.fileName.contains("hand"))
        }
        assertTrue(ExtensionCatalog.ocr.files.all { it.relativeDirectory == "ocr/tessdata" })
    }

    @Test fun exactSizeAndHashAreBothRequiredForPrivateData() {
        val bytes = "model-data".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val spec = ExtensionDataFile("vision/data.tflite", "extensions/vision", LocalModelSpec(
            "data.tflite", "synthetic fixture", "https://example.com/data.tflite", bytes.size.toLong(), sha))
        val file = ExtensionDataFiles.destination(temporary.root, spec)
        file.parentFile!!.mkdirs()
        file.writeBytes(bytes)
        assertTrue(ExtensionDataFiles.verified(file, spec))
        file.writeText("wrong-data")
        assertEquals(bytes.size.toLong(), file.length())
        assertFalse(ExtensionDataFiles.verified(file, spec))
        file.writeBytes(bytes + 1)
        assertFalse(ExtensionDataFiles.verified(file, spec))
    }

    @Test fun pathsCannotEscapePrivateFilesOrFollowSymbolicDirectories() {
        val root = temporary.newFolder("private")
        val spec = ExtensionCatalog.objects.files.single()
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionDataFiles.destination(root, spec.copy(relativeDirectory = "../outside"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionDataFiles.destination(root, spec.copy(model = spec.model.copy(fileName = "../model.tflite")))
        }
        Files.createSymbolicLink(File(root, "extensions").toPath(), temporary.newFolder("outside").toPath())
        assertThrows(IllegalStateException::class.java) { ExtensionDataFiles.destination(root, spec) }
    }

    @Test fun aPartialSymlinkIsRejectedBeforeTheDownloaderOpensIt() {
        val file = ExtensionDataFiles.destination(temporary.root, ExtensionCatalog.objects.files.single())
        file.parentFile!!.mkdirs()
        Files.createSymbolicLink(File(file.parentFile, file.name + ".part").toPath(), temporary.newFile("outside").toPath())
        assertThrows(IllegalStateException::class.java) {
            ExtensionDataFiles.destination(temporary.root, ExtensionCatalog.objects.files.single())
        }
    }
}
