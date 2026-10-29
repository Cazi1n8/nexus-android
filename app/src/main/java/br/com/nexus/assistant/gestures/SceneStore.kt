package br.com.nexus.assistant.gestures

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One local scene, app-private and atomic; never a remote asset or executable extension. */
class SceneStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir,"nexus3d/scene.json"))

    suspend fun save(scene: SceneState) {
        val ticket = revision.get()
        val data = SceneCodec.encode(scene).toByteArray(Charsets.UTF_8)
        withContext(Dispatchers.IO) {
            lock.withLock {
                check(ticket == revision.get()) { "A cena foi apagada enquanto esse salvamento aguardava." }
                val parent = file.baseFile.parentFile ?: throw IOException("Pasta da cena indisponível.")
                check(parent.isDirectory || parent.mkdirs()) { "Não foi possível criar a pasta privada da cena." }
                val stream = file.startWrite()
                try {
                    stream.write(data)
                    file.finishWrite(stream)
                } catch (failure: Exception) {
                    file.failWrite(stream)
                    throw IOException("Não foi possível salvar a cena local. Tente novamente.",failure)
                }
            }
        }
    }

    suspend fun load(): SceneState? = withContext(Dispatchers.IO) {
        lock.withLock {
            val input = try { file.openRead() } catch (_: FileNotFoundException) { return@withLock null }
            try {
                val output = ByteArrayOutputStream()
                input.use { stream ->
                    val buffer = ByteArray(4 * 1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        check(output.size() + read <= SceneCodec.MAX_BYTES) { "O arquivo da cena ultrapassa o limite." }
                        output.write(buffer,0,read)
                    }
                }
                val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
                SceneCodec.decode(text)
            } catch (failure: Exception) {
                throw IOException("A cena salva não pôde ser aberta. A cena atual foi preservada.",failure)
            }
        }
    }

    suspend fun delete() {
        // A save queued before deletion must not restore erased data afterwards.
        revision.incrementAndGet()
        withContext(Dispatchers.IO) {
            lock.withLock {
                file.delete()
                check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists() &&
                    !File(file.baseFile.path + ".new").exists()) { "Não foi possível apagar a cena salva." }
            }
        }
    }

    companion object {
        private val lock = Mutex()
        private val revision = AtomicLong(0)
    }
}
