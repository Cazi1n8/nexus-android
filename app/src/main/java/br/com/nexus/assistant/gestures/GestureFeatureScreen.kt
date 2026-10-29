package br.com.nexus.assistant.gestures

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import br.com.nexus.assistant.commands.PhoneCommands
import br.com.nexus.assistant.device.DeviceCapabilities
import br.com.nexus.assistant.device.DevicePolicy
import br.com.nexus.assistant.device.TrackingProfile
import br.com.nexus.assistant.gestures.global.GlobalMousePanel
import br.com.nexus.assistant.gestures.global.GlobalMouseRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GestureMode { MOUSE, HANDS, CUBE }

/** The three destinations have separate controllers and separate state. */
@Composable
fun GestureFeatureScreen(mode: GestureMode, onNavigateBack: () -> Unit = {},
    onOpenCommands: () -> Unit = {}, session: GestureSession? = null) {
    when (mode) {
        GestureMode.MOUSE -> {
            val current = session ?: rememberGestureSession()
            Box(Modifier.fillMaxSize()) {
                MouseScreen(current)
                if (session == null) GestureSessionHost(current)
            }
        }
        GestureMode.HANDS -> HandsScreen(onNavigateBack, onOpenCommands)
        GestureMode.CUBE -> CubeScreen()
    }
}

@Composable
private fun GestureColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) { content(); Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun MouseScreen(session: GestureSession) {
    val context = LocalContext.current
    val capabilities = remember(context) { DeviceCapabilities.read(context) }
    val policy = remember(capabilities) { DevicePolicy.evaluate(capabilities) }
    val global by GlobalMouseRuntime.state.collectAsState()
    var testCount by remember { mutableIntStateOf(0) }
    fun startInternalMouse() {
        if (policy.handTrackingSupported && !GlobalMouseRuntime.state.value.active) session.startMouse()
    }
    val internalCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startInternalMouse()
        else session.fail("Autorize a câmera ao ativar o mouse. Nenhuma imagem é gravada ou enviada pelo rastreador.")
    }
    GestureColumn {
        Text("Mouse Virtual", style = MaterialTheme.typography.titleLarge)
        Text("Para controlar outros aplicativos, ative a sessão global abaixo. O modo interno fica disponível separadamente para usar somente no NEXUS.")
        Text("Perfil de rastreamento", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrackingProfile.entries.forEach { profile ->
                FilterChip(selected = session.trackingProfile == profile,
                    onClick = { session.updateTrackingProfile(profile) },
                    enabled = !session.mouseActive && !global.active,
                    label = { Text(profile.label) })
            }
        }
        Text("O perfil adapta resolução e frequência de análise. Altere com os dois mouses parados; a câmera pode negociar outra resolução conforme o Android.", style = MaterialTheme.typography.bodySmall)
        GlobalMousePanel(onBeforeStart = session::stopMouse)
        HorizontalDivider()
        Text("Mouse interno · somente no NEXUS", style = MaterialTheme.typography.titleLarge)
        Text("Um cursor para tocar nos botões reais do aplicativo. Permanece ativo ao trocar de tela e para quando você sai do NEXUS.")
        Text("Indicador: mover. Pinça curta: tocar. Duas pinças: dois toques. Segure a pinça por 0,45 s: arrastar; solte para liberar. Dois dedos estendidos: toque e mova a mão para rolar a lista sob o cursor.")
        Button(onClick = {
            if (session.mouseActive) session.stopMouse()
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startInternalMouse()
            else internalCameraPermission.launch(Manifest.permission.CAMERA)
        }, enabled = session.mouseActive || !global.active && policy.handTrackingSupported) {
            Text(if (session.mouseActive) "PARAR mouse" else "Ativar mouse interno")
        }
        if (global.active) Text("Pare o mouse global antes de ativar o mouse interno.")
        if (!policy.handTrackingSupported) Text("O mouse por câmera não está disponível neste aparelho. Confira câmera e arquitetura na Central de diagnóstico.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = session::switchCamera, enabled = capabilities.hasFrontCamera && capabilities.hasRearCamera) { Text(if (session.frontCamera) "Usar traseira" else "Usar frontal") }
            OutlinedButton(onClick = session::calibrate, enabled = session.frame.hands.isNotEmpty()) { Text("Calibrar centro") }
        }
        session.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(if (session.mouseActive) "Câmera interna ativa · processamento no aparelho" else "Câmera interna desligada")
        Text("Sensibilidade")
        Slider(session.sensitivity, session::updateSensitivity, valueRange = .6f..2.3f)
        Text("Limiar de pinça")
        Slider(session.pinchThreshold, session::updatePinchThreshold, valueRange = .2f.. .55f)
        Row {
            Checkbox(checked = session.showPreview, onCheckedChange = session::updateShowPreview)
            Text("Mostrar prévia da câmera", modifier = Modifier.padding(top = 12.dp))
        }
        Text("Ocultar a prévia mantém o rastreamento ativo e os indicadores de privacidade do Android.", style = MaterialTheme.typography.bodySmall)
        Text("Toques enviados: ${session.clicks} · duplos: ${session.doubleClicks}")
        Button(onClick = { testCount++ }, modifier = Modifier.fillMaxWidth()) { Text("Botão real de teste: $testCount") }
        Text("O toque mantido é cancelado se a mão desaparecer. Use PARAR em caso de dificuldade. O painel de controle mantém o botão PARAR visível; a imagem da câmera é opcional.", style = MaterialTheme.typography.bodySmall)
        Text("Precisão e atraso dependem da iluminação e do aparelho. Toques e câmera ainda precisam de teste físico no dispositivo utilizado.", style = MaterialTheme.typography.bodySmall)
        repeat(8) { Card(Modifier.fillMaxWidth()) { Text("Área de rolagem · item ${it + 1}", Modifier.padding(18.dp)) } }
    }
}

