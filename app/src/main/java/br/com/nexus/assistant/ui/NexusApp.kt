@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package br.com.nexus.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import br.com.nexus.assistant.BuildConfig
import br.com.nexus.assistant.NexusViewModel
import br.com.nexus.assistant.commands.CommandStore
import br.com.nexus.assistant.commands.PhoneCommands
import br.com.nexus.assistant.data.*
import br.com.nexus.assistant.device.*
import br.com.nexus.assistant.gestures.GestureFeatureScreen
import br.com.nexus.assistant.gestures.GestureMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

private data class Destination(val route: String, val title: String, val subtitle: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("chat", "Chat IA", "Texto, contexto e imagens", Icons.Default.Forum),
    Destination("voice", "Assistente de voz", "Ouvir, conversar e interromper", Icons.Default.Mic),
    Destination("vision", "NEXUS Vision", "Capturar e perguntar à IA", Icons.Default.CenterFocusStrong),
    Destination("mouse", "Mouse virtual", "Cursor por rastreamento da mão", Icons.Default.Mouse),
    Destination("hands", "Mãos livres", "Associar poses a ações internas", Icons.Default.PanTool),
    Destination("cube", "NEXUS 3D", "Cubo controlado pelas mãos", Icons.Default.ViewInAr),
    Destination("libras", "NEXUS Libras", "Roteiro de estudos e limites", Icons.Default.SignLanguage),
    Destination("commands", "Central de comandos", "Atalhos e aplicativos", Icons.Default.Terminal),
    Destination("personality", "Personalidade", "O seu jeito de conversar", Icons.Default.AutoAwesome),
    Destination("memory", "Memória e histórico", "Seus dados sob seu controle", Icons.Default.History),
    Destination("settings", "Configurações", "IA, voz e privacidade", Icons.Default.Settings),
    Destination("diagnostics", "Diagnóstico", "Verificar recursos do aparelho", Icons.Default.MonitorHeart),
)

@Composable
fun NexusApp(model: NexusViewModel, voice: VoiceController) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "splash"
    val snackbar = remember { SnackbarHostState() }
    val error by model.error.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val store = remember { CommandStore(context) }
    val commands by store.commands.collectAsStateWithLifecycle(initialValue = emptyList())
    val navigate: (String) -> Unit = { target -> nav.navigate(target) { launchSingleTop = true } }
    val dispatch: (String) -> Boolean = { text ->
        val action = PhoneCommands.resolve(text, commands)
        if (action == null) false else {
            if (action.startsWith("route:")) navigate(action.removePrefix("route:"))
            else PhoneCommands.execute(context, action)?.let(model::report)
            true
        }
    }
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long); model.clearError() }
    }
    Scaffold(
        containerColor = NexusBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (route != "splash") TopAppBar(
                title = {
                    Column {
                        Text(if (route == "home") "N E X U S" else destinations.find { it.route == route }?.title ?: "NEXUS",
                            fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                        Text(if (route == "home") "ASSISTENTE PESSOAL • v${BuildConfig.VERSION_NAME}" else "INTELIGÊNCIA EM SUAS MÃOS",
                            fontSize = 10.sp, color = NexusCyan)
                    }
                },
                navigationIcon = {
                    if (route != "home") IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    if (route != "settings") IconButton(onClick = { navigate("settings") }) { Icon(Icons.Default.Settings, "Configurações") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NexusBackground)
            )
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "splash", modifier = Modifier.padding(padding)) {
            composable("splash") {
                Splash { nav.navigate("home") { popUpTo("splash") { inclusive = true } } }
            }
            composable("home") { Home(settings, navigate) }
            composable("chat") { ChatScreen(model, voice, settings, navigate) }
            composable("voice") {
                PermissionGate(Manifest.permission.RECORD_AUDIO, "Conversa por voz", "O microfone é usado somente enquanto você ativa a escuta. O reconhecedor do Android pode usar a rede quando não houver modelo local.") {
                    VoiceScreen(model, voice, settings, dispatch)
                }
            }
            composable("vision") {
                PermissionGate(Manifest.permission.CAMERA, "NEXUS Vision", "A câmera mostra a cena no aparelho. Só a imagem capturada será enviada quando você tocar em Analisar, após a autorização de envio.") {
                    VisionScreen(model, settings, navigate)
                }
            }
            for ((name, mode) in listOf("mouse" to GestureMode.MOUSE, "hands" to GestureMode.HANDS, "cube" to GestureMode.CUBE)) {
                composable(name) {
                    PermissionGate(Manifest.permission.CAMERA, "Rastreamento de mãos", "A câmera detecta as mãos localmente com MediaPipe. Estes modos controlam apenas o próprio NEXUS.") {
                        GestureFeatureScreen(mode, onNavigateBack = { nav.popBackStack() }, onOpenCommands = { navigate("commands") })
                    }
                }
            }
            composable("libras") { LibrasScreen(navigate) }
            composable("commands") { CommandsScreen(store, dispatch) }
            composable("personality") { PersonalityScreen(settings) { model.saveSettings(it) } }
            composable("memory") { MemoryScreen(model) { navigate("chat") } }
            composable("settings") { SettingsScreen(model, voice, settings) }
            composable("diagnostics") { DiagnosticsScreen(voice, settings, navigate) }
        }
    }
}

