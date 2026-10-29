package br.com.nexus.assistant.device.vision

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.nexus.assistant.NexusViewModel
import br.com.nexus.assistant.data.AiMode
import br.com.nexus.assistant.data.MessageEntity
import br.com.nexus.assistant.data.NexusSettings
import br.com.nexus.assistant.device.VoiceController
import br.com.nexus.assistant.device.voice.VoiceSessionRuntime
import br.com.nexus.assistant.gestures.global.GlobalMouseRuntime
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A visible, explicitly consented conversation over captured JPEGs. Pixels travel
 * through the ordinary image import/provider/Room path; this is not video input.
 * No camera or microphone service is started, and this panel releases on pause.
 */
@Composable
fun VisionConversationPanel(
    model: NexusViewModel,
    voice: VoiceController,
    settings: NexusSettings,
    modifier: Modifier = Modifier,
    onActiveChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val capabilities by model.deviceCapabilities.collectAsStateWithLifecycle()
    val policy by model.devicePolicy.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val streaming by model.streamedReply.collectAsStateWithLifecycle()
    val answerSources by model.answerSources.collectAsStateWithLifecycle()
    val importing by model.importing.collectAsStateWithLifecycle()
    val speech by voice.state.collectAsStateWithLifecycle()
    val backgroundVoice by VoiceSessionRuntime.state.collectAsStateWithLifecycle()
    val global by GlobalMouseRuntime.state.collectAsStateWithLifecycle()
    val activeCallback by rememberUpdatedState(onActiveChanged)
    val gate = remember { VisionConversationGate() }
    var active by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var confirmSearch by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(!capabilities.hasRearCamera && capabilities.hasFrontCamera) }
    var preview by remember { mutableStateOf(true) }
    var periodic by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("O que é isso?") }
    var answer by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var captureControl by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var ticket by remember { mutableStateOf<VisionConversationGate.Ticket?>(null) }
    var task by remember { mutableStateOf<Job?>(null) }
    var importPending by remember { mutableStateOf(false) }
    var ownedAttachment by remember { mutableStateOf<String?>(null) }
    var ownsResponse by remember { mutableStateOf(false) }
    var sessionConversation by remember { mutableStateOf<String?>(null) }
    var hasImage by remember { mutableStateOf(false) }
    val configured = settings.aiMode == AiMode.CUSTOM && settings.remoteConsent && settings.endpoint.isNotBlank() && settings.model.isNotBlank()
    var sessionEndpoint by remember { mutableStateOf("") }
    var sessionModel by remember { mutableStateOf("") }

    fun stop() {
        val cancelOwnedResponse = ownsResponse
        gate.stop()
        active = false
        periodic = false
        permissionRequested = false
        captureControl = null
        task?.cancel()
        task = null
        if (importPending || (ownedAttachment != null && model.attachment.value == ownedAttachment)) model.removeAttachment()
        importPending = false
        ownedAttachment = null
        ownsResponse = false
        working = false
        ticket = null
        if (cancelOwnedResponse) model.cancelResponse()
        voice.stopListening()
        voice.stopSpeaking()
        activeCallback(false)
    }

    fun start() {
        permissionRequested = false
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || model.busy.value || model.importing.value || !configured) return
        if (GlobalMouseRuntime.state.value.active || VoiceSessionRuntime.state.value.active) {
            error = "Encerre o mouse global e a sessão de voz antes de iniciar esta conversa visual."
            return
        }
        voice.stopListening()
        voice.stopSpeaking()
        model.newConversation()
        sessionEndpoint = settings.endpoint
        sessionModel = settings.model
        gate.start()
        sessionConversation = null
        hasImage = false
        answer = null
        error = null
        periodic = false
        active = true
        activeCallback(true)
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && permissionRequested && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        else {
            permissionRequested = false
            if (!granted) error = "A câmera não foi autorizada. A galeria e o Chat continuam disponíveis."
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && active && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !backgroundVoice.active && !working && !busy) {
            voice.startListening(settings.language, onResult = { heard ->
                if (gate.active && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) question = heard.trim().take(2_000)
            })
        } else if (!granted) error = "O microfone não foi autorizado. Escreva sua pergunta."
    }

    fun finishFailure(current: VisionConversationGate.Ticket, message: String) {
        if (!gate.finish(current)) return
        error = message
        periodic = false // A failed request never creates a retry/upload loop.
        working = false
        ticket = null
        ownsResponse = false
    }

    fun beginCapture(automatic: Boolean) {
        if (!active || !configured || busy || importing || working || speech.isListening || speech.isSpeaking || question.isBlank()) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val capture = captureControl ?: return
        val current = gate.begin(SystemClock.elapsedRealtime(), periodic = automatic) ?: return
        ticket = current
        working = true
        error = null
        if (!capture()) finishFailure(current, "A câmera ainda não está pronta. Aguarde e tente capturar novamente.")
    }

    fun awaitAnswer(current: VisionConversationGate.Ticket, query: String, previousIds: Set<String>, image: String?) {
        task = scope.launch {
            try {
                // send() raises busy synchronously. Only this request owns it.
                model.busy.first { !it }
                if (!gate.accepts(current)) return@launch
                val response = withTimeoutOrNull(5_000L) {
                    model.messages.first { rows -> findResponse(rows, previousIds, query, image) != null }
                }?.let { findResponse(it, previousIds, query, image) }
                if (!gate.accepts(current)) return@launch
                if (response == null) {
                    finishFailure(current, model.error.value ?: "A análise não gerou uma resposta. Confira o modelo multimodal e a conexão antes de tentar novamente.")
                } else {
                    sessionConversation = model.selected.value
                    if (image != null) hasImage = true
                    answer = response.content
                    error = null
                    ownsResponse = false
                    working = false
                    ticket = null
                    gate.finish(current)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
        }
    }

    fun askAboutLastPhoto(search: Boolean) {
        if (!active || !hasImage || working || busy || importing || question.isBlank() || speech.isListening) return
        val current = gate.begin(SystemClock.elapsedRealtime(), periodic = false) ?: return
        val query = question.trim()
        val previousIds = model.messages.value.map { it.id }.toSet()
        error = null
        ticket = current
        working = true
        if (model.send(query, onlineSearchQuery = if (search) query else null)) {
            ownsResponse = true
            awaitAnswer(current, query, previousIds, null)
        } else finishFailure(current, model.error.value ?: "Não foi possível enviar a pergunta.")
    }

    val latestCapture by rememberUpdatedState<(File) -> Unit> { file ->
        val current = ticket
        if (current == null || !gate.accepts(current) || !active || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            file.delete()
        } else {
            val query = question.trim().take(2_000)
            val previous = model.attachment.value
            importPending = true
            model.importCapturedImage(file) // The ViewModel now owns/deletes this cache file.
            task = scope.launch {
                try {
                    model.importing.first { !it }
                    importPending = false
                    if (!gate.accepts(current)) return@launch
                    val path = model.attachment.value
                    if (path == null || path == previous) {
                        finishFailure(current, model.error.value ?: "Não foi possível preparar esta captura. Nenhuma imagem anterior foi enviada.")
                        return@launch
                    }
                    ownedAttachment = path
                    val previousIds = model.messages.value.map { it.id }.toSet()
                    if (!model.send(query)) {
                        if (model.attachment.value == path) model.removeAttachment()
                        ownedAttachment = null
                        finishFailure(current, model.error.value ?: "A análise não pôde começar. Confira a configuração da IA.")
                        return@launch
                    }
                    // send() transfers this image to its own persistence/cancellation
                    // flow. Never delete it from a late panel/import callback.
                    ownedAttachment = null
                    ownsResponse = true
                    awaitAnswer(current, query, previousIds, path)
                } catch (cancelled: CancellationException) { throw cancelled }
            }
        }
    }

    LaunchedEffect(selected, active) {
        if (active && selected != null && sessionConversation == null) sessionConversation = selected
        else if (active && sessionConversation != null && selected != sessionConversation) stop()
    }
    LaunchedEffect(global.active, backgroundVoice.active, configured, settings.endpoint, settings.model) {
        if (active && (global.active || backgroundVoice.active || !configured || settings.endpoint != sessionEndpoint || settings.model != sessionModel)) stop()
    }
    LaunchedEffect(active, periodic) {
        while (active && periodic) {
            delay(1_000L)
            beginCapture(automatic = true)
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                // A pending system permission dialog has no running sensor yet.
                // Its result may start only if the Activity is RESUMED again.
                if (active || working) stop() else voice.stopListening()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CONVERSA VISUAL MULTIMODAL", style = MaterialTheme.typography.titleMedium)
            Text("Captura uma foto real e envia ao modelo de imagens configurado. As perguntas seguintes usam a foto e o histórico desta sessão. A análise periódica usa quadros separados; não é compreensão contínua de vídeo.", style = MaterialTheme.typography.bodySmall)
            if (!configured) Text("Disponível com um provedor multimodal autorizado em Configurações. O CORE não inclui modelo generativo de imagens nem um serviço online já implantado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { if (active) stop() else confirm = true }, enabled = active || configured && capabilities.hasCamera && !busy && !importing && !global.active && !backgroundVoice.active) {
                Text(if (active) "Encerrar conversa visual" else "Iniciar conversa visual")
            }
            if (active) {
                Row {
                    Checkbox(preview, { if (!working) preview = it }, enabled = !working)
                    Text("Mostrar prévia da câmera", Modifier.padding(top = 12.dp))
                }
                VisionFrameCamera(front, preview, if (policy.cameraWidth <= 320) 960 else 1280,
                    Modifier.fillMaxWidth().height(if (preview) 240.dp else 64.dp),
                    onCaptureControl = { captureControl = it },
                    onCapture = { latestCapture(it) },
                    onError = { message -> error = message; stop() })
                if (capabilities.hasFrontCamera && capabilities.hasRearCamera) OutlinedButton(onClick = { front = !front }, enabled = !working) { Text("Trocar câmera") }
                OutlinedTextField(question, { question = it.take(2_000) }, modifier = Modifier.fillMaxWidth(), label = { Text("Pergunta sobre o objeto") }, minLines = 2, enabled = !working)
                Button(onClick = { beginCapture(automatic = false) }, enabled = captureControl != null && !working && !busy && !importing && question.isNotBlank() && !speech.isListening && !speech.isSpeaking) { Text("Capturar e perguntar") }
                OutlinedButton(onClick = { askAboutLastPhoto(search = false) }, enabled = hasImage && !working && !busy && !importing && question.isNotBlank() && !speech.isListening) { Text("Perguntar sobre a última foto") }
                TextButton(onClick = { confirmSearch = true }, enabled = hasImage && !working && !busy && !importing && question.isNotBlank() && question.length <= 500 && !speech.isListening) { Text("Pesquisar sobre o objeto") }
                Row {
                    Checkbox(periodic, { periodic = it })
                    Text("Analisar periodicamente", Modifier.padding(top = 12.dp))
                }
                Text("Se ativado, envia no máximo uma foto a cada 10 segundos, após a análise anterior terminar. Uma falha desativa o envio periódico. Encerre a sessão para liberar a câmera.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    if (speech.isListening) voice.stopListening()
                    else {
                        voice.stopSpeaking()
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            voice.startListening(settings.language, onResult = { heard -> if (gate.active) question = heard.trim().take(2_000) })
                        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }, enabled = speech.available && !working && !busy && !backgroundVoice.active) { Text(if (speech.isListening) "Parar ditado" else "Ditar pergunta") }
                Text("O ditado preenche a pergunta. Toque em Capturar e perguntar para enviar a imagem. O reconhecedor Android pode usar rede.", style = MaterialTheme.typography.bodySmall)
                if (speech.isListening) Text("● Microfone ativo", color = Color(0xFF31E7FA))
                if (working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (importing) "Preparando captura…" else "Analisando a captura com o modelo…")
                    if (streaming.isNotEmpty()) SelectionContainer { Text(streaming) }
                }
                speech.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            answer?.let { text ->
                SelectionContainer { Text(text) }
                if (!working && answerSources.isNotEmpty()) {
                    Text("Fontes retornadas pela pesquisa", style = MaterialTheme.typography.labelLarge)
                    answerSources.forEach { source ->
                        TextButton(onClick = {
                            val uri = Uri.parse(source.url)
                            if (uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                    .onFailure { error = "Não foi possível abrir esta fonte no navegador." }
                            }
                        }) { Text(source.title) }
                    }
                    Text("Essas fontes são resultados da pesquisa. As observações da foto vêm da análise da captura.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { voice.speak(text, settings.language, settings.voiceRate, settings.voiceName, settings.voicePitch) }, enabled = !backgroundVoice.active && !speech.isListening && !working) { Text("Ouvir resposta") }
            }
            if (speech.isSpeaking) TextButton(onClick = voice::stopSpeaking) { Text("Parar voz") }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Autorizar conversa visual?") },
        text = { Text("Será criada uma conversa nova; um anexo ainda não enviado será descartado. As fotos capturadas, as perguntas e as memórias autorizadas serão enviadas ao provedor configurado. Ative a análise periódica somente se desejar enviar fotos durante esta sessão. Os arquivos e o histórico ficam no aparelho até serem excluídos. Sair desta tela ou bloquear a tela encerra a sessão de câmera.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            permissionRequested = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) start()
            else cameraPermission.launch(Manifest.permission.CAMERA)
        }) { Text("Autorizar e iniciar") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
    )
    if (confirmSearch) AlertDialog(
        onDismissRequest = { confirmSearch = false },
        title = { Text("Pesquisar informações do objeto?") },
        text = { Text("A pergunta e o contexto visual serão enviados ao gateway. Ele consultará o mecanismo de pesquisa habilitado pelo operador. As fontes da pesquisa devem ser diferenciadas das observações da foto. Sem essa ferramenta configurada, a pesquisa retorna um erro e não inventa resultados.") },
        confirmButton = { TextButton(onClick = { confirmSearch = false; askAboutLastPhoto(search = true) }) { Text("Pesquisar e perguntar") } },
        dismissButton = { TextButton(onClick = { confirmSearch = false }) { Text("Cancelar") } },
    )
}

private fun findResponse(rows: List<MessageEntity>, previousIds: Set<String>, query: String, image: String?): MessageEntity? {
    val user = rows.indexOfFirst { it.id !in previousIds && it.role == "user" && it.content == query && (image == null || it.imagePath == image) }
    return if (user < 0) null else rows.drop(user + 1).firstOrNull { it.role == "assistant" && it.id !in previousIds }
}

/** Only these two CameraX use cases are owned/unbound here, never unbindAll(). */
@Composable
private fun VisionFrameCamera(
    front: Boolean,
    previewVisible: Boolean,
    edge: Int,
    modifier: Modifier,
    onCaptureControl: ((() -> Boolean)?) -> Unit,
    onCapture: (File) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val captureCallback by rememberUpdatedState(onCapture)
    val errorCallback by rememberUpdatedState(onError)
    val controlCallback by rememberUpdatedState(onCaptureControl)
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val view = remember(context) { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(owner, front, previewVisible, edge) {
        var disposed = false
        var capturing = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var capture: ImageCapture? = null
        ready = false
        controlCallback(null)
        val future = runCatching { ProcessCameraProvider.getInstance(context) }.getOrElse {
            errorCallback("Não foi possível preparar o serviço de câmera neste Android.")
            null
        }
        future?.let { binding -> binding.addListener({
            if (!disposed && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                try {
                    val cameras = binding.get()
                    provider = cameras
                    val preferred = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    if (!cameras.hasCamera(preferred)) error("Câmera selecionada indisponível")
                    val resolution = ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(edge, edge * 3 / 4), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    ).build()
                    val image = ImageCapture.Builder().setResolutionSelector(resolution)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(82).build()
                    capture = image
                    if (previewVisible) {
                        preview = Preview.Builder().setResolutionSelector(resolution).build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        cameras.bindToLifecycle(owner, preferred, preview!!, image)
                    } else cameras.bindToLifecycle(owner, preferred, image)
                    ready = true
                    controlCallback {
                        if (disposed || capturing || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) false
                        else {
                            var pending: File? = null
                            try {
                                val file = File.createTempFile("nexus-vision-session-", ".jpg", context.cacheDir)
                                pending = file
                                capturing = true
                                image.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                                image.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            capturing = false
                                            if (!disposed && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) captureCallback(file)
                                            else file.delete()
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            capturing = false
                                            file.delete()
                                            if (!disposed) errorCallback("Não foi possível capturar esta foto. Confira a câmera e o espaço disponível.")
                                        }
                                    })
                                true
                            } catch (_: Exception) {
                                pending?.delete()
                                capturing = false
                                false
                            }
                        }
                    }
                } catch (_: Exception) {
                    if (!disposed) errorCallback("A câmera não está disponível. Encerre outras sessões que usem a câmera e tente novamente.")
                }
            }
        }, executor) }
        onDispose {
            disposed = true
            ready = false
            controlCallback(null)
            preview?.let { provider?.unbind(it) }
            capture?.let { provider?.unbind(it) }
        }
    }
    Box(modifier.background(Color.Black)) {
        if (previewVisible) AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        Text(if (ready) "● Câmera ativa · fotos ao solicitar" else "Preparando câmera…", Modifier.background(Color.Black.copy(alpha = .75f)).padding(8.dp), color = Color(0xFF31E7FA), style = MaterialTheme.typography.labelSmall)
    }
}
