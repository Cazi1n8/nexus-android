package br.com.nexus.assistant.libras

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.nexus.assistant.gestures.HandCamera
import br.com.nexus.assistant.gestures.HandFrame
import br.com.nexus.assistant.gestures.HandPoint
import br.com.nexus.assistant.device.DeviceCapabilities
import br.com.nexus.assistant.device.DevicePolicy
import br.com.nexus.assistant.ui.NexusCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import kotlin.math.min

/** Original preparation exercises; not a sign-language translation or certification tool. */
@Composable
fun NexusLibrasScreen() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val capabilities = remember(context) { DeviceCapabilities.read(context) }
    val policy = remember(capabilities) { DevicePolicy.evaluate(capabilities) }
    val hasCamera = capabilities.hasFrontCamera || capabilities.hasRearCamera
    val canSwitchCamera = capabilities.hasFrontCamera && capabilities.hasRearCamera
    val repository = remember(context) { LibrasProgressRepository(context) }
    val progress by repository.progress.collectAsStateWithLifecycle(initialValue = LibrasProgress())
    var selected by remember { mutableStateOf(PreparationExercise.EXTENDED) }
    var running by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(capabilities.hasFrontCamera) }
    var frame by remember { mutableStateOf(HandFrame()) }
    var completedThisSession by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("Escolha um exercício e leia a orientação antes de ligar a câmera.") }
    var deleteDialog by remember { mutableStateOf(false) }
    val gate = remember { PracticeHoldGate() }
    val modelAvailable = remember(context) {
        runCatching { context.assets.open("hand_landmarker.task").use { it.read() >= 0 } }.getOrDefault(false)
    }

    fun stopCamera() { running = false; frame = HandFrame(); gate.reset() }

    fun startCamera() {
        gate.reset(); completedThisSession = false; frame = HandFrame(); running = true
        feedback = "Câmera ativa • mostre uma mão de frente e mantenha a posição por pelo menos 1,2 segundo."
        scope.launch { runCatching { repository.recordSession() }.onFailure { feedback = "O treino pode continuar, mas não consegui salvar o início da sessão." } }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else feedback = "A câmera não foi autorizada. Você pode estudar as referências sem ela e autorizar depois nas configurações do Android."
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val snapshot = progress
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val json = JSONObject().put("module", "NEXUS Libras · preparação")
                        .put("notASignLanguageAssessment", true)
                        .put("completedExercises", JSONArray(snapshot.completed.sorted()))
                        .put("sessionsStarted", snapshot.sessions).put("stableHolds", snapshot.completions)
                        .put("lastCompletedAt", snapshot.lastCompletedAt).toString(2)
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Destino indisponível")
                }.isSuccess
            }
            feedback = if (success) "Progresso exportado para o arquivo escolhido." else "Não consegui exportar. Escolha outro destino e tente novamente."
        }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stopCamera() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); gate.reset() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("NEXUS Libras · preparação", style = MaterialTheme.typography.titleLarge)
        Text("Comece pela percepção e pelo controle dos dedos. São dois exercícios geométricos de nível inicial; não são sinais completos de Libras.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Antes de praticar", style = MaterialTheme.typography.titleMedium)
                Text("Libras envolve configuração de mãos, orientação, localização, movimento, expressões não manuais e gramática. Este módulo mede somente parte da configuração de quatro dedos. Não avalia significado, orientação correta de um sinal, fluência ou pronúncia em Libras.")
                Text("Os exercícios e esquemas são próprios. O algoritmo ainda precisa de validação com pessoas sinalizantes e educadores; o resultado não é uma correção linguística.", style = MaterialTheme.typography.bodySmall)
            }
        }
        PreparationExercise.entries.forEach { exercise ->
            OutlinedButton(onClick = {
                stopCamera(); selected = exercise; completedThisSession = false
                feedback = "Referência selecionada: ${exercise.title.lowercase()}."
            }, modifier = Modifier.fillMaxWidth()) {
                val learned = if (exercise.name in progress.completed) " · já praticado" else ""
                Text((if (selected == exercise) "✓ " else "") + exercise.title + learned)
            }
        }
        Text(selected.title, style = MaterialTheme.typography.titleMedium)
        Text(selected.instruction)
        ReferenceIllustration(selected)
        Text("Esquema estático de referência, sem significado linguístico. O polegar aparece apenas para orientar a leitura; não é avaliado.", style = MaterialTheme.typography.bodySmall)
        Text("Apoie o celular, deixe espaço para os dedos e use boa iluminação. Pare se houver dor. O reconhecimento pode falhar por oclusão, perspectiva, iluminação ou limitações do modelo.", style = MaterialTheme.typography.bodySmall)
        if (!hasCamera) Text("Este aparelho não informa uma câmera frontal ou traseira. Os esquemas e as referências permanecem disponíveis; a avaliação com câmera está desativada.", color = MaterialTheme.colorScheme.error)
        else if (!capabilities.hasFrontCamera) Text("Não há câmera frontal disponível. O treino utilizará a câmera traseira; apoie o aparelho para conseguir enquadrar sua mão.", style = MaterialTheme.typography.bodySmall)
        if (hasCamera && !policy.handTrackingSupported) Text("O rastreador de mãos deste APK não tem suporte ao processo ${capabilities.processAbi}. Os esquemas, as referências e o progresso continuam disponíveis, mas a avaliação com câmera está desativada.", color = MaterialTheme.colorScheme.error)
        if (!modelAvailable) Text("O modelo de mãos não está disponível neste build. As referências continuam acessíveis; o exercício com câmera está indisponível.", color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (running) stopCamera()
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
                else permission.launch(Manifest.permission.CAMERA)
            }, enabled = modelAvailable && hasCamera && policy.handTrackingSupported) { Text(if (running) "Parar câmera" else "Praticar com câmera") }
        }
        if (canSwitchCamera) OutlinedButton(onClick = { stopCamera(); frontCamera = !frontCamera; completedThisSession = false }) {
            Text(if (frontCamera) "Usar câmera traseira" else "Usar câmera frontal")
        }
        Text("Ao iniciar, a câmera será usada para detectar pontos das mãos neste aparelho. Não gravamos nem enviamos imagens. Salvo apenas contagens de treino e exercícios praticados.", style = MaterialTheme.typography.bodySmall)
        if (running) {
            Box(Modifier.fillMaxWidth().height(340.dp).background(Color.Black)
                .border(1.dp, NexusCyan, RoundedCornerShape(10.dp))
                .semantics { contentDescription = "Prévia da câmera ativa para treino preparatório. Os pontos detectados são sobrepostos e o resultado aparece em texto abaixo." }) {
                HandCamera(frontCamera = frontCamera, modifier = Modifier.fillMaxSize(), onFrame = { detected ->
                    frame = detected
                    if (!completedThisSession) {
                        val result = LibrasPracticeEvaluator.evaluate(detected.hands, selected)
                        feedback = result.message
                        if (gate.update(result.observation, detected.timestampMs)) {
                            completedThisSession = true
                            val completedExercise = selected
                            stopCamera()
                            feedback = "Treino registrado: posição estável por pelo menos 1,2 segundo. Isso confirma apenas a correspondência geométrica deste exercício, não um sinal de Libras."
                            scope.launch { runCatching { repository.recordCompletion(completedExercise) }.onFailure { feedback = "A posição foi mantida, mas não consegui salvar o progresso. Tente novamente." } }
                        }
                    }
                }, onError = { message -> stopCamera(); feedback = message })
                DetectedHandsOverlay(frame)
                Text("CÂMERA ATIVA • processamento local", Modifier.align(Alignment.TopCenter).background(Color.Black.copy(alpha = .7f)).padding(8.dp), color = NexusCyan)
            }
        }
        Text(feedback, color = if (completedThisSession) NexusCyan else MaterialTheme.colorScheme.onSurface)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Seu progresso de preparação", style = MaterialTheme.typography.titleMedium)
                Text("${progress.completed.count { name -> PreparationExercise.entries.any { it.name == name } }} de 2 configurações praticadas • ${progress.sessions} sessões iniciadas • ${progress.completions} posições estáveis")
                if (progress.lastCompletedAt > 0L) Text("Último treino: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(progress.lastCompletedAt))}", style = MaterialTheme.typography.bodySmall)
                if (progress.completed.isNotEmpty()) Text("Conquista de prática: primeiro treino registrado ✓", color = NexusCyan)
                if (PreparationExercise.entries.all { it.name in progress.completed }) Text("Conquista de prática: duas configurações exploradas ✓", color = NexusCyan)
                Text("Conquistas indicam prática neste módulo e não certificam aprendizado de Libras. Repita um exercício para revisão.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { export.launch("nexus-libras-progresso.json") }) { Text("Exportar progresso") }
                TextButton(onClick = { stopCamera(); deleteDialog = true }) { Text("Excluir progresso de preparação") }
            }
        }
        Text("Continue com referências educacionais", style = MaterialTheme.typography.titleMedium)
        Text("Para estudar sinais e sua execução completa, consulte conteúdos do INES e da UFSC. Essas instituições são referências de estudo; não validaram os exercícios nem o reconhecimento deste aplicativo.", style = MaterialTheme.typography.bodySmall)
        SourceLink("INES · Instituto Nacional de Educação de Surdos", "https://www.ines.gov.br/") { feedback = it }
        SourceLink("UFSC · estudos de Libras", "https://libras.ufsc.br/") { feedback = it }
    }
    if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("Excluir este progresso?") },
        text = { Text("As contagens e conquistas de preparação serão apagadas deste aparelho.") },
        confirmButton = { TextButton(onClick = {
            deleteDialog = false
            scope.launch { runCatching { repository.clear() }.onSuccess { feedback = "Progresso de preparação excluído." }.onFailure { feedback = "Não consegui excluir o progresso. Tente novamente." } }
        }) { Text("Excluir") } }, dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancelar") } })
}