@Composable
private fun Splash(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { delay(1_200); onFinished() }
    Column(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF06334A), NexusBackground))),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        NexusOrb(Modifier.size(158.dp), animated = true)
        Spacer(Modifier.height(28.dp))
        Text("N E X U S", fontSize = 34.sp, fontWeight = FontWeight.Light, color = NexusCyan)
        Text("INTELIGÊNCIA EM SUAS MÃOS", fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun NexusOrb(modifier: Modifier, animated: Boolean) {
    val transition = rememberInfiniteTransition(label = "NEXUS")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "rotação")
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(NexusCyan.copy(alpha = .07f), r)
        drawCircle(NexusCyan.copy(alpha = .25f), r * .85f, style = Stroke(1.5.dp.toPx()))
        drawCircle(NexusBlue.copy(alpha = .5f), r * .62f, style = Stroke(1.dp.toPx()))
        drawArc(NexusCyan, if (animated) angle else 20f, 100f, false,
            topLeft = Offset(r * .15f, r * .15f), size = androidx.compose.ui.geometry.Size(r * 1.7f, r * 1.7f),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawLine(NexusCyan, Offset(r * .7f, r * 1.28f), Offset(r * .7f, r * .7f), 4.dp.toPx(), StrokeCap.Round)
        drawLine(NexusCyan, Offset(r * .7f, r * .7f), Offset(r * 1.3f, r * 1.28f), 4.dp.toPx(), StrokeCap.Round)
        drawLine(NexusCyan, Offset(r * 1.3f, r * 1.28f), Offset(r * 1.3f, r * .7f), 4.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun Home(settings: NexusSettings, navigate: (String) -> Unit) = ScrollPage {
    val configured = settings.endpoint.isNotBlank() && settings.model.isNotBlank() && settings.remoteConsent
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (settings.userName.isBlank()) "Olá. Eu sou o NEXUS." else "Olá, ${settings.userName}.", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text("Um espaço para conversar, explorar e aprender.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        NexusOrb(Modifier.size(76.dp), animated = true)
    }
    InfoCard(if (!configured) "CONFIGURE SUA IA" else "SERVIDOR CONFIGURADO",
        if (!configured) "Conecte o NEXUS ao seu servidor de IA. O modelo pode rodar gratuitamente em um computador com Ollama."
        else "Modelo: ${settings.model}. A disponibilidade será verificada ao enviar uma mensagem.")
    Button(onClick = { navigate(if (configured) "chat" else "settings") }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(if (configured) "Iniciar conversa" else "Conectar uma IA")
    }
    Text("EXPLORE O NEXUS", color = NexusCyan, style = MaterialTheme.typography.labelLarge)
    destinations.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            pair.forEach { destination ->
                Card(onClick = { navigate(destination.route) }, modifier = Modifier.weight(1f).heightIn(min = 136.dp),
                    colors = CardDefaults.cardColors(containerColor = NexusPanel)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(destination.icon, null, tint = NexusCyan, modifier = Modifier.size(25.dp))
                        Text(destination.title, fontWeight = FontWeight.SemiBold)
                        Text(destination.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    Text("Versão em desenvolvimento. Câmera, áudio e gestos precisam de validação em um aparelho físico.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChatScreen(model: NexusViewModel, voice: VoiceController, settings: NexusSettings, navigate: (String) -> Unit) {
    val messages by model.messages.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val importing by model.importing.collectAsStateWithLifecycle()
    val attachment by model.attachment.collectAsStateWithLifecycle()
    var draft by rememberSaveable(selected) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(model::importImage) }
    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${messages.size} mensagens • contexto limitado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { model.newConversation() }) { Icon(Icons.Default.Add, null); Text("Nova") }
        }
        if (settings.endpoint.isBlank() || !settings.remoteConsent) {
            TextButton(onClick = { navigate("settings") }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Conecte seu servidor de IA em Configurações") }
        }
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                NexusOrb(Modifier.size(108.dp), animated = busy)
                Spacer(Modifier.height(20.dp)); Text("O que vamos explorar?", fontSize = 23.sp)
                Text("Envie uma pergunta ou uma imagem. As respostas vêm do modelo configurado.", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(messages, key = { it.id }) { message ->
                Card(colors = CardDefaults.cardColors(containerColor = if (message.role == "user") Color(0xFF102B3D) else NexusPanel), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (message.role == "user") "VOCÊ" else "NEXUS", color = NexusCyan, style = MaterialTheme.typography.labelSmall)
                            if (message.role == "assistant") IconButton(onClick = { voice.speak(message.content, settings.language, settings.voiceRate, settings.voiceName) }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.VolumeUp, "Ler resposta em voz alta")
                            }
                        }
                        message.imagePath?.let { LocalImage(it, Modifier.fillMaxWidth().heightIn(max = 200.dp)) }
                        SelectionContainer { Text(message.content, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
        }
        if (busy) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("  Processando com o modelo…", modifier = Modifier.weight(1f))
            TextButton(onClick = model::cancelResponse) { Text("Interromper") }
        } else if (messages.lastOrNull()?.role == "user") TextButton(onClick = { model.retry() }, modifier = Modifier.align(Alignment.End)) { Text("Tentar novamente") }
        if (attachment != null) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            LocalImage(attachment!!, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
            Text("Imagem pronta para envio", Modifier.weight(1f).padding(8.dp))
            IconButton(onClick = model::removeAttachment) { Icon(Icons.Default.Close, "Remover imagem") }
        }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy && !importing) { Icon(Icons.Default.AddPhotoAlternate, "Anexar imagem") }
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Pergunte ao NEXUS…") }, maxLines = 5, enabled = !busy, shape = RoundedCornerShape(22.dp))
            IconButton(onClick = { if (model.send(draft)) draft = "" }, enabled = !busy && !importing && (draft.isNotBlank() || attachment != null)) {
                Icon(Icons.AutoMirrored.Filled.Send, "Enviar mensagem", tint = NexusCyan)
            }
        }
    }
    DisposableEffect(Unit) { onDispose { voice.stopSpeaking() } }
}

@Composable
private fun VoiceScreen(model: NexusViewModel, voice: VoiceController, settings: NexusSettings, dispatch: (String) -> Boolean) {
    val state by voice.state.collectAsStateWithLifecycle()
    val messages by model.messages.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val importing by model.importing.collectAsStateWithLifecycle()
    val attachment by model.attachment.collectAsStateWithLifecycle()
    val responseError by model.error.collectAsStateWithLifecycle()
    // A restored screen must never reopen its microphone without a new user action.
    var continuous by remember { mutableStateOf(false) }
    var lastHeard by rememberSaveable { mutableStateOf("") }
    var awaitingQuery by remember { mutableStateOf<String?>(null) }
    var awaitingSince by remember { mutableStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val receive: (String) -> Unit = { heard ->
        lastHeard = heard
        if (!dispatch(heard)) {
            val query = PhoneCommands.removeWakePhrase(heard)
            if (query.isBlank()) {
                model.report("Pode falar sua pergunta. A frase de ativação funciona somente com esta tela aberta.")
            } else {
                val sentAt = System.currentTimeMillis()
                if (model.send(query)) {
                    awaitingSince = sentAt
                    awaitingQuery = query
                } else {
                    awaitingQuery = null
                    continuous = false
                }
            }
        }
    }
    val currentReceive by rememberUpdatedState(receive)
    val last = messages.lastOrNull()
    LaunchedEffect(messages, awaitingQuery, busy) {
        val query = awaitingQuery
        if (query != null && !busy && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // Speak only a reply to a send accepted from this screen. Loading an existing
            // conversation, entering this screen, or returning from background stays silent.
            val userIndex = messages.indexOfLast {
                it.role == "user" && it.content == query && it.createdAt >= awaitingSince
            }
            val reply = if (userIndex >= 0) messages.drop(userIndex + 1).firstOrNull { it.role == "assistant" } else null
            if (reply != null) {
                awaitingQuery = null
                voice.speak(reply.content, settings.language, settings.voiceRate, settings.voiceName)
            }
        }
    }
    LaunchedEffect(responseError) {
        if (responseError != null && awaitingQuery != null) {
            awaitingQuery = null
            continuous = false
        }
    }
    LaunchedEffect(continuous, busy, importing, awaitingQuery, state.isListening, state.isSpeaking, state.error, state.ttsReady, settings.language) {
        if (voice.state.value.error != null) continuous = false
        if (continuous && !busy && !importing && awaitingQuery == null && !state.isListening && !state.isSpeaking && state.ttsReady) {
            delay(900)
            val current = voice.state.value
            if (continuous && !model.busy.value && !model.importing.value && awaitingQuery == null &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                !current.isSpeaking && !current.isListening && current.error == null) {
                voice.startListening(settings.language, onResult = { currentReceive(it) })
            }
        }
    }
    DisposableEffect(lifecycle, voice) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                continuous = false
                awaitingQuery = null
                voice.stopListening()
                voice.stopSpeaking()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            continuous = false
            awaitingQuery = null
            voice.stopListening()
            voice.stopSpeaking()
        }
    }
    ScrollPage {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { NexusOrb(Modifier.size(160.dp), state.isListening || state.isSpeaking || busy) }
        Text(when { state.isListening -> "Ouvindo você"; state.isSpeaking -> "NEXUS falando"; busy -> "Pensando com a IA"; else -> "Vamos conversar" }, fontSize = 26.sp)
        Text(if (state.onDevice) "Reconhecimento local disponível" else "Reconhecimento pelo serviço Android; pode usar a rede", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.speechUsesNetwork) Text("A voz selecionada exige rede e pode enviar texto ao mecanismo de fala do Android.", style = MaterialTheme.typography.bodySmall)
        if (state.isListening) LinearProgressIndicator(progress = { state.level.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        if (state.partialText.isNotBlank()) Text(state.partialText, color = NexusCyan)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                awaitingQuery = null
                if (state.isListening) { continuous = false; voice.stopListening() }
                else { voice.clearError(); voice.stopSpeaking(); voice.startListening(settings.language, onResult = receive) }
            }, enabled = state.available && !busy && !importing, modifier = Modifier.weight(1f)) {
                Icon(if (state.isListening) Icons.Default.MicOff else Icons.Default.Mic, null)
                Text(if (state.isListening) " Parar" else " Falar")
            }
            OutlinedButton(onClick = {
                continuous = false; awaitingQuery = null
                voice.stopListening(); voice.stopSpeaking(); model.cancelResponse()
            }, modifier = Modifier.weight(1f)) { Text("Interromper") }
        }
        ToggleRow("Conversa contínua nesta tela", continuous, {
            voice.clearError(); continuous = it
            if (!it) voice.stopListening()
        })
        state.error?.let { InfoCard("ÁUDIO", it) }
        if (!state.available) InfoCard("RECONHECEDOR AUSENTE", "Instale ou habilite um serviço de reconhecimento de fala do Android para usar o microfone.")
        if (attachment != null) {
            InfoCard("IMAGEM ANEXADA", "A próxima pergunta também enviará a imagem selecionada anteriormente ao servidor configurado.")
            TextButton(onClick = model::removeAttachment) { Text("Remover imagem antes de falar") }
        }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (lastHeard.isNotBlank()) InfoCard("VOCÊ DISSE", lastHeard)
        last?.let { InfoCard(if (it.role == "assistant") "NEXUS" else "MENSAGEM", it.content) }
        InfoCard("EI, NEXUS", "Você pode começar a fala com “Ei, NEXUS”. A escuta precisa estar ativada nesta tela. A sessão termina ao sair do aplicativo. Erros e silêncio interrompem o modo contínuo.")
    }
}

