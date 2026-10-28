package br.com.nexus.assistant.extensions

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class ExtensionUnavailableException(message: String) : IOException(message)

/** Serializes package mutation against active native readers; independent of Android. */
internal class PackAccess {
    private val users = mutableMapOf<ExtensionPackId, Int>()
    private val changing = mutableSetOf<ExtensionPackId>()
    @Synchronized fun acquire(id: ExtensionPackId): AutoCloseable {
        if (id in changing) throw ExtensionUnavailableException("O pacote está sendo alterado. Aguarde e abra a função novamente.")
        users[id] = (users[id] ?: 0) + 1
        return once { synchronized(this) { users[id] = (users[id] ?: 1) - 1 } }
    }
    @Synchronized fun mutate(id: ExtensionPackId): AutoCloseable {
        if ((users[id] ?: 0) > 0) throw ExtensionUnavailableException("Encerre a sessão NEXUS Vision ou a análise de imagem antes de alterar este pacote.")
        if (!changing.add(id)) throw ExtensionUnavailableException("O pacote já tem uma operação em andamento.")
        return once { synchronized(this) { changing.remove(id) } }
    }
    private fun once(action: () -> Unit): AutoCloseable {
        val closed = AtomicBoolean(false)
        return AutoCloseable { if (closed.compareAndSet(false, true)) action() }
    }
}
