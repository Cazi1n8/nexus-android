package br.com.nexus.assistant

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.nexus.assistant.data.*
import br.com.nexus.assistant.device.ImageAttachments
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NexusViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = SettingsRepository(application)
    private val imageDirectory = File(application.filesDir, "chat-images").apply { mkdirs() }
    private val repository = NexusRepository(NexusDatabase.getInstance(application), imageDirectory)
    private val provider: AiProvider = OpenAiCompatibleProvider()
    val settings = preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, NexusSettings())
    val conversations = repository.conversations.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val memories = repository.memories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _selected = MutableStateFlow<String?>(null)
    val selected = _selected.asStateFlow()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages = selected.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.messages(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _attachment = MutableStateFlow<String?>(null)
    val attachment = _attachment.asStateFlow()
    private var responseJob: Job? = null
    private var imageJob: Job? = null
    private var responseGeneration = 0L
    private var imageGeneration = 0L
    // A cancelled answer and a changed conversation are different events. Finishing
    // a local save after Stop must still display the saved first message; finishing
    // after New/Open must never switch the user back to that old conversation.
    private var conversationGeneration = 0L
    private val runningResponses = ConcurrentHashMap.newKeySet<Job>()
    private val runningImports = ConcurrentHashMap.newKeySet<Job>()
    private val dataMutationMutex = Mutex()
    private var deletingData = false
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    fun report(message: String) { _error.value = message }
    fun clearError() { _error.value = null }
    fun newConversation() { conversationGeneration++; cancelResponse(); _selected.value = null; removeAttachment() }
    fun openConversation(id: String) { conversationGeneration++; cancelResponse(); _selected.value = id; removeAttachment() }
    fun cancelResponse() {
        responseJob?.cancel()
        // Keep the send barrier until the cancelled job has finished its short,
        // non-cancellable Room save. Otherwise a second send can create a second
        // conversation before the first one has an id.
        if (responseJob == null) _busy.value = false
    }

    /** True means this send was accepted; validation failures preserve the UI draft. */
    fun send(raw: String): Boolean {
        if (deletingData || _busy.value || _importing.value) return false
        val text = raw.trim()
        val image = _attachment.value
        if (text.isEmpty() && image == null) return false
        if (text.length > 24_000) { report("Divida a mensagem em partes de até 24.000 caracteres."); return false }
        val config = settings.value
        if (!canUseAi(config)) return false
        val selectedAtSend = _selected.value
        val conversationAtSend = conversationGeneration
        val generation = ++responseGeneration
        _error.value = null
        _busy.value = true
        responseJob = viewModelScope.launch {
            var userSaved = false
            var imageRestored = false
            // Transfer the attachment to this message before any suspension. Changing conversations
            // can now remove the next draft without deleting an image being saved to the database.
            if (image != null && _attachment.value == image) _attachment.value = null
            try {
                // Finish the short, local save even when the user interrupts the network response.
                // This makes attachment ownership unambiguous around Room's cancellation boundary.
                val id = withContext(NonCancellable) {
                    val conversationId = selectedAtSend ?: repository.createConversation(text.ifBlank { "Análise de imagem" })
                    repository.addMessage(conversationId, "user", text, image)
                    userSaved = true
                    if (conversationAtSend == conversationGeneration) _selected.value = conversationId
                    conversationId
                }
                val history = repository.conversationMessages(id).map { AiMessage(it.role, it.content, it.imagePath) }
                val token = withContext(Dispatchers.IO) { preferences.sessionToken }
                val answer = provider.complete(config, token, history, memories.value.map { it.content })
                repository.addMessage(id, "assistant", answer)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == responseGeneration && conversationAtSend == conversationGeneration) {
                    if (!userSaved && image != null && _attachment.value == null) {
                        _attachment.value = image
                        imageRestored = true
                    }
                    report((failure.message ?: "Não foi possível completar a conversa.") + if (userSaved) " Sua mensagem foi salva. Use Tentar novamente." else "")
                }
            } finally {
                if (!userSaved && !imageRestored && image != null) {
                    withContext(NonCancellable + Dispatchers.IO) { File(image).delete() }
                }
                if (generation == responseGeneration) {
                    _busy.value = false
                    responseJob = null
                }
            }
        }
        responseJob?.let { job ->
            runningResponses.add(job)
            job.invokeOnCompletion { runningResponses.remove(job) }
        }
        return true
    }

    fun retry(): Boolean {
        val id = _selected.value ?: return false
        if (deletingData || _busy.value || _importing.value) return false
        val config = settings.value
        if (!canUseAi(config)) return false
        val generation = ++responseGeneration
        _busy.value = true
        _error.value = null
        responseJob = viewModelScope.launch {
            try {
                val history = repository.conversationMessages(id)
                if (history.lastOrNull()?.role != "user") {
                    if (generation == responseGeneration) report("Não há mensagem pendente para tentar novamente.")
                    return@launch
                }
                val token = withContext(Dispatchers.IO) { preferences.sessionToken }
                val answer = provider.complete(config, token,
                    history.map { AiMessage(it.role, it.content, it.imagePath) }, memories.value.map { it.content })
                repository.addMessage(id, "assistant", answer)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (generation == responseGeneration) report(failure.message ?: "Falha ao tentar novamente.")
            } finally {
                if (generation == responseGeneration) {
                    _busy.value = false
                    responseJob = null
                }
            }
        }
        responseJob?.let { job ->
            runningResponses.add(job)
            job.invokeOnCompletion { runningResponses.remove(job) }
        }
        return true
    }

    /** Takes ownership only of a camera file within our own cache directory. */
    fun importCapturedImage(file: File) {
        val cache = getApplication<Application>().cacheDir.canonicalFile.toPath()
        val source = runCatching { file.canonicalFile }.getOrNull()
        if (source == null || !source.toPath().startsWith(cache)) {
            report("A captura não pertence ao cache do NEXUS.")
            return
        }
        if (deletingData || _busy.value || _importing.value) {
            source.delete()
            return
        }
        importImage(Uri.fromFile(source))
        val pendingImport = imageJob
        if (pendingImport == null) source.delete()
        else pendingImport.invokeOnCompletion { runCatching { source.delete() } }
    }

    fun importImage(uri: Uri) {
        if (deletingData || _busy.value || _importing.value) return
        val generation = ++imageGeneration
        // Allocate the name before switching dispatchers so cancellation can always remove it.
        val destination = File(imageDirectory, "${UUID.randomUUID()}.jpg")
        _importing.value = true
        imageJob = viewModelScope.launch {
            var attached = false
            try {
                withContext(Dispatchers.IO) {
                    val compressed = ImageAttachments.copyAndCompress(getApplication(), uri)
                    try {
                        ensureActive()
                        compressed.copyTo(destination, overwrite = false)
                        ensureActive()
                    } finally { compressed.delete() }
                }
                if (generation == imageGeneration) {
                    discardPendingAttachment()
                    _attachment.value = destination.absolutePath
                    attached = true
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (generation == imageGeneration) report(failure.message ?: "Não foi possível abrir a imagem.")
            } finally {
                if (!attached) withContext(NonCancellable + Dispatchers.IO) { destination.delete() }
                if (generation == imageGeneration) {
                    _importing.value = false
                    imageJob = null
                }
            }
        }
        imageJob?.let { job ->
            runningImports.add(job)
            job.invokeOnCompletion { runningImports.remove(job) }
        }
    }
    fun removeAttachment() {
        cancelImageImport()
        discardPendingAttachment()
    }
    private fun cancelImageImport() {
        imageGeneration++
        imageJob?.cancel()
        imageJob = null
        _importing.value = false
    }
    private fun discardPendingAttachment() {
        val path = _attachment.value
        _attachment.value = null
        if (path != null) {
            val file = File(path)
            val removal = viewModelScope.launch(Dispatchers.IO) { file.delete() }
            // A pending cleanup must still happen if the ViewModel is cleared before dispatch.
            removal.invokeOnCompletion { failure ->
                if (failure is CancellationException) runCatching { file.delete() }
            }
        }
    }
    private fun canUseAi(value: NexusSettings): Boolean {
        if (!value.remoteConsent || value.endpoint.isBlank() || value.model.isBlank()) {
            report("Configure o endereço, o modelo e a autorização de envio em Configurações para conversar com uma IA real.")
            return false
        }
        return true
    }
    fun saveSettings(value: NexusSettings, sessionToken: String? = null, onSaved: (() -> Unit)? = null) = mutate {
        val normalized = value.copy(endpoint = value.endpoint.trim(), model = value.model.trim())
        if (normalized.endpoint.isNotEmpty()) OpenAiCompatibleProvider.endpoint(normalized.endpoint, BuildConfig.DEBUG)
        if (sessionToken != null && sessionToken.any { it.code !in 33..126 } && sessionToken.isNotBlank()) {
            throw IllegalArgumentException("O token de sessão contém caracteres inválidos.")
        }
        val previous = preferences.settings.first()
        val changedServer = normalized.endpoint != previous.endpoint.trim()
        if (changedServer) {
            // Disable sending before touching credentials: DataStore and Keystore are
            // different stores, so a partial write must leave the connection inactive.
            preferences.save(previous.copy(remoteConsent = false))
        }
        if (sessionToken != null || changedServer) withContext(Dispatchers.IO) {
            preferences.sessionToken = sessionToken?.trim().orEmpty()
        }
        preferences.save(normalized.copy(remoteConsent = normalized.remoteConsent && normalized.endpoint.isNotBlank()))
        onSaved?.invoke()
        report("Configurações salvas neste aparelho.")
    }
    fun saveMemory(content: String, id: String? = null) = mutate { repository.saveMemory(content, id) }
    fun deleteMemory(id: String) = mutate { repository.deleteMemory(id) }
    fun deleteConversation(id: String) = mutate {
        if (_selected.value == id) newConversation()
        runningResponses.filter { it.isCancelled }.forEach { it.join() }
        repository.deleteConversation(id)
    }
    fun deleteAllData() = guarded {
        if (deletingData) return@guarded
        deletingData = true
        newConversation()
        val generation = responseGeneration
        _busy.value = true
        try {
            val pendingResponses = runningResponses.toList()
            val pendingImports = runningImports.toList()
            pendingResponses.forEach { it.cancel() }
            pendingImports.forEach { it.cancel() }
            // Wait for the small local persistence/cleanup sections before deleting their data.
            pendingResponses.forEach { it.join() }
            pendingImports.forEach { it.join() }
            // Existing saves finish before this section; new edits are refused until
            // it ends. No delayed preference/memory write can restore erased data.
            dataMutationMutex.withLock {
                repository.clearConversations()
                repository.clearMemories()
                preferences.save(NexusSettings())
                br.com.nexus.assistant.commands.CommandStore(getApplication()).clear()
                withContext(Dispatchers.IO) {
                    preferences.sessionToken = ""
                    check(getApplication<Application>().getSharedPreferences("nexus_gestures", android.content.Context.MODE_PRIVATE).edit().clear().commit()) {
                        "Não foi possível apagar as preferências de gestos. Tente novamente."
                    }
                    imageDirectory.listFiles()?.filter { it.isFile }?.forEach { file ->
                        check(file.delete() || !file.exists()) { "Não foi possível remover uma imagem local. Tente novamente." }
                    }
                }
            }
            report("Conversas, memórias e preferências removidas.")
        } finally {
            deletingData = false
            if (generation == responseGeneration) _busy.value = false
        }
    }
    fun export(uri: Uri) = guarded {
        val content = repository.exportJson()
        withContext(Dispatchers.IO) {
            val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                ?: error("Não foi possível criar o arquivo.")
            output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
        }
        report("Exportação concluída. O JSON contém textos pessoais; guarde em um local seguro.")
    }
    private fun guarded(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) { report(failure.message ?: "Não foi possível concluir a operação.") }
    }

    private fun mutate(block: suspend () -> Unit) = guarded {
        if (deletingData) return@guarded
        dataMutationMutex.withLock {
            if (!deletingData) block()
        }
    }

    override fun onCleared() {
        cancelResponse()
        cancelImageImport()
        // The viewModelScope has already been cancelled here; a queued coroutine would not run.
        _attachment.value?.let { runCatching { File(it).delete() } }
        _attachment.value = null
        super.onCleared()
    }
}
