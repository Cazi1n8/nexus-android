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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import br.com.nexus.assistant.data.local.LocalModelStatus
import br.com.nexus.assistant.data.local.LocalModelProfile
import br.com.nexus.assistant.data.local.LocalModelCatalog
import br.com.nexus.assistant.device.*
import br.com.nexus.assistant.gestures.GestureFeatureScreen
import br.com.nexus.assistant.gestures.GestureMode
import br.com.nexus.assistant.gestures.GestureSessionHost
import br.com.nexus.assistant.gestures.rememberGestureSession
import br.com.nexus.assistant.gestures.global.GlobalMouseRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
    Destination("libras", "NEXUS Libras", "Exercícios preparatórios", Icons.Default.SignLanguage),
    Destination("commands", "Central de comandos", "Atalhos e aplicativos", Icons.Default.Terminal),
    Destination("personality", "Personalidade", "O seu jeito de conversar", Icons.Default.AutoAwesome),
    Destination("memory", "Memória e histórico", "Seus dados sob seu controle", Icons.Default.History),
    Destination("settings", "Configurações", "IA, voz e privacidade", Icons.Default.Settings),
    Destination("diagnostics", "Diagnóstico", "Verificar recursos do aparelho", Icons.Default.MonitorHeart),
)

@Composable
fun NexusApp(model: NexusViewModel, voice: VoiceController) {
    val nav = rememberNavController()
    val gestureSession = rememberGestureSession()
    val globalMouse by GlobalMouseRuntime.state.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "splash"
    LaunchedEffect(route) {
        if (route in listOf("vision", "cube", "hands", "libras")) gestureSession.stopMouse()
    }
    val snackbar = remember { SnackbarHostState() }
    val error by model.error.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val capabilities by model.deviceCapabilities.collectAsStateWithLifecycle()
    val policy by model.devicePolicy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestSettings by rememberUpdatedState(settings)
    val latestRoute by rememberUpdatedState(route)
    val voiceState by voice.state.collectAsStateWithLifecycle()
    val voices by voice.voices.collectAsStateWithLifecycle()
    LaunchedEffect(settings.language) { voice.setLanguage(settings.language) }
    LaunchedEffect(settings.voiceName, settings.language, voices, voiceState.ttsReady, voiceState.language) {
        if (voiceState.ttsReady && voiceState.language == settings.language && voices.isNotEmpty() &&
            settings.voiceName.isNotBlank() && voices.none { it.name == settings.voiceName }) {
            model.saveSettings(settings.copy(voiceName = ""))
        }
    }
    LaunchedEffect(model, voice, lifecycle) {
        model.assistantReplies.collect { reply ->
            val current = latestSettings
            if (current.autoSpeak && latestRoute != "voice" && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                voice.speak(reply, current.language, current.voiceRate, current.voiceName, current.voicePitch)
            }
        }
    }
    val store = remember { CommandStore(context) }
    val commands by store.commands.collectAsStateWithLifecycle(initialValue = emptyList())
    val navigate: (String) -> Unit = { target ->
        if (target in listOf("vision", "cube", "hands", "libras")) gestureSession.stopMouse()
        nav.navigate(target) { launchSingleTop = true }
    }
    val executeAction: (String) -> Unit = { action ->
        if (action.startsWith("route:")) navigate(action.removePrefix("route:"))
        else PhoneCommands.execute(context, action)?.let(model::report)
    }
    val dispatch: (String) -> Boolean = { text ->
        when (PhoneCommands.normalize(PhoneCommands.removeWakePhrase(text))) {
            "desativar mouse virtual" -> {
                gestureSession.stopMouse()
                GlobalMouseRuntime.stop(context)
                true
            }
            "ativar mouse virtual" -> {
                navigate("mouse")
                if (policy.handTrackingSupported && !globalMouse.active && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    gestureSession.startMouse()
                }
                true
            }
            else -> {
                val action = PhoneCommands.resolve(text, commands)
                if (action == null) false else { executeAction(action); true }
            }
        }
    }
    val pendingAction by model.pendingAction.collectAsStateWithLifecycle()
    pendingAction?.let { proposal ->
        AlertDialog(onDismissRequest = model::dismissPendingAction,
            title = { Text("Autorizar ação sugerida pela IA?") },
            text = { Text("${proposal.label}\n\nA ação só será executada depois da sua confirmação.") },
            confirmButton = { TextButton(onClick = {
                model.takePendingAction(proposal.id)?.let { executeAction(it.action) }
            }) { Text("Confirmar ação") } },
            dismissButton = { TextButton(onClick = model::dismissPendingAction) { Text("Cancelar") } })
    }
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long); model.clearError() }
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = NexusBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (route != "splash") TopAppBar(
                title = {
                    Column {
                        Text(if (route == "home") "N E X U S" else destinations.find { it.route == route }?.title ?: "NEXUS",
                            fontWeight = FontWeight.SemiBold, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (LocalDensity.current.fontScale <= 1.3f) Text(if (route == "home") "ASSISTENTE PESSOAL • v${BuildConfig.VERSION_NAME}" else "INTELIGÊNCIA EM SUAS MÃOS",
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
            composable("home") { Home(model, settings, navigate) }
            composable("chat") { ChatScreen(model, voice, settings, navigate) }
            composable("voice") {
                CompatibilityGate(capabilities.hasMicrophone && voiceState.available, "Conversa por voz",
                    if (!capabilities.hasMicrophone) "O Android não informa um microfone neste aparelho. Você pode continuar conversando por texto."
                    else "Nenhum serviço de reconhecimento de fala está disponível. Instale ou habilite um serviço de fala nas configurações do Android; o chat por texto continua disponível.",
                    onContinue = { navigate("chat") }) {
                PermissionGate(Manifest.permission.RECORD_AUDIO, "Conversa por voz", "O microfone é usado somente enquanto você ativa a escuta. O reconhecedor do Android pode usar a rede quando não houver modelo local.") {
                    VoiceScreen(model, voice, settings, dispatch)
                }
                }
            }
            composable("vision") {
                CompatibilityGate(capabilities.hasFrontCamera || capabilities.hasRearCamera, "NEXUS Vision", "O Android não informa uma câmera frontal ou traseira compatível neste aparelho. O chat por texto permanece disponível; você também pode anexar uma imagem da galeria a um provedor multimodal personalizado.", onContinue = { navigate("chat") }) {
                GlobalCameraGate {
                PermissionGate(Manifest.permission.CAMERA, "NEXUS Vision", "A câmera mostra a cena no aparelho. Só a imagem capturada será enviada quando você tocar em Analisar, após a autorização de envio.") {
                    VisionScreen(model, settings, navigate)
                }
                }
                }
            }
            for ((name, mode) in listOf("mouse" to GestureMode.MOUSE, "hands" to GestureMode.HANDS, "cube" to GestureMode.CUBE)) {
                composable(name) {
                    CompatibilityGate(if (mode == GestureMode.CUBE) policy.supportsGles2 else policy.handTrackingSupported,
                        if (mode == GestureMode.CUBE) "NEXUS 3D" else "Rastreamento de mãos",
                        when {
                            !capabilities.hasCamera -> "O Android não informa uma câmera neste aparelho. O rastreamento de mãos precisa de uma câmera; as demais áreas do NEXUS continuam disponíveis."
                            !policy.handTrackingSupported -> "O rastreador MediaPipe deste APK não tem uma biblioteca compatível com o processo ${capabilities.processAbi}. Câmera, chat e conteúdos de Libras continuam disponíveis quando suportados."
                            else -> "Este aparelho não declara OpenGL ES 2.0, necessário para renderizar o cubo. O chat e as demais funções compatíveis continuam disponíveis."
                        }, onContinue = { navigate("home") }) {
                    val feature: @Composable () -> Unit = {
                        if (mode == GestureMode.CUBE) {
                            // Manual 3D exploration requires GLES, but neither camera nor accessibility.
                            GestureFeatureScreen(mode, onNavigateBack = { nav.popBackStack() }, onOpenCommands = { navigate("commands") }, session = gestureSession)
                        } else PermissionGate(Manifest.permission.CAMERA, "Rastreamento de mãos", "A câmera detecta as mãos localmente com MediaPipe. O controle global opcional exige uma ativação separada e explícita na tela de mouse.") {
                            GestureFeatureScreen(mode, onNavigateBack = { nav.popBackStack() }, onOpenCommands = { navigate("commands") }, session = gestureSession)
                        }
                    }
                    if (mode == GestureMode.HANDS) GlobalCameraGate(feature) else feature()
                    }
                }
            }
            composable("libras") { GlobalCameraGate { br.com.nexus.assistant.libras.NexusLibrasScreen() } }
            composable("commands") { CommandsScreen(store, dispatch) }
            composable("personality") { PersonalityScreen(settings) { model.saveSettings(it) } }
            composable("memory") {
                MemoryScreen(model, openChat = { navigate("chat") }, onBeforeDeleteAll = {
                    gestureSession.resetPreferences()
                    GlobalMouseRuntime.stop(context)
                    voice.stopListening()
                    voice.stopSpeaking()
                })
            }
            composable("settings") { SettingsScreen(model, voice, settings) }
            composable("diagnostics") { br.com.nexus.assistant.diagnostics.NexusDiagnosticsScreen(model, voice, settings, navigate) }
        }
    }
    GestureSessionHost(gestureSession, onNavigateBack = { nav.popBackStack() }, onOpenCommands = { navigate("commands") })
    }
}

@Composable
private fun CompatibilityGate(available: Boolean, title: String, reason: String,
    onContinue: () -> Unit, content: @Composable () -> Unit) {
    if (available) content() else ScrollPage {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        InfoCard("RECURSO INDISPONÍVEL NESTE APARELHO", reason)
        Button(onClick = onContinue) { Text("Continuar com as funções disponíveis") }
        Text("Permissões não acrescentam sensores ou suporte nativo ausentes. A compatibilidade é verificada por recurso.", style = MaterialTheme.typography.bodySmall)
    }
}

/** Global tracking keeps its camera until the service has actually released it. */
@Composable
private fun GlobalCameraGate(content: @Composable () -> Unit) {
    val state by GlobalMouseRuntime.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (state.active) ScrollPage {
        InfoCard("CÂMERA EM USO PELO MOUSE GLOBAL", "O mouse global continua ativo durante a navegação. Para usar a câmera nesta função, encerre essa sessão primeiro.")
        Button(onClick = { GlobalMouseRuntime.stop(context) }) { Text("Parar mouse global e usar esta câmera") }
    } else content()
}

@Composable
private fun Splash(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { delay(1_200); onFinished() }
    val background = if (LocalReducedEffects.current) Modifier.background(NexusBackground)
        else Modifier.background(Brush.radialGradient(listOf(Color(0xFF06334A), NexusBackground)))
    Column(Modifier.fillMaxSize().then(background).verticalScroll(rememberScrollState()),
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
    val angle = if (animated && !LocalReducedEffects.current) {
        val transition = rememberInfiniteTransition(label = "NEXUS")
        val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "rotação")
        rotation
    } else 20f
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(NexusCyan.copy(alpha = .07f), r)
        drawCircle(NexusCyan.copy(alpha = .25f), r * .85f, style = Stroke(1.5.dp.toPx()))
        drawCircle(NexusBlue.copy(alpha = .5f), r * .62f, style = Stroke(1.dp.toPx()))
        drawArc(NexusCyan, angle, 100f, false,
            topLeft = Offset(r * .15f, r * .15f), size = androidx.compose.ui.geometry.Size(r * 1.7f, r * 1.7f),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawLine(NexusCyan, Offset(r * .7f, r * 1.28f), Offset(r * .7f, r * .7f), 4.dp.toPx(), StrokeCap.Round)
        drawLine(NexusCyan, Offset(r * .7f, r * .7f), Offset(r * 1.3f, r * 1.28f), 4.dp.toPx(), StrokeCap.Round)
        drawLine(NexusCyan, Offset(r * 1.3f, r * 1.28f), Offset(r * 1.3f, r * .7f), 4.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun Home(model: NexusViewModel, settings: NexusSettings, navigate: (String) -> Unit) {
    val localState by model.localModelState.collectAsStateWithLifecycle()
    val policy by model.devicePolicy.collectAsStateWithLifecycle()
    val capabilities by model.deviceCapabilities.collectAsStateWithLifecycle()
    val effectiveProfile by model.effectiveLocalProfile.collectAsStateWithLifecycle()
    val local = settings.aiMode == AiMode.NEXUS_LOCAL
    val compatibleLocal = policy.localAiSupported && DevicePolicy.canRunProfile(capabilities, effectiveProfile)
    val configured = if (local) compatibleLocal && localState.status == LocalModelStatus.READY else settings.endpoint.isNotBlank() && settings.model.isNotBlank() && settings.remoteConsent
    ScrollPage {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (settings.userName.isBlank()) "Olá. Eu sou o NEXUS." else "Olá, ${settings.userName}.", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text("Um espaço para conversar, explorar e aprender.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        NexusOrb(Modifier.size(76.dp), animated = true)
    }
    InfoCard(if (local) "NEXUS AI · NO APARELHO" else "PROVEDOR PERSONALIZADO",
        if (local && !compatibleLocal) {
            if (!policy.localAiSupported) policy.reason else "O modelo escolhido exige mais memória do que o perfil deste aparelho permite. Ative o ajuste automático ou selecione o modelo compacto em Configurações."
        }
        else if (local && configured) "Modelo local pronto. Suas conversas são processadas neste aparelho, sem servidor ou chave."
        else if (local) "Baixe uma vez o modelo de texto gratuito para conversar sem servidor, conta ou chave. O download precisa de internet e espaço livre."
        else if (configured) "Modelo: ${settings.model}. A disponibilidade será verificada ao enviar uma mensagem."
        else "Configure seu provedor opcional ou selecione NEXUS AI local nas configurações.")
    Button(onClick = { navigate(if (configured) "chat" else "settings") }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(if (configured) "Iniciar conversa" else if (local) "Preparar NEXUS AI" else "Configurar provedor")
    }
    Text("EXPLORE O NEXUS", color = NexusCyan, style = MaterialTheme.typography.labelLarge)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val columns = (maxWidth.value / (160f * fontScale)).toInt().coerceIn(1, 4)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    destinations.chunked(columns).forEach { pair ->
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
            repeat(columns - pair.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    }
    }
    Text("Versão em desenvolvimento. Câmera, áudio e gestos precisam de validação em um aparelho físico.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
            Text("${messages.size} mensagens • contexto limitado", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { model.newConversation() }) { Icon(Icons.Default.Add, null); Text("Nova") }
        }
        val local = settings.aiMode == AiMode.NEXUS_LOCAL
        val localState by model.localModelState.collectAsStateWithLifecycle()
        if (local && localState.status != LocalModelStatus.READY) {
            TextButton(onClick = { navigate("settings") }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Preparar modelo local em Configurações") }
        } else if (!local && (settings.endpoint.isBlank() || !settings.remoteConsent)) {
            TextButton(onClick = { navigate("settings") }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Configurar provedor personalizado") }
        }
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                NexusOrb(Modifier.size(108.dp), animated = busy)
                Spacer(Modifier.height(20.dp)); Text("O que vamos explorar?", fontSize = 23.sp)
                Text("Envie sua pergunta. O modelo local aceita texto; imagens exigem um provedor multimodal personalizado.", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(messages, key = { it.id }) { message ->
                Card(colors = CardDefaults.cardColors(containerColor = if (message.role == "user") Color(0xFF102B3D) else NexusPanel), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (message.role == "user") "VOCÊ" else "NEXUS", color = NexusCyan, style = MaterialTheme.typography.labelSmall)
                            if (message.role == "assistant") IconButton(onClick = { voice.speak(message.content, settings.language, settings.voiceRate, settings.voiceName, settings.voicePitch) }, modifier = Modifier.size(40.dp)) {
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
            IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy && !importing && settings.aiMode == AiMode.CUSTOM) { Icon(Icons.Default.AddPhotoAlternate, "Anexar imagem") }
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
                voice.speak(reply.content, settings.language, settings.voiceRate, settings.voiceName, settings.voicePitch)
            }
        }
    }
    LaunchedEffect(responseError) {
        if (responseError != null && awaitingQuery != null) {
            awaitingQuery = null
            continuous = false
        }
    }
    LaunchedEffect(continuous, busy, importing, awaitingQuery, state.isListening, state.isSpeaking, state.error, state.ttsReady, state.languageReady, settings.language) {
        if (voice.state.value.error != null) continuous = false
        if (continuous && !busy && !importing && awaitingQuery == null && !state.isListening && !state.isSpeaking && state.ttsReady && state.languageReady) {
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
        if (state.ttsReady && !state.languageReady) VoiceLanguageHelp(settings.language)
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
    val capabilities by model.deviceCapabilities.collectAsStateWithLifecycle()
    // Camera activation is intentionally not restored after recreation or backgrounding.
    var cameraActive by remember { mutableStateOf(false) }
    var front by rememberSaveable { mutableStateOf(!capabilities.hasRearCamera && capabilities.hasFrontCamera) }
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
        if (settings.aiMode == AiMode.NEXUS_LOCAL) {
            InfoCard("MODELO LOCAL DE TEXTO", "Este modelo não analisa imagens. Para usar Vision, selecione e autorize um provedor multimodal personalizado em Configurações.")
            OutlinedButton(onClick = { navigate("settings") }) { Text("Configurar IA para imagens") }
        }
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
                if (capabilities.hasFrontCamera && capabilities.hasRearCamera) OutlinedButton(onClick = { front = !front }) { Text("Alternar câmera") }
                TextButton(onClick = { cameraActive = false }) { Text("Encerrar câmera") }
            }
        } else Button(onClick = { cameraActive = true }, enabled = !busy && !importing) { Icon(Icons.Default.CameraAlt, null); Text(" Abrir câmera") }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        attachment?.let { LocalImage(it, Modifier.fillMaxWidth().heightIn(max = 260.dp)); TextButton(onClick = model::removeAttachment) { Text("Descartar foto") } }
        OutlinedTextField(question, { question = it }, label = { Text("Pergunta sobre a captura") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Button(onClick = { if (model.send(question)) navigate("chat") }, enabled = settings.aiMode == AiMode.CUSTOM && attachment != null && !busy && !importing && !cameraActive) { Text("Analisar com a IA") }
        if (settings.aiMode == AiMode.CUSTOM && !settings.remoteConsent) Text("Autorize o envio para o servidor em Configurações antes de analisar.", color = MaterialTheme.colorScheme.error)
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
    var mode by remember(settings.aiMode) { mutableStateOf(settings.aiMode) }
    var endpoint by rememberSaveable(settings.endpoint) { mutableStateOf(settings.endpoint) }
    var aiModel by rememberSaveable(settings.model) { mutableStateOf(settings.model) }
    var token by remember { mutableStateOf("") }
    var replaceToken by remember { mutableStateOf(false) }
    var consent by rememberSaveable(settings.remoteConsent) { mutableStateOf(settings.remoteConsent) }
    var rate by remember(settings.voiceRate) { mutableFloatStateOf(settings.voiceRate) }
    var pitch by remember(settings.voicePitch) { mutableFloatStateOf(settings.voicePitch) }
    var autoSpeak by remember(settings.autoSpeak) { mutableStateOf(settings.autoSpeak) }
    var voiceName by rememberSaveable(settings.voiceName) { mutableStateOf(settings.voiceName) }
    val voices by voice.voices.collectAsStateWithLifecycle()
    val voiceState by voice.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ScrollPage {
        Text("Inteligência artificial", fontSize = 24.sp)
        ChoiceField("Modo de IA", if (mode == AiMode.NEXUS_LOCAL) "NEXUS AI · local" else "Provedor personalizado",
            listOf("NEXUS AI · local", "Provedor personalizado")) {
            mode = if (it == "NEXUS AI · local") AiMode.NEXUS_LOCAL else AiMode.CUSTOM
        }
        if (mode == AiMode.NEXUS_LOCAL) {
            LocalModelCard(model, settings, onUseCustom = {
                mode = AiMode.CUSTOM
                model.saveSettings(settings.copy(aiMode = AiMode.CUSTOM))
            })
        } else {
            InfoCard("PROVEDOR OPCIONAL", "Use seu servidor NEXUS com Ollama ou outro serviço compatível. Configure apenas se desejar. Chaves privadas de serviços pagos devem ficar no servidor.")
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
            Text("Ao enviar neste modo, o servidor recebe sua mensagem, preferências, memórias autorizadas, até 24 mensagens e até 2 imagens recentes. O serviço de fala do Android tem sua própria política.", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Text("Voz · ${settings.language}", fontSize = 22.sp)
        Text("Velocidade: ${"%.1f".format(rate)}×")
        Slider(rate, { rate = it }, valueRange = .5f..2f)
        Text("Tom: ${"%.1f".format(pitch)}×")
        Slider(pitch, { pitch = it }, valueRange = .5f..2f)
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = voices.isNotEmpty()) {
                Text(voices.find { it.name == voiceName }?.label ?: "Automática · ${settings.language}")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Automática · priorizar voz local") }, onClick = { voiceName = ""; expanded = false })
                voices.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label + if (option.requiresNetwork) " • rede" else " • local") }, onClick = { voiceName = option.name; expanded = false })
                }
            }
        }
        Text("A lista mostra somente vozes do idioma escolhido em Personalidade. Vozes de rede podem enviar texto ao mecanismo de fala do Android.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { voice.speak("Olá! Eu sou o NEXUS, seu assistente pessoal.", settings.language, rate, voiceName, pitch) }, enabled = voiceState.ttsReady && voices.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Testar voz") }
            OutlinedButton(onClick = { voice.stopSpeaking() }, enabled = voiceState.isSpeaking, modifier = Modifier.weight(1f)) { Text("Parar leitura") }
        }
        ToggleRow("Ler novas respostas automaticamente", autoSpeak, { autoSpeak = it })
        Text("A leitura automática acontece somente com o app aberto. O assistente por voz lê a resposta à sua fala mesmo com esta opção desligada.", style = MaterialTheme.typography.bodySmall)
        voiceState.error?.let { InfoCard("VOZ DO ANDROID", it) }
        VoiceLanguageHelp(settings.language, missing = voiceState.ttsReady && voices.isEmpty())
        Button(onClick = {
            val submittedToken = token
            model.saveSettings(settings.copy(aiMode = mode, endpoint = endpoint.trim(), model = aiModel.trim(), remoteConsent = consent,
                voiceRate = rate, voicePitch = pitch, voiceName = voiceName.takeIf { name -> voices.any { it.name == name } }.orEmpty(), autoSpeak = autoSpeak), if (replaceToken) token else null) {
                if (token == submittedToken) { token = ""; replaceToken = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Salvar configurações") }
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Gerenciar permissões do Android") }
        InfoCard("PRIVACIDADE", "O modo NEXUS AI processa texto no aparelho. A câmera e o microfone são solicitados nas suas funções. Conversas ficam no armazenamento privado do app. O token opcional é protegido pelo Android Keystore. Backup automático está desativado.")
        Text("Tema escuro padrão • interface adaptável • versão ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    }
    DisposableEffect(Unit) { onDispose { voice.stopSpeaking() } }
}

@Composable
private fun LocalModelCard(model: NexusViewModel, settings: NexusSettings, onUseCustom: () -> Unit) {
    val state by model.localModelState.collectAsStateWithLifecycle()
    val spec by model.localModelSpec.collectAsStateWithLifecycle()
    val capabilities by model.deviceCapabilities.collectAsStateWithLifecycle()
    val policy by model.devicePolicy.collectAsStateWithLifecycle()
    val effectiveProfile by model.effectiveLocalProfile.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val selecting = spec != LocalModelCatalog.forProfile(effectiveProfile) || state.totalBytes != spec.bytes
    val preparing = selecting || state.status in setOf(LocalModelStatus.CHECKING, LocalModelStatus.DOWNLOADING, LocalModelStatus.VERIFYING)
    val compatible = policy.localAiSupported && DevicePolicy.canRunProfile(capabilities, effectiveProfile)
    var confirmDownload by remember(spec.fileName) { mutableStateOf(false) }
    var confirmDelete by remember(spec.fileName) { mutableStateOf(false) }
    val compactLabel = "0.5B · básico, arquivo menor"
    val balancedLabel = "1.5B · arquivo maior, mais RAM"
    ToggleRow("Ajustar automaticamente ao aparelho", settings.automaticProfile,
        { model.saveSettings(settings.copy(automaticProfile = it)) }, enabled = !preparing && !busy)
    Text(policy.reason, style = MaterialTheme.typography.bodySmall)
    Text("Processo ${capabilities.processAbi.ifBlank { "não informado" }} · RAM total ${capabilities.totalRamBytes / 1_048_576} MiB · disponível na inicialização ${capabilities.availableRamBytes / 1_048_576} MiB.", style = MaterialTheme.typography.bodySmall)
    if (settings.automaticProfile) {
        Text("Modelo ajustado ao aparelho: ${spec.displayName}", color = NexusCyan)
        Text("O ajuste escolhe um perfil, mas não baixa arquivos nem usa um servidor automaticamente. A RAM livre pode mudar durante o uso.", style = MaterialTheme.typography.bodySmall)
    } else if (preparing || busy) {
        Text("Modelo selecionado: ${spec.displayName}")
        Text("Interrompa a resposta ou aguarde a preparação terminar para trocar de modelo.", style = MaterialTheme.typography.bodySmall)
    } else ChoiceField("Escolha o modelo local", if (effectiveProfile == LocalModelProfile.COMPACT) compactLabel else balancedLabel,
        listOf(compactLabel, balancedLabel)) { label ->
        val profile = if (label == compactLabel) LocalModelProfile.COMPACT else LocalModelProfile.BALANCED
        if (profile != settings.localModelProfile) model.saveSettings(settings.copy(localModelProfile = profile))
    }
    InfoCard("NEXUS AI · LOCAL", "Modelo de texto ${spec.displayName}, de terceiros, executado com llama.cpp. Sem conta, servidor ou chave. Não interpreta imagens. A escolha do modelo é salva ao selecionar.")
    Text("Download: aproximadamente ${modelSize(spec.bytes)}. Reserve espaço adicional no armazenamento. A inferência também usa RAM; o perfil de 1.5B exige mais memória. Velocidade e qualidade variam e precisam ser verificadas neste aparelho.", style = MaterialTheme.typography.bodySmall)
    Text("O perfil de 0.5B tem capacidade limitada, especialmente em português. Ambos podem errar. Depois do download verificado, o chat de texto funciona sem internet.", style = MaterialTheme.typography.bodySmall)
    if (!compatible) {
        InfoCard("IA LOCAL INDISPONÍVEL NESTE PERFIL", if (!policy.localAiSupported) policy.reason else "O modelo escolhido exige mais RAM do que este aparelho oferece com a margem de segurança. Ative o ajuste automático ou selecione o perfil compacto.")
        OutlinedButton(onClick = onUseCustom, enabled = !busy) { Text("Usar provedor personalizado opcional") }
        Text("O provedor exige um servidor real e sua autorização. O NEXUS não enviará mensagens automaticamente.", style = MaterialTheme.typography.bodySmall)
    }
    Text(if (selecting) "Preparando modelo selecionado…" else when (state.status) {
        LocalModelStatus.CHECKING -> "Verificando modelo instalado…"
        LocalModelStatus.ABSENT -> "Modelo ainda não instalado"
        LocalModelStatus.DOWNLOADING -> "Baixando: ${modelSize(state.downloadedBytes)} / ${modelSize(state.totalBytes)}"
        LocalModelStatus.VERIFYING -> "Verificando integridade do arquivo…"
        LocalModelStatus.READY -> "Modelo local verificado"
        LocalModelStatus.FAILED -> "Não foi possível preparar o modelo"
    }, color = NexusCyan)
    if (!selecting) state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (selecting && compatible) LinearProgressIndicator(Modifier.fillMaxWidth())
    else when (state.status) {
        LocalModelStatus.DOWNLOADING -> {
            LinearProgressIndicator(progress = { if (state.totalBytes > 0) (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = model::cancelLocalModelDownload) { Text("Cancelar download") }
        }
        LocalModelStatus.CHECKING, LocalModelStatus.VERIFYING -> if (compatible) LinearProgressIndicator(Modifier.fillMaxWidth())
        LocalModelStatus.READY -> OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Excluir este modelo baixado") }
        else -> Button(onClick = { confirmDownload = true }, enabled = !busy && compatible && !selecting) { Text("Baixar este modelo gratuito") }
    }
    if (confirmDownload && !selecting && compatible) AlertDialog(onDismissRequest = { confirmDownload = false },
        title = { Text("Baixar ${spec.displayName}?") },
        text = { Text("O NEXUS baixará aproximadamente ${modelSize(spec.bytes)} do Hugging Face (${Uri.parse(spec.downloadUrl).host}), que receberá uma conexão de download. Suas conversas não serão enviadas. Prefira Wi-Fi e mantenha o app aberto. O arquivo será verificado antes do uso. Memória e desempenho dependem do aparelho.") },
        confirmButton = { TextButton(onClick = { confirmDownload = false; model.downloadLocalModel() }) { Text("Autorizar download") } },
        dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Cancelar") } })
    if (confirmDelete && !selecting) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Excluir ${spec.displayName}?") }, text = { Text("Suas conversas serão preservadas. Para usar este modelo novamente, será necessário baixá-lo outra vez.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; model.deleteLocalModel() }) { Text("Excluir modelo") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

private fun modelSize(bytes: Long): String = if (bytes >= 1_073_741_824L) {
    String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f GiB", bytes.toDouble() / 1_073_741_824)
} else "${bytes / 1_048_576} MiB"

@Composable
private fun VoiceLanguageHelp(language: String, missing: Boolean = true) {
    val context = LocalContext.current
    var launchError by remember { mutableStateOf<String?>(null) }
    if (missing) InfoCard("VOZ DO IDIOMA AUSENTE", "Não há voz utilizável para $language. No Android, abra Texto para fala, escolha um mecanismo e instale os dados desse idioma. Para português brasileiro, selecione Português (Brasil). Depois reabra o NEXUS.")
    OutlinedButton(onClick = {
        val install = Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        val settings = Intent("com.android.settings.TTS_SETTINGS")
        launchError = runCatching { context.startActivity(install) }.fold(
            onSuccess = { null },
            onFailure = { runCatching { context.startActivity(settings) }.fold(onSuccess = { null }, onFailure = { "Abra Configurações do Android → Idioma → Texto para fala para instalar ou escolher uma voz." }) }
        )
    }) { Text("Instalar ou gerenciar vozes do Android") }
    launchError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun MemoryScreen(model: NexusViewModel, openChat: () -> Unit, onBeforeDeleteAll: () -> Unit) {
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
        text = { Text("Conversas, imagens anexadas, memórias, token, preferências, progresso de treino e o modelo de IA baixado serão removidos deste aparelho. As sessões de voz e mouse serão encerradas. A exclusão não pode ser desfeita.") },
        confirmButton = { TextButton(onClick = { confirmDeleteAll = false; onBeforeDeleteAll(); model.deleteAllData() }) { Text("Apagar") } },
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
        InfoCard("CONTROLE DO ANDROID", "O mouse interno atua dentro do NEXUS. O controle global experimental é ativado separadamente na tela de mouse, com consentimento, acessibilidade e câmera visível. Bloqueio de tela não está implementado. A disponibilidade depende do aparelho e das proteções do Android.")
    }
    if (chooseApp) AlertDialog(onDismissRequest = { chooseApp = false }, title = { Text("Aplicativos disponíveis") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            if (apps.isEmpty()) item { Text("Nenhum aplicativo com atalho disponível.") }
            items(apps, key = { it.first }) { app -> TextButton(onClick = { packageName = app.first; label = app.second; chooseApp = false }) { Text(app.second) } }
        }
    }, confirmButton = { TextButton(onClick = { chooseApp = false }) { Text("Fechar") } })
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
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp)); Switch(checked, onCheckedChange, enabled = enabled)
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