@Composable
private fun SourceLink(label: String, url: String, onError: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { onError("Não foi possível abrir o navegador. Endereço: $url") }
    }, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

private val handEdges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12, 9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20)

@Composable
private fun ReferenceIllustration(exercise: PreparationExercise) {
    val points = if (exercise == PreparationExercise.EXTENDED) listOf(
        HandPoint(.5f,.84f), HandPoint(.36f,.76f), HandPoint(.26f,.67f), HandPoint(.18f,.59f), HandPoint(.12f,.51f),
        HandPoint(.36f,.60f), HandPoint(.32f,.42f), HandPoint(.30f,.29f), HandPoint(.29f,.18f),
        HandPoint(.46f,.58f), HandPoint(.46f,.37f), HandPoint(.46f,.23f), HandPoint(.46f,.12f),
        HandPoint(.56f,.60f), HandPoint(.59f,.41f), HandPoint(.61f,.28f), HandPoint(.62f,.18f),
        HandPoint(.66f,.63f), HandPoint(.72f,.49f), HandPoint(.76f,.39f), HandPoint(.79f,.31f)
    ) else listOf(
        HandPoint(.5f,.84f), HandPoint(.36f,.76f), HandPoint(.28f,.68f), HandPoint(.34f,.64f), HandPoint(.43f,.66f),
        HandPoint(.36f,.60f), HandPoint(.34f,.47f), HandPoint(.37f,.56f), HandPoint(.39f,.64f),
        HandPoint(.46f,.58f), HandPoint(.46f,.42f), HandPoint(.48f,.53f), HandPoint(.49f,.63f),
        HandPoint(.56f,.60f), HandPoint(.58f,.46f), HandPoint(.58f,.56f), HandPoint(.56f,.65f),
        HandPoint(.66f,.63f), HandPoint(.69f,.52f), HandPoint(.68f,.61f), HandPoint(.64f,.68f)
    )
    Canvas(Modifier.fillMaxWidth().height(230.dp).background(Color(0xFF071521), RoundedCornerShape(12.dp))
        .semantics { contentDescription = "Esquema original: ${exercise.title}. ${exercise.instruction}" }) {
        val side = min(size.width, size.height)
        fun at(point: HandPoint) = Offset((size.width - side) / 2f + point.x * side, point.y * side)
        handEdges.forEach { (a, b) -> drawLine(NexusCyan.copy(alpha = if (a in 1..3 || b in 1..4) .35f else .9f), at(points[a]), at(points[b]), strokeWidth = 8f, cap = StrokeCap.Round) }
        points.forEachIndexed { i, point -> drawCircle(if (i in 1..4) Color.Gray else NexusCyan, 5f, at(point)) }
    }
}

@Composable
private fun DetectedHandsOverlay(frame: HandFrame) {
    Canvas(Modifier.fillMaxSize()) {
        val scale = min(size.width / frame.imageWidth.coerceAtLeast(1), size.height / frame.imageHeight.coerceAtLeast(1))
        val width = frame.imageWidth * scale
        val height = frame.imageHeight * scale
        fun at(point: HandPoint) = Offset((size.width - width) / 2f + point.x * width, (size.height - height) / 2f + point.y * height)
        frame.hands.filter { it.points.size == 21 }.forEach { hand ->
            handEdges.forEach { (a, b) -> drawLine(NexusCyan.copy(alpha = .7f), at(hand.points[a]), at(hand.points[b]), 3f) }
            hand.points.forEach { point -> drawCircle(NexusCyan, 4f, at(point)) }
        }
    }
}