@Composable
private fun VisionScreen(model: NexusViewModel, settings: NexusSettings, navigate: (String) -> Unit) {
    // Camera activation is intentionally not restored after recreation or backgrounding.
    var cameraActive by remember { mutableStateOf(false) }
    var front by rememberSaveable { mutableStateOf(false) }
    var question by rememberSaveable { mutableStateOf("O que aparece nesta imagem?") }
    val attachment by model.attachment.collectAsStateWithLifecycle()
    val importing by model.importing.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) cameraActive = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); cameraActive = false }
    }
    ScrollPage {
        InfoCard("ANÁLISE POR CAPTURA", "A visualização é ao vivo. A IA recebe apenas a foto selecionada ao tocar em Analisar; não recebe vídeo contínuo.")
        if (cameraActive) {
            CameraPanel(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(20.dp)), frontCamera = front,
                onCapture = { file ->
                    cameraActive = false
                    // Ownership transfers to the ViewModel, which deletes the raw cache file
                    // after copying/compressing it, including cancellation or rejected imports.
                    model.importCapturedImage(file)
                },
                onError = { message -> cameraActive = false; model.report(message) })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { front = !front }) { Text("Alternar câmera") }
                TextButton(onClick = { cameraActive = false }) { Text("Encerrar câmera") }
            }
        } else Button(onClick = { cameraActive = true }, enabled = !busy && !importing) { Icon(Icons.Default.CameraAlt, null); Text(" Abrir câmera") }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        attachment?.let { LocalImage(it, Modifier.fillMaxWidth().heightIn(max = 260.dp)); TextButton(onClick = model::removeAttachment) { Text("Descartar foto") } }
        OutlinedTextField(question, { question = it }, label = { Text("Pergunta sobre a captura") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Button(onClick = { if (model.send(question)) navigate("chat") }, enabled = attachment != null && !busy && !importing && !cameraActive) { Text("Analisar com a IA") }
        if (!settings.remoteConsent) Text("Autorize o envio para o servidor em Configurações antes de analisar.", color = MaterialTheme.colorScheme.error)
        Text("O modelo configurado precisa aceitar imagens. Textos pequenos e detalhes desfocados podem não ser reconhecidos.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PersonalityScreen(settings: NexusSettings, save: (NexusSettings) -> Unit) {
    var name by rememberSaveable(settings.userName) { mutableStateOf(settings.userName) }
    var style by rememberSaveable(settings.personality) { mutableStateOf(settings.personality) }
    var detail by rememberSaveable(settings.detail) { mutableStateOf(settings.detail) }
    var language by rememberSaveable(settings.language) { mutableStateOf(settings.language) }
    ScrollPage {
        Text("Uma IA com o seu jeito", fontSize = 26.sp)
        Text("Estas preferências entram no contexto das próximas perguntas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name, { name = it.take(100) }, label = { Text("Como devo chamar você?") }, modifier = Modifier.fillMaxWidth())
        ChoiceField("Personalidade", style, listOf("Amigável", "Profissional", "Divertido", "Direto", "Professor", "Técnico")) { style = it }
        OutlinedTextField(style, { style = it.take(2000) }, label = { Text("Estilo ou instruções personalizadas") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        ChoiceField("Detalhamento", detail, listOf("Curto", "Equilibrado", "Detalhado")) { detail = it }
        ChoiceField("Idioma das respostas", language, listOf("pt-BR", "en-US", "es-ES")) { language = it }
        Button(onClick = { save(settings.copy(userName = name, personality = style, detail = detail, language = language)) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar personalidade") }
        Text("A interface desta versão está em português. A tradução completa da interface está pendente.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingsScreen(model: NexusViewModel, voice: VoiceController, settings: NexusSettings) {
    var endpoint by rememberSaveable(settings.endpoint) { mutableStateOf(settings.endpoint) }
    var aiModel by rememberSaveable(settings.model) { mutableStateOf(settings.model) }
    var token by remember { mutableStateOf("") }
    var replaceToken by remember { mutableStateOf(false) }
    var consent by rememberSaveable(settings.remoteConsent) { mutableStateOf(settings.remoteConsent) }
    var rate by remember(settings.voiceRate) { mutableFloatStateOf(settings.voiceRate) }
    var voiceName by rememberSaveable(settings.voiceName) { mutableStateOf(settings.voiceName) }
    val voices by voice.voices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ScrollPage {
        InfoCard("CONEXÃO REAL COM IA", "O aplicativo não inclui modelo nem respostas simuladas. Use o servidor NEXUS com Ollama gratuito ou um provedor compatível. Chaves privadas ficam no servidor.")
        OutlinedTextField(endpoint, {
            endpoint = it
            if (it.trim() != settings.endpoint.trim()) consent = false
        }, label = { Text("URL HTTPS do servidor") }, placeholder = { Text("https://seu-servidor/v1") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
        OutlinedTextField(aiModel, { aiModel = it.take(200) }, label = { Text("Modelo instalado no servidor") }, placeholder = { Text("Ex.: gemma3:4b (Ollama)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false))
        if (endpoint.trim() != settings.endpoint.trim()) Text("Ao mudar o endereço, o token anterior será apagado. Informe o token do novo servidor e autorize o envio novamente.", style = MaterialTheme.typography.bodySmall)
        ToggleRow("Atualizar token de sessão", replaceToken, { replaceToken = it })
        if (replaceToken) {
            OutlinedTextField(token, { token = it }, label = { Text("Token do seu servidor NEXUS") }, supportingText = { Text("Vazio apaga o token. Não use aqui a chave privada do provedor.") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
        }
        ToggleRow("Autorizar envio ao servidor configurado", consent, { consent = it })
        Text("Ao enviar, o servidor recebe sua mensagem, preferências, memórias autorizadas, até 24 mensagens e até 2 imagens recentes. Áudios não são enviados ao chat; o serviço de fala do Android tem sua própria política.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Voz", fontSize = 22.sp)
        Text("Velocidade: ${"%.1f".format(rate)}×")
        Slider(rate, { rate = it }, valueRange = .5f..2f)
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(voices.find { it.name == voiceName }?.label ?: "Voz padrão do sistema") }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Padrão do sistema") }, onClick = { voiceName = ""; expanded = false })
                voices.forEach { option -> DropdownMenuItem(text = { Text(option.label + if (option.requiresNetwork) " • rede" else " • local") }, onClick = { voiceName = option.name; expanded = false }) }
            }
        }
        OutlinedButton(onClick = { voice.speak("Olá! Eu sou o NEXUS, seu assistente pessoal.", settings.language, rate, voiceName) }) { Text("Testar voz selecionada") }
        Button(onClick = {
            val submittedToken = token
            model.saveSettings(settings.copy(endpoint = endpoint.trim(), model = aiModel.trim(), remoteConsent = consent, voiceRate = rate, voiceName = voiceName), if (replaceToken) token else null) {
                if (token == submittedToken) { token = ""; replaceToken = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Salvar configurações") }
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Gerenciar permissões do Android") }
        InfoCard("PRIVACIDADE", "Câmera e microfone só são solicitados ao abrir suas funções. Conversas ficam no armazenamento privado do app. O token de sessão é protegido pelo Android Keystore. Backup automático está desativado.")
        Text("Tema escuro padrão • interface adaptável • versão ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    }
    DisposableEffect(Unit) { onDispose { voice.stopSpeaking() } }
}

@Composable
private fun MemoryScreen(model: NexusViewModel, openChat: () -> Unit) {
    val memories by model.memories.collectAsStateWithLifecycle()
    val conversations by model.conversations.collectAsStateWithLifecycle()
    var memoryDraft by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::export) }
    ScrollPage {
        InfoCard("MEMÓRIAS AUTORIZADAS", "Somente o que você salvar aqui será usado como memória pessoal nas conversas. Você pode editar ou apagar a qualquer momento.")
        OutlinedTextField(memoryDraft, { memoryDraft = it.take(2000) }, label = { Text(if (editingId == null) "Informação que posso lembrar" else "Editar memória") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { model.saveMemory(memoryDraft, editingId); memoryDraft = ""; editingId = null }, enabled = memoryDraft.isNotBlank()) { Text("Salvar memória") }
            if (editingId != null) TextButton(onClick = { editingId = null; memoryDraft = "" }) { Text("Cancelar") }
        }
        memories.forEach { memory ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(memory.content)
                    Row {
                        TextButton(onClick = { editingId = memory.id; memoryDraft = memory.content }) { Text("Editar") }
                        TextButton(onClick = { model.deleteMemory(memory.id) }) { Text("Excluir") }
                    }
                }
            }
        }
        Text("Conversas salvas (${conversations.size})", fontSize = 22.sp)
        if (conversations.isEmpty()) Text("Suas próximas conversas aparecerão aqui.")
        conversations.forEach { conversation ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { model.openConversation(conversation.id); openChat() }, modifier = Modifier.weight(1f)) { Text(conversation.title) }
                    IconButton(onClick = { model.deleteConversation(conversation.id) }) { Icon(Icons.Default.DeleteOutline, "Excluir conversa ${conversation.title}") }
                }
            }
        }
        OutlinedButton(onClick = { export.launch("nexus-dados.json") }) { Text("Exportar conversas e memórias (JSON)") }
        Text("A exportação contém textos pessoais. Imagens e token não são incluídos.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { confirmDeleteAll = true }) { Text("Apagar dados e preferências", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDeleteAll) AlertDialog(onDismissRequest = { confirmDeleteAll = false }, title = { Text("Apagar os dados locais?") },
        text = { Text("Conversas, imagens anexadas, memórias, token e preferências da IA serão removidos deste aparelho. A exclusão não pode ser desfeita.") },
        confirmButton = { TextButton(onClick = { confirmDeleteAll = false; model.deleteAllData() }) { Text("Apagar") } },
        dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancelar") } })
}

@Composable
private fun CommandsScreen(store: CommandStore, dispatch: (String) -> Boolean) {
    val commands by store.commands.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phrase by rememberSaveable { mutableStateOf("") }
    var packageName by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var chooseApp by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val apps by produceState<List<Pair<String, String>>>(emptyList()) { value = withContext(Dispatchers.IO) { PhoneCommands.installedApps(context) } }
    ScrollPage {
        InfoCard("AÇÕES AUTORIZADAS", "Diga um comando completo no Assistente de voz. Os botões abaixo executam as mesmas ações oficiais do Android.")
        PhoneCommands.examples.forEach { example -> OutlinedButton(onClick = { dispatch(example) }, modifier = Modifier.fillMaxWidth()) { Text(example) } }
        Text("Seu comando", fontSize = 22.sp)
        OutlinedTextField(phrase, { phrase = it.take(100) }, label = { Text("Ex.: Abrir meu jogo") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { chooseApp = true }) { Text(if (label.isBlank()) "Escolher aplicativo" else label) }
        Button(onClick = {
            scope.launch {
                try { store.save(phrase, packageName, label); phrase = ""; error = null }
                catch (failure: Exception) { error = failure.message ?: "Falha ao salvar comando." }
            }
        }, enabled = phrase.isNotBlank() && packageName.isNotBlank()) { Text("Salvar comando") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        commands.forEach { command ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { dispatch(command.phrase) }, modifier = Modifier.weight(1f)) { Text("${command.phrase} → ${command.label}") }
                IconButton(onClick = { scope.launch { store.delete(command.id) } }) { Icon(Icons.Default.DeleteOutline, "Excluir comando ${command.phrase}") }
            }
        }
        InfoCard("LIMITES DESTA VERSÃO", "O mouse atua dentro do NEXUS. Controle de outros apps e bloqueio de tela ainda não foram implementados. Nenhum serviço de acessibilidade é ativado.")
    }
    if (chooseApp) AlertDialog(onDismissRequest = { chooseApp = false }, title = { Text("Aplicativos disponíveis") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            if (apps.isEmpty()) item { Text("Nenhum aplicativo com atalho disponível.") }
            items(apps, key = { it.first }) { app -> TextButton(onClick = { packageName = app.first; label = app.second; chooseApp = false }) { Text(app.second) } }
        }
    }, confirmButton = { TextButton(onClick = { chooseApp = false }) { Text("Fechar") } })
}

@Composable
private fun LibrasScreen(navigate: (String) -> Unit) = ScrollPage {
    Text("Aprender com responsabilidade", fontSize = 26.sp)
    InfoCard("TUTOR EM DESENVOLVIMENTO", "O rastreador identifica mãos, mas esta versão ainda não possui referências de sinais validadas por educadores de Libras. Não atribui notas nem inventa correções.")
    Text("Libras é uma língua completa", fontSize = 22.sp)
    Text("Configuração da mão é só um dos elementos. Movimento, orientação, localização, expressões não manuais e gramática também são fundamentais.")
    InfoCard("ROTEIRO INICIAL", "1. Conhecer os parâmetros de Libras.\n2. Estudar demonstrações de professores e pessoas surdas.\n3. Praticar configurações de mão.\n4. Revisar sinais em contexto com um educador.")
    Button(onClick = { navigate("hands") }) { Text("Explorar rastreamento de mãos") }
    Text("O modo de mãos reconhece poses geométricas. Ele não traduz nem valida Libras.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    val context = LocalContext.current
    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ines.gov.br/"))) }) { Text("Conhecer o INES — referência educacional") }
    Text("Pendentes: biblioteca com licença e validação pedagógica, vídeos de demonstração, avaliações confiáveis, níveis e progresso.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DiagnosticsScreen(voice: VoiceController, settings: NexusSettings, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val voiceState by voice.state.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    val diagnostics = remember(refresh, voiceState.available, voiceState.ttsReady, settings) {
        listOf(
            "Android" to "${android.os.Build.VERSION.RELEASE} • API ${android.os.Build.VERSION.SDK_INT}",
            "Câmera disponível" to if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) "Sim" else "Não",
            "Permissão de câmera" to permissionStatus(context, Manifest.permission.CAMERA),
            "Permissão de microfone" to permissionStatus(context, Manifest.permission.RECORD_AUDIO),
            "Reconhecimento de fala" to if (voiceState.available) (if (voiceState.onDevice) "Local disponível" else "Serviço do sistema disponível") else "Indisponível",
            "Síntese de voz" to if (voiceState.ttsReady) "Inicializada" else "Ainda indisponível",
            "Modelo de mãos" to if (runCatching { context.assets.open("hand_landmarker.task").use { it.read() } }.isSuccess) "Asset presente; execução ainda requer teste" else "Ausente: baixar modelo oficial antes de compilar",
            "IA configurada" to if (settings.endpoint.isNotBlank() && settings.model.isNotBlank()) "Sim; conexão ainda não verificada" else "Não",
            "Envio de dados à IA" to if (settings.remoteConsent) "Autorizado pelo usuário" else "Desativado",
            "Build" to "${BuildConfig.VERSION_NAME} • ${if (BuildConfig.DEBUG) "debug" else "release"}",
        )
    }
    ScrollPage {
        InfoCard("VERIFICAÇÃO LOCAL", "Esta lista informa disponibilidade e permissões. Ela não substitui testar câmera, microfone, modelo e desempenho no aparelho.")
        diagnostics.forEach { (name, result) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(result, Modifier.weight(1f))
        }; HorizontalDivider() }
        Button(onClick = { refresh++ }) { Text("Atualizar verificação") }
        OutlinedButton(onClick = { navigate("vision") }) { Text("Testar câmera") }
        OutlinedButton(onClick = { navigate("voice") }) { Text("Testar microfone e voz") }
        OutlinedButton(onClick = { navigate("mouse") }) { Text("Testar rastreamento e cursor") }
        OutlinedButton(onClick = { navigate("chat") }) { Text("Testar IA com uma mensagem") }
    }
}

private fun permissionStatus(context: android.content.Context, permission: String) =
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) "Concedida" else "Não concedida"

@Composable
private fun PermissionGate(permission: String, title: String, explanation: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var granted by remember { mutableStateOf(permissionStatus(context, permission) == "Concedida") }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    DisposableEffect(lifecycle, permission) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = permissionStatus(context, permission) == "Concedida" }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (granted) content() else ScrollPage {
        Text(title, fontSize = 26.sp)
        InfoCard("PERMISSÃO NECESSÁRIA", explanation)
        Button(onClick = { request.launch(permission) }) { Text(if (permission == Manifest.permission.CAMERA) "Autorizar câmera" else "Autorizar microfone") }
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Abrir permissões do aplicativo") }
        Text("Você pode continuar usando as demais funções sem conceder esta permissão.")
    }
}

@Composable
private fun LocalImage(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 768) sample *= 2
                    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    })
                }
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), "Imagem anexada", modifier.clip(RoundedCornerShape(10.dp))) }
        ?: Text("Não foi possível carregar a imagem.", modifier, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
private fun InfoCard(title: String, description: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = NexusPanel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = NexusCyan, style = MaterialTheme.typography.labelMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp)); Switch(checked, onCheckedChange)
    }
}

@Composable
private fun ChoiceField(label: String, value: String, choices: List<String>, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(value.ifBlank { "Escolher" }); Icon(Icons.Default.ArrowDropDown, null) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { choose(choice); expanded = false }) }
            }
        }
    }
}
