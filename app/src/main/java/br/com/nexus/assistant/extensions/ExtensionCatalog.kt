package br.com.nexus.assistant.extensions

import br.com.nexus.assistant.data.local.LocalModelSpec
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

enum class ExtensionPackId { VISION_OBJECTS, VISION_OCR }

data class ExtensionDataFile(
    val assetPath: String,
    val relativeDirectory: String,
    val model: LocalModelSpec,
)

data class ExtensionPack(val id: ExtensionPackId, val title: String, val description: String, val files: List<ExtensionDataFile>) {
    val bytes: Long get() = files.sumOf { it.model.bytes }
}

/** Only data files from pinned public sources. No executable/plugin loader is involved. */
object ExtensionCatalog {
    val objects = ExtensionPack(ExtensionPackId.VISION_OBJECTS, "NEXUS Vision · objetos",
        "Classificação local de categorias em fotos e quadros. Não é compreensão geral de imagens.", listOf(
            ExtensionDataFile("vision/efficientnet_lite0_int8.tflite", "extensions/vision",
                LocalModelSpec("efficientnet_lite0_int8.tflite", "EfficientNet-Lite0 INT8",
                    "https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/int8/1/efficientnet_lite0.tflite",
                    5_434_517, "bc2ffe19c1118de0c0c2a9088992da5589722656e0fba81421385300a4a34b16"))))
    private const val OCR_REVISION = "87416418657359cb625c412a48b6e1d6d41c29bd"
    val ocr = ExtensionPack(ExtensionPackId.VISION_OCR, "NEXUS Vision · leitura de texto",
        "OCR offline em português e inglês. Reaproveita os dados já preparados pela versão anterior.", listOf(
            ocrFile("por", 1_982_756, "c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb"),
            ocrFile("eng", 4_113_088, "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2")))
    val packs = listOf(objects, ocr)
    fun pack(id: ExtensionPackId): ExtensionPack = packs.first { it.id == id }
    private fun ocrFile(language: String, size: Long, sha: String) = ExtensionDataFile(
        "ocr/$language.traineddata", "ocr/tessdata",
        LocalModelSpec("$language.traineddata", "OCR $language · tessdata_fast",
            "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/$OCR_REVISION/$language.traineddata", size, sha))
}

/** Resolve known private data paths without following symbolic links out of the app's files. */
object ExtensionDataFiles {
    fun destination(root: File, file: ExtensionDataFile): File {
        require(file.relativeDirectory.split('/').all { it.matches(Regex("[a-zA-Z0-9_-]+")) })
        require(file.model.fileName.matches(Regex("[a-zA-Z0-9_.-]+")) && file.model.fileName !in setOf(".", ".."))
        val result = File(File(root, file.relativeDirectory), file.model.fileName)
        var component: File? = result
        while (component != null && component != root) {
            check(!Files.isSymbolicLink(component.toPath())) { "O pacote contém um caminho simbólico inválido." }
            component = component.parentFile
        }
        check(result.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) { "O pacote precisa ficar no armazenamento privado." }
        check(!Files.isSymbolicLink(File(result.parentFile, "${result.name}.part").toPath())) { "O download parcial contém um caminho inválido." }
        return result
    }

    fun verified(file: File, spec: ExtensionDataFile): Boolean {
        if (Files.isSymbolicLink(file.toPath()) || !file.isFile || file.length() != spec.model.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == spec.model.sha256
    }
}
