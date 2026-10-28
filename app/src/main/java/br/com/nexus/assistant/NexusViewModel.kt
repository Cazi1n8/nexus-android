package br.com.nexus.assistant

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.nexus.assistant.data.*
import br.com.nexus.assistant.data.local.LocalAiProvider
import br.com.nexus.assistant.data.local.LocalModelManager
import br.com.nexus.assistant.data.local.LocalModelCatalog
import br.com.nexus.assistant.data.local.LocalModelProfile
import br.com.nexus.assistant.data.local.LocalModelState
import br.com.nexus.assistant.data.local.LocalModelStatus
import br.com.nexus.assistant.commands.ActionProposal
import br.com.nexus.assistant.device.ImageAttachments
import br.com.nexus.assistant.device.DeviceCapabilities
import br.com.nexus.assistant.device.DevicePolicy
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
    // Keep a stable startup budget. A download or retained model must not switch
    // profiles halfway through a conversation as the amount of free RAM changes.
    val deviceCapabilities = MutableStateFlow(DeviceCapabilities.read(application)).asStateFlow()
    val devicePolicy = MutableStateFlow(DevicePolicy.evaluate(deviceCapabilities.value)).asStateFlow()
    private val preferences = SettingsRepository(application)
    private val imageDirectory = File(application.filesDir, "chat-images").apply { mkdirs() }
    private val repository = NexusRepository(NexusDatabase.getInstance(application), imageDirectory)
    private val localModels = LocalModelProfile.entries.associateWith { profile ->
        LocalModelManager(File(application.filesDir, "models"), LocalModelCatalog.forProfile(profile))
    }
    private val localProviders = localModels.mapValues { (_, manager) -> LocalAiProvider(manager) { devicePolicy.value } }
    private val provider: AiProvider = AiCoordinator(LocalImageTextProvider(object : AiProvider {
        override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String {
            val profile = effectiveProfile(settings)
            return localProviders.getValue(profile).complete(settings.copy(localModelProfile = profile), token, messages, memories)
        }
    }, classifyImage = { path ->
        br.com.nexus.assistant.device.vision.LocalObjectClassifier(application).use { classifier -> classifier.classify(File(path)) }
    }, readText = { path ->
        br.com.nexus.assistant.device.vision.LocalTextReader(application).use { reader -> reader.read(File(path)).text }
    }))
    private var modelDownloadJob: Job? = null
    private val _localModelDownloadActive = MutableStateFlow(false)
    val localModelDownloadActive = _localModelDownloadActive.asStateFlow()
    private val _localModelOperationProfile = MutableStateFlow<LocalModelProfile?>(null)
    val localModelOperationProfile = _localModelOperationProfile.asStateFlow()
    /** Both optional packs, including an installed profile that is not selected for chat. */
    val localModelStates = combine(localModels.map { (profile, manager) ->
        manager.state.map { profile to it }
    }) { states -> states.toMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, localModels.mapValues { it.value.state.value })
    private var modifyingLocalModel = false
    private val _assistantReplies = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val assistantReplies = _assistantReplies.asSharedFlow()
    private val _pendingAction = MutableStateFlow<ActionProposal?>(null)
    val pendingAction = _pendingAction.asStateFlow()
    val settings = preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, NexusSettings())
    val effectiveLocalProfile = settings.map(::effectiveProfile).distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, effectiveProfile(NexusSettings()))
    val localModelSpec = effectiveLocalProfile.map(LocalModelCatalog::forProfile)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalModelCatalog.forProfile(effectiveProfile(NexusSettings())))
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val localModelState = effectiveLocalProfile
        .flatMapLatest { profile ->
            localModels.getValue(profile).state.map { state ->
                // A bundled model is intentionally idle until the user opens
                // Chat. CHECKING is the manager's initial value, not active work.
                if (BuildConfig.BUNDLED_MODEL && state.status == LocalModelStatus.CHECKING) {
                    state.copy(status = LocalModelStatus.ABSENT)
                } else state
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly,
            LocalModelState(status = if (BuildConfig.BUNDLED_MODEL) LocalModelStatus.ABSENT else LocalModelStatus.CHECKING))
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val generationDiagnostics = effectiveLocalProfile.flatMapLatest { localProviders.getValue(it).diagnostics }
        .stateIn(viewModelScope, SharingStarted.Eagerly, br.com.nexus.assistant.data.local.LocalGenerationDiagnostics())
    private val _historyQuery = MutableStateFlow("")
    val historyQuery = _historyQuery.asStateFlow()
    fun searchHistory(query: String) { _historyQuery.value = query.take(200) }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val historyResults = _historyQuery.flatMapLatest(repository::searchConversations)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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
    private val _chatDraft = MutableStateFlow<String?>(null)
    val chatDraft = _chatDraft.asStateFlow()
    fun stageChatDraft(text: String) { _chatDraft.value = text.take(24_000) }
    fun consumeChatDraft() { _chatDraft.value = null }
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
    private val _clearingTemporaryCache = MutableStateFlow(false)
    val clearingTemporaryCache = _clearingTemporaryCache.asStateFlow()

    init {
        if (!BuildConfig.BUNDLED_MODEL) {
            viewModelScope.launch {
                settings.map(::effectiveProfile).distinctUntilChanged().collectLatest { profile ->
                    localModels.getValue(profile).refresh()
                }
            }
        }
    }

    fun report(message: String) { _error.value = message }
    fun clearError() { _error.value = null }
    fun newConversation() { _chatDraft.value = null; conversationGeneration++; cancelResponse(); releaseLocalContext(); _selected.value = null; removeAttachment() }
    fun openConversation(id: String) { conversationGeneration++; cancelResponse(); releaseLocalContext(); _selected.value = id; removeAttachment() }
    // KV state belongs only to the selected conversation. Never retain it when
    // the user changes the conversation or revokes personal context.
    private fun releaseLocalContext() { localProviders.values.forEach { it.release() } }
    fun cancelResponse() {
        _pendingAction.value = null
        responseJob?.cancel()
        // Keep the send barrier until the cancelled job has finished its short,
        // non-cancellable Room save. Otherwise a second send can create a second
        // conversation before the first one has an id.
        if (responseJob == null) _busy.value = false
    }

    /** True means this send was accepted; validation failures preserve the UI draft. */
    fun send(raw: String): Boolean {
        if (deletingData || modifyingLocalModel || _busy.value || _importing.value) return false
        val text = raw.trim()
        val image = _attachment.value
        if (text.isEmpty() && image == null) return false
        if (text.length > 24_000) { report("Divida a mensagem em partes de até 24.000 caracteres."); return false }
        val config = settings.value
        // CORE can calculate and propose local actions without an optional LLM.
        // Keep image/provider validation for every request not handled by tools.
        val toolAnswer = AiCoordinator.builtInResponse(AiMessage("user", text, image))
        if (toolAnswer == null && !canUseAi(config, image != null)) return false
        val selectedAtSend = _selected.value
        val conversationAtSend = conversationGeneration
        val generation = ++responseGeneration
        _pendingAction.value = null
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
                val token = if (toolAnswer == null && config.aiMode == AiMode.CUSTOM) withContext(Dispatchers.IO) { preferences.sessionToken } else ""
                val answer = toolAnswer ?: provider.complete(config, token, history, memories.value.map { it.content })
                val completion = AiCompletion.fromText(answer)
                repository.addMessage(id, "assistant", completion.text)
                if (generation == responseGeneration && conversationAtSend == conversationGeneration) {
                    _pendingAction.value = completion.proposal
                    _assistantReplies.emit(completion.text)
                }
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
        if (deletingData || modifyingLocalModel || _busy.value || _importing.value) return false
        val config = settings.value
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
                val last = history.last()
                val toolAnswer = AiCoordinator.builtInResponse(AiMessage(last.role, last.content, last.imagePath))
                if (toolAnswer == null && !canUseAi(config, last.imagePath != null)) return@launch
                val token = if (toolAnswer == null && config.aiMode == AiMode.CUSTOM) withContext(Dispatchers.IO) { preferences.sessionToken } else ""
                val answer = toolAnswer ?: provider.complete(config, token,
                    history.map { AiMessage(it.role, it.content, it.imagePath) }, memories.value.map { it.content })
                val completion = AiCompletion.fromText(answer)
                repository.addMessage(id, "assistant", completion.text)
                if (generation == responseGeneration && _selected.value == id) {
                    _pendingAction.value = completion.proposal
                    _assistantReplies.emit(completion.text)
                }
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
        if (deletingData || _clearingTemporaryCache.value || _busy.value || _importing.value) {
            source.delete()
            return
        }
        importImage(Uri.fromFile(source))
        val pendingImport = imageJob
        if (pendingImport == null) source.delete()
        else pendingImport.invokeOnCompletion { runCatching { source.delete() } }
    }

    fun importImage(uri: Uri) {
        if (deletingData || _clearingTemporaryCache.value || _busy.value || _importing.value) return
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
    private fun effectiveProfile(value: NexusSettings): LocalModelProfile =
        if (value.automaticProfile) devicePolicy.value.recommendedProfile ?: LocalModelProfile.COMPACT else value.localModelProfile

    private fun canUseAi(value: NexusSettings, hasImage: Boolean = false): Boolean {
        if (value.aiMode == AiMode.NEXUS_LOCAL) {
            if (modifyingLocalModel) {
                report("Aguarde a alteração do modelo local terminar.")
                return false
            }
            val profile = effectiveProfile(value)
            if (!DevicePolicy.canRunProfile(deviceCapabilities.value, profile)) {
                report(if (!devicePolicy.value.localAiSupported) devicePolicy.value.reason
                    else "O modelo equilibrado exige mais RAM disponível. Ative o ajuste automático, escolha o compacto ou use um provedor personalizado.")
                return false
            }
            if (localModels.getValue(profile).state.value.status != LocalModelStatus.READY) {
                report(if (BuildConfig.BUNDLED_MODEL) "A IA incluída ainda está sendo preparada. Aguarde a verificação terminar ou toque em Preparar IA incluída."
                    else "Baixe o modelo selecionado em NEXUS AI. Depois do download verificado, a conversa funciona sem servidor, chave ou internet.")
                return false
            }
            return true
        }
        if (!value.remoteConsent || value.endpoint.isBlank() || value.model.isBlank()) {
            report("Configure o endereço, o modelo e a autorização de envio em Configurações para conversar com uma IA real.")
            return false
        }
        return true
    }

    /** Opening Chat may prepare the bundled asset; this never authorizes a network download. */
    fun prepareLocalAi() {
        if (!BuildConfig.BUNDLED_MODEL || settings.value.aiMode != AiMode.NEXUS_LOCAL) return
        startLocalModelPreparation()
    }

    fun downloadLocalModel() = startLocalModelPreparation()

    /** Explicit pack download; installation alone does not change the selected chat profile. */
    fun downloadLocalModel(profile: LocalModelProfile) = startLocalModelPreparation(profile)

    /** Revalidates private files left by an earlier version. Does not start a download. */
    fun refreshLocalModels() = viewModelScope.launch {
        if (deletingData || modifyingLocalModel || modelDownloadJob != null) return@launch
        localModels.values.forEach { it.refresh() }
    }

    private fun startLocalModelPreparation(profile: LocalModelProfile = effectiveProfile(settings.value)) {
        // A cancelled job can still be closing its socket and file. Keep the
        // barrier until completion rather than queuing a second download.
        if (deletingData || modifyingLocalModel || modelDownloadJob != null) return
        if (!DevicePolicy.canRunProfile(deviceCapabilities.value, profile)) {
            report(if (!devicePolicy.value.localAiSupported) devicePolicy.value.reason
                else "Este aparelho não tem a margem de RAM exigida para o modelo equilibrado. Escolha o perfil compacto.")
            return
        }
        val manager = localModels.getValue(profile)
        if (manager.state.value.status == LocalModelStatus.READY) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (BuildConfig.BUNDLED_MODEL) {
                    // Wait for the persisted mode before doing local setup; a
                    // restored optional provider must not trigger asset copying.
                    if (preferences.settings.first().aiMode != AiMode.NEXUS_LOCAL) return@launch
                    manager.installBundled {
                        getApplication<Application>().assets.open("models/${LocalModelCatalog.forProfile(profile).fileName}")
                    }
                } else manager.download()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { report(failure.message ?: "Não foi possível concluir a preparação da IA.") }
        }
        modelDownloadJob = job
        _localModelOperationProfile.value = profile
        _localModelDownloadActive.value = true
        job.invokeOnCompletion {
            // Completion can arrive from an IO cancellation. Keep both the
            // reference and its UI flag on Main so an old job cannot clear a
            // newer operation's active flag between two assignments.
            viewModelScope.launch(Dispatchers.Main.immediate) {
                if (modelDownloadJob === job) {
                    modelDownloadJob = null
                    _localModelOperationProfile.value = null
                    _localModelDownloadActive.value = false
                }
            }
        }
        job.start()
    }

    fun cancelLocalModelDownload() { modelDownloadJob?.cancel() }

    /** Only disposable cache files; models, OCR data, history and code cache stay intact. */
    suspend fun clearTemporaryCache(): String = withContext(Dispatchers.Main.immediate) {
        if (deletingData || _clearingTemporaryCache.value) {
            return@withContext "Aguarde a operação de armazenamento atual terminar."
        }
        _clearingTemporaryCache.value = true
        try {
            // Image import owns temporary cache streams. Stop and await cleanup
            // before deleting cache; a new import is refused until this ends.
            cancelImageImport()
            runningImports.toList().forEach { it.join() }
            val result = br.com.nexus.assistant.storage.StorageRepository(getApplication()).clearTemporaryCache()
            val megabytes = String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", result.removedBytes / 1_000_000.0)
            buildString {
                append("Cache temporário removido: $megabytes MB em ${result.removedFiles} arquivo(s).")
                if (result.failures > 0) append(" ${result.failures} item(ns) não puderam ser removidos.")
                if (result.skippedSymlinks > 0) append(" Links simbólicos foram preservados.")
            }
        } finally { _clearingTemporaryCache.value = false }
    }

    fun deleteLocalModel() = deleteLocalModel(effectiveProfile(settings.value))

    /** Removes this pack and any partial download, preserving chat, memories and other packs. */
    fun deleteLocalModel(profile: LocalModelProfile) = mutate {
        modifyingLocalModel = true
        try {
            modelDownloadJob?.cancel()
            modelDownloadJob?.join()
            cancelResponse()
            runningResponses.toList().forEach { it.join() }
            localProviders.getValue(profile).releaseAndAwait()
            localModels.getValue(profile).delete()
            report(if (BuildConfig.BUNDLED_MODEL) "Cópia local da IA removida. O modelo continua no APK e será preparado ao abrir o Chat. Suas conversas foram preservadas."
                else "Modelo selecionado removido. As conversas e o outro perfil foram preservados.")
        } finally { modifyingLocalModel = false }
    }

    fun dismissPendingAction() { _pendingAction.value = null }

    /** One-shot claim used only by the explicit confirmation button. */
    fun takePendingAction(id: String): ActionProposal? {
        val current = _pendingAction.value ?: return null
        return current.takeIf { it.id == id && _pendingAction.compareAndSet(current, null) }
    }

    fun onAppBackgrounded() {
        if (settings.value.aiMode == AiMode.NEXUS_LOCAL) cancelResponse()
        localProviders.values.forEach { it.release() }
    }
    fun saveSettings(value: NexusSettings, sessionToken: String? = null, onSaved: (() -> Unit)? = null) = mutate {
        val normalized = value.copy(endpoint = value.endpoint.trim(), model = value.model.trim())
        if (normalized.aiMode == AiMode.CUSTOM && normalized.endpoint.isNotEmpty()) {
            OpenAiCompatibleProvider.endpoint(normalized.endpoint, BuildConfig.DEBUG)
        }
        if (sessionToken != null && sessionToken.any { it.code !in 33..126 } && sessionToken.isNotBlank()) {
            throw IllegalArgumentException("O token de sessão contém caracteres inválidos.")
        }
        val previous = preferences.settings.first()
        val changingAi = normalized.aiMode != previous.aiMode || effectiveProfile(normalized) != effectiveProfile(previous)
        val changingPrompt = normalized.userPreferredName != previous.userPreferredName ||
            normalized.assistantPersonality != previous.assistantPersonality ||
            normalized.assistantInstructions != previous.assistantInstructions ||
            normalized.userPreferences != previous.userPreferences || normalized.language != previous.language ||
            normalized.detail != previous.detail
        if (changingAi || changingPrompt) modifyingLocalModel = true
        try {
            if (changingAi || changingPrompt) {
                cancelResponse()
                if (changingAi) {
                    modelDownloadJob?.cancel()
                    modelDownloadJob?.join()
                }
                runningResponses.toList().forEach { it.join() }
                localProviders.values.forEach { it.releaseAndAwait() }
            }
            val changedServer = normalized.endpoint != previous.endpoint.trim()
            if (changedServer) {
                // DataStore and Keystore are separate stores: partial writes must
                // leave remote sending inactive until new settings are saved.
                preferences.save(previous.copy(remoteConsent = false))
            }
            if (sessionToken != null || changedServer) withContext(Dispatchers.IO) {
                preferences.sessionToken = sessionToken?.trim().orEmpty()
            }
            preferences.save(normalized.copy(remoteConsent = normalized.remoteConsent && normalized.endpoint.isNotBlank()))
            onSaved?.invoke()
            report("Configurações salvas neste aparelho.")
        } finally {
            if (changingAi || changingPrompt) modifyingLocalModel = false
        }
    }
    fun saveMemory(content: String, id: String? = null) = mutate { resetPersonalContext { repository.saveMemory(content, id) } }
    fun deleteMemory(id: String) = mutate { resetPersonalContext { repository.deleteMemory(id) } }
    private suspend fun resetPersonalContext(change: suspend () -> Unit) {
        modifyingLocalModel = true
        try {
            cancelResponse()
            runningResponses.toList().forEach { it.join() }
            localProviders.values.forEach { it.releaseAndAwait() }
            change()
        } finally { modifyingLocalModel = false }
    }
    fun renameConversation(id: String, title: String) = mutate { repository.renameConversation(id, title) }
    fun deleteConversation(id: String) = mutate {
        if (_selected.value == id) newConversation()
        resetPersonalContext { repository.deleteConversation(id) }
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
            modelDownloadJob?.cancel()
            modelDownloadJob?.join()
            localProviders.values.forEach { it.releaseAndAwait() }
            // Existing saves finish before this section; new edits are refused until
            // it ends. No delayed preference/memory write can restore erased data.
            dataMutationMutex.withLock {
                repository.clearConversations()
                repository.clearMemories()
                preferences.save(NexusSettings())
                localModels.values.forEach { it.delete() }
                br.com.nexus.assistant.commands.CommandStore(getApplication()).clear()
                br.com.nexus.assistant.libras.LibrasProgressRepository(getApplication()).clear()
                withContext(Dispatchers.IO) {
                    preferences.sessionToken = ""
                    check(getApplication<Application>().getSharedPreferences("nexus_gestures", android.content.Context.MODE_PRIVATE).edit().clear().commit()) {
                        "Não foi possível apagar as preferências de gestos. Tente novamente."
                    }
                    check(getApplication<Application>().getSharedPreferences("nexus_tracking", android.content.Context.MODE_PRIVATE).edit().clear().commit()) {
                        "Não foi possível apagar o perfil de rastreamento."
                    }
                    check(getApplication<Application>().getSharedPreferences("nexus_hands_free", android.content.Context.MODE_PRIVATE).edit().clear().commit()) {
                        "Não foi possível apagar as preferências de mãos livres. Tente novamente."
                    }
                    imageDirectory.listFiles()?.filter { it.isFile }?.forEach { file ->
                        check(file.delete() || !file.exists()) { "Não foi possível remover uma imagem local. Tente novamente." }
                    }
                }
            }
            report("Conversas, memórias, preferências e modelo local removidos.")
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
        modelDownloadJob?.cancel()
        localProviders.values.forEach { it.close() }
        // The viewModelScope has already been cancelled here; a queued coroutine would not run.
        _attachment.value?.let { runCatching { File(it).delete() } }
        _attachment.value = null
        super.onCleared()
    }
}
