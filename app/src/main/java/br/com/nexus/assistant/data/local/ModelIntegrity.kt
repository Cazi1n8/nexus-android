package br.com.nexus.assistant.data.local

import java.io.File
import java.security.MessageDigest

/** Pure JVM verifier shared by startup validation and regression tests. */
object ModelIntegrity {
    fun verify(file: File, spec: LocalModelSpec, checkCancellation: () -> Unit = {}): Boolean {
        return verifyWithProgress(file, spec, checkCancellation)
    }

    fun verifyWithProgress(
        file: File,
        spec: LocalModelSpec,
        checkCancellation: () -> Unit = {},
        onProgress: (Long) -> Unit = {},
    ): Boolean {
        if (!file.isFile || file.length() != spec.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        var verified = 0L
        onProgress(verified)
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkCancellation()
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
                verified += count
                onProgress(verified)
            }
        }
        checkCancellation()
        return digest.digest().toHex() == spec.sha256
    }

    internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