@Composable
private fun HandsScreen(onNavigateBack: () -> Unit, onOpenCommands: () -> Unit) {
    val context = LocalContext.current
    val capabilities = remember(context) { DeviceCapabilities.read(context) }
    val policy = remember(capabilities) { DevicePolicy.evaluate(capabilities) }
    val prefs = remember { context.getSharedPreferences("nexus_hands_free", Context.MODE_PRIVATE) }
    val controller = remember { HandsFreeController() }
    val previewPrefs = remember { context.getSharedPreferences("nexus_gestures", Context.MODE_PRIVATE) }
    var showPreview by remember { mutableStateOf(previewPrefs.getBoolean("hands_show_preview", false)) }
    var active by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(capabilities.hasFrontCamera) }
    var frame by remember { mutableStateOf(HandFrame()) }
    var feedback by remember { mutableStateOf("Escolha as ações antes de iniciar a câmera.") }
    var error by remember { mutableStateOf<String?>(null) }
    var mapping by remember { mutableStateOf(GestureTrigger.entries.associateWith { prefs.getString(it.name, "none") ?: "none" }) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val global by GlobalMouseRuntime.state.collectAsState()
    LaunchedEffect(context) { apps = withContext(Dispatchers.IO) { PhoneCommands.installedApps(context) } }
    fun startCamera() {
        if (!policy.handTrackingSupported || GlobalMouseRuntime.state.value.active) return
        active = true; frame = HandFrame(); controller.reset(); error = null
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
        else error = "Autorize a câmera para reconhecer gestos. As associações de ações continuam disponíveis sem ela."
    }
    StopOnBackground { frame = HandFrame(); controller.reset() }
    fun execute(trigger: GestureTrigger) {
        when (val action = mapping[trigger] ?: "none") {
            "none" -> feedback = "${trigger.label}: nenhuma ação configurada."
            "back" -> { active = false; onNavigateBack() }
            "commands" -> { active = false; onOpenCommands() }
            else -> {
                val packageName = action.removePrefix("app:")
                if (action.startsWith("app:") && apps.any { it.first == packageName }) {
                    // CameraX pauses with the activity; keep the explicit session choice
                    // so returning to NEXUS resumes without reconfiguring the mode.
                    controller.reset()
                    feedback = PhoneCommands.execute(context, action) ?: "Aplicativo aberto por ${trigger.label.lowercase()}."
                } else feedback = "Aplicativo indisponível. Escolha novamente a ação."
            }
        }
    }
    GestureColumn {
        Text("Mãos livres · ações autorizadas", style = MaterialTheme.typography.titleLarge)
        Text("Configurações independentes do mouse e do cubo. Mantenha a pose por 0,42 s e mude de postura antes de repetir. Para gesto lateral, desloque a mão aberta para o lado.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (active) { active = false; frame = HandFrame(); controller.reset() }
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            }, enabled = active || !global.active && policy.handTrackingSupported) { Text(if (active) "Parar câmera" else "Iniciar câmera") }
            OutlinedButton(onClick = { front = !front; frame = HandFrame(); controller.reset() }, enabled = capabilities.hasFrontCamera && capabilities.hasRearCamera) { Text("Trocar câmera") }
        }
        if (!policy.handTrackingSupported) Text("Os comandos por câmera não estão disponíveis neste aparelho. As associações podem ser configuradas para uso em um dispositivo compatível.")
        Row {
            Checkbox(showPreview, { showPreview = it; previewPrefs.edit().putBoolean("hands_show_preview", it).apply() })
            Text("Mostrar prévia da câmera", modifier = Modifier.padding(top = 12.dp))
        }
        CameraBox(active, front, frame, showPreview = showPreview, onFrame = {
            frame = it
            val result = controller.update(it, .35f)
            feedback = "${it.hands.size} mão(s) · ${result.configuration.label} · ${it.processingMs} ms"
            result.trigger?.let(::execute)
        }, onError = { error = it; active = false })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(feedback)
        GestureTrigger.entries.forEach { trigger ->
            var expanded by remember(trigger) { mutableStateOf(false) }
            val current = mapping[trigger] ?: "none"
            val choices = listOf("none" to "Nenhuma ação", "back" to "Voltar no NEXUS", "commands" to "Abrir comandos") +
                apps.map { "app:${it.first}" to "Abrir ${it.second}" }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(trigger.label, style = MaterialTheme.typography.titleSmall)
                    Box {
                        OutlinedButton(onClick = { expanded = true }) { Text(choices.firstOrNull { it.first == current }?.second ?: "Aplicativo indisponível") }
                        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                            choices.forEach { (action, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    mapping = mapping + (trigger to action); prefs.edit().putString(trigger.name, action).apply()
                                    controller.reset(); expanded = false
                                })
                            }
                        }
                    }
                }
            }
        }
        Text("Reconhecimento geométrico experimental; não reconhece Libras. A câmera pausa fora desta tela. Volte ao NEXUS para continuar; controle de mãos livres fora do app ainda não está disponível.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CubeScreen() {
    val context = LocalContext.current
    val capabilities = remember(context) { DeviceCapabilities.read(context) }
    val policy = remember(capabilities) { DevicePolicy.evaluate(capabilities) }
    if (!policy.supportsGles2) {
        GestureColumn {
            Text("NEXUS 3D", style = MaterialTheme.typography.titleLarge)
            Text("O Android não informou suporte a OpenGL ES 2.0 neste aparelho. O ambiente 3D foi desativado para evitar falhas; as outras funções do NEXUS continuam disponíveis.")
        }
        return
    }
    val controller = remember { SceneController() }
    val store = remember(context) { SceneStore(context) }
    val scope = rememberCoroutineScope()
    val previewPrefs = remember { context.getSharedPreferences("nexus_gestures", Context.MODE_PRIVATE) }
    var showPreview by remember { mutableStateOf(previewPrefs.getBoolean("cube_show_preview", false)) }
    var scene by remember { mutableStateOf(controller.state) }
    var active by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(capabilities.hasFrontCamera) }
    var rotate by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf(HandFrame()) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val global by GlobalMouseRuntime.state.collectAsState()
    fun edit(change: () -> SceneState) {
        try { scene = change(); error = null; notice = null }
        catch (failure: IllegalArgumentException) { error = failure.message ?: "Não foi possível alterar a cena." }
    }
    fun stopCamera() { active = false; frame = HandFrame(); scene = controller.release() }
    fun startCamera() {
        if (!policy.handTrackingSupported || GlobalMouseRuntime.state.value.active) return
        active = true; frame = HandFrame(); scene = controller.release(); error = null
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
        else error = "A câmera não foi autorizada. Os controles manuais da cena continuam disponíveis."
    }
    StopOnBackground { frame = HandFrame(); scene = controller.release() }
    GestureColumn {
        Text("NEXUS 3D · OpenGL ES", style = MaterialTheme.typography.titleLarge)
        Text("Comece uma pinça sobre um objeto para segurá-lo. Solte para liberar. Uma mão move ou gira. Duas pinças sobre o mesmo objeto ampliam, reduzem, giram e deslocam. Perder a mão solta o objeto.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (active) stopCamera()
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            }, enabled = !saving && (active || !global.active && policy.handTrackingSupported)) {
                Text(if (active) "Parar câmera" else "Iniciar câmera")
            }
            OutlinedButton(onClick = { front = !front; frame = HandFrame(); scene = controller.release() },
                enabled = !saving && capabilities.hasFrontCamera && capabilities.hasRearCamera) { Text("Trocar câmera") }
        }
        if (!policy.handTrackingSupported) Text("Rastreamento de mãos indisponível neste aparelho. A exploração manual da cena continua disponível.")
        else Text("Duas mãos habilitadas: o rastreador reduz resolução e frequência em perfis econômicos. A viabilidade e o atraso precisam ser medidos no aparelho; use uma mão se necessário.", style = MaterialTheme.typography.bodySmall)
        if (global.active) Text("O mouse global está usando a câmera. Pare essa sessão antes de iniciar o rastreamento 3D; o controle manual continua disponível.")
        Row {
            Checkbox(showPreview, { showPreview = it; previewPrefs.edit().putBoolean("cube_show_preview", it).apply() })
            Text("Mostrar prévia da câmera", modifier = Modifier.padding(top = 12.dp))
        }
        Text("A câmera pausa ao sair do NEXUS e retoma ao voltar. PARAR encerra a sessão.", style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().height(340.dp).background(Color.Black).border(1.dp, Color(0xFF31E7FA))
            .onSizeChanged { controller.aspect = it.width.toFloat() / it.height.coerceAtLeast(1) }) {
            if (active) HandCamera(front, Modifier.fillMaxSize(), onFrame = {
                frame = it; scene = controller.update(it, rotate, .35f)
            }, onError = { message -> stopCamera(); error = message }, requestedHands = 2, showPreview = showPreview)
            HandSkeleton(frame, Modifier.fillMaxSize())
            SceneView(scene, Modifier.fillMaxSize(), onRenderError = { message -> stopCamera(); error = message })
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        val selected = scene.selectedObject
        Text(when {
            selected?.transform?.bimanual == true -> "${selected.shape.label} · segurado com duas mãos"
            selected?.transform?.selected == true -> "${selected.shape.label} · ${if (rotate) "girando" else "movendo"}"
            selected != null -> "${selected.shape.label} selecionado · comece uma nova pinça sobre ele"
            else -> "Cena vazia · crie um objeto abaixo"
        })
        Text(if (active) "Câmera ativa · ${frame.hands.size} mão(s) · ${frame.processingMs} ms" else "Câmera desligada · controle manual disponível")
        Text("Objetos: ${scene.objects.size}/${SceneState.MAX_OBJECTS}", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SceneShape.entries.forEach { shape ->
                OutlinedButton(onClick = { edit { controller.create(shape) } },
                    enabled = !saving && scene.objects.size < SceneState.MAX_OBJECTS) { Text("+ ${shape.label}") }
            }
        }
        if (scene.objects.isNotEmpty()) {
            Text("Selecionar objeto")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scene.objects.forEachIndexed { index, item ->
                    FilterChip(selected = selected?.id == item.id, onClick = { edit { controller.select(item.id) } },
                        enabled = !saving, label = { Text("${index + 1} · ${item.shape.label}") })
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { edit { controller.duplicateSelected() } },
                enabled = !saving && selected != null && scene.objects.size < SceneState.MAX_OBJECTS) { Text("Duplicar") }
            OutlinedButton(onClick = { edit { controller.deleteSelected() } }, enabled = !saving && selected != null) { Text("Excluir objeto") }
            OutlinedButton(onClick = { edit { controller.release() } }, enabled = !saving && selected?.transform?.selected == true) { Text("Soltar objeto") }
        }
        if (selected != null) {
            Text("Cor do objeto")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Ciano" to 0xFF31E7FA.toInt(), "Azul" to 0xFF548BFF.toInt(), "Rosa" to 0xFFFF69CD.toInt(),
                    "Verde" to 0xFF70E995.toInt(), "Dourado" to 0xFFFFC75B.toInt()).forEach { (label, color) ->
                    FilterChip(selected = selected.colorArgb == color, onClick = { edit { controller.setSelectedColor(color) } },
                        enabled = !saving, label = { Text(label) })
                }
            }
            Text("Rotação manual")
            Slider(selected.transform.yaw.coerceIn(-3.14f, 3.14f), { edit { controller.setManualYaw(it) } },
                enabled = !saving, valueRange = -3.14f..3.14f)
            Text("Tamanho manual")
            Slider(selected.transform.scale, { edit { controller.setManualScale(it) } }, enabled = !saving, valueRange = .1f.. .65f)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { rotate = !rotate; scene = controller.release() }, enabled = !saving) {
                Text(if (rotate) "Modo: girar" else "Modo: mover")
            }
            OutlinedButton(onClick = { edit { controller.reset() } }, enabled = !saving) { Text("Reiniciar cena") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val snapshot = controller.release().also { scene = it }
                saving = true; error = null; notice = null
                scope.launch {
                    try { store.save(snapshot); notice = "Cena salva somente neste aparelho." }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Não foi possível salvar a cena." }
                    finally { saving = false }
                }
            }, enabled = !saving) { Text("Salvar cena") }
            OutlinedButton(onClick = {
                stopCamera(); saving = true; error = null; notice = null
                scope.launch {
                    try {
                        val saved = store.load()
                        if (saved == null) notice = "Nenhuma cena foi salva ainda."
                        else { scene = controller.restore(saved); notice = "Cena local aberta. Inicie a câmera quando desejar." }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Não foi possível abrir a cena." }
                    finally { saving = false }
                }
            }, enabled = !saving) { Text("Abrir cena") }
        }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Renderização 3D real sobre a câmera. Sem posicionamento espacial de realidade aumentada. Até 12 objetos; uma seleção por vez. O controle 3D não gera toques de mouse. Salvar mantém um arquivo privado da cena, sem imagens da câmera.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CameraBox(active: Boolean, front: Boolean, frame: HandFrame, showPreview: Boolean = true, onFrame: (HandFrame) -> Unit, onError: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().height(300.dp).background(Color.Black).border(1.dp, Color(0xFF31E7FA))) {
        if (active) HandCamera(front, Modifier.fillMaxSize(), onFrame, onError, showPreview = showPreview)
        HandSkeleton(frame, Modifier.fillMaxSize())
    }
}

@Composable
private fun StopOnBackground(onStop: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest = rememberUpdatedState(onStop)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) latest.value() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); latest.value() }
    }
}
