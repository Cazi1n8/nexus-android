package br.com.nexus.assistant.libras

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.nexus.assistant.gestures.HandCamera
import br.com.nexus.assistant.gestures.HandFrame
import br.com.nexus.assistant.gestures.HandPoint
import br.com.nexus.assistant.gestures.global.GlobalMouseRuntime
import br.com.nexus.assistant.device.DeviceCapabilities
import br.com.nexus.assistant.device.DevicePolicy
import br.com.nexus.assistant.ui.NexusCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val globalMouse by GlobalMouseRuntime.state.collectAsStateWithLifecycle()
    val hasCamera = capabilities.hasFrontCamera || capabilities.hasRearCamera
    val canSwitchCamera = capabilities.hasFrontCamera && capabilities.hasRearCamera
    val repository = remember(context) { LibrasProgressRepository(context) }
    val progress by repository.progress.collectAsStateWithLifecycle(initialValue = LibrasProgress())
    var selected by rememberSaveable { mutableStateOf(PreparationExercise.EXTENDED) }
    var running by rememberSaveable { mutableStateOf(false) }
    var freeMode by rememberSaveable { mutableStateOf(false) }
    var letterPractice by rememberSaveable { mutableStateOf(false) }
    var selectedLetter by rememberSaveable { mutableStateOf(ManualLetterCandidate.L) }
    var observedLetter by remember { mutableStateOf<ManualLetterCandidate?>(null) }
    var letterMatched by remember { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(true) }
    var frontCamera by remember { mutableStateOf(capabilities.hasFrontCamera) }
    var frame by remember { mutableStateOf(HandFrame()) }
    var completedThisSession by remember { mutableStateOf(false) }
    var observedConfiguration by remember { mutableStateOf<PreparationExercise?>(null) }
    var feedback by remember { mutableStateOf("Escolha um exercício e leia a orientação antes de ligar a câmera.") }
    var deleteDialog by remember { mutableStateOf(false) }
    val gate = remember { PracticeHoldGate() }
    val letterGate = remember { StableLetterCandidate() }
    val modelAvailable = remember(context) {
        runCatching { context.assets.open("hand_landmarker.task").use { it.read() >= 0 } }.getOrDefault(false)
    }

    fun stopCamera() { running = false; frame = HandFrame(); observedConfiguration = null; observedLetter = null; letterMatched = false; gate.reset(); letterGate.reset() }

    fun startCamera() {
        if (GlobalMouseRuntime.state.value.active) { feedback = "O mouse global está usando a câmera. Encerre essa sessão antes de praticar aqui."; return }
        gate.reset(); letterGate.reset(); observedLetter = null; letterMatched = false; completedThisSession = false; frame = HandFrame(); running = true
        feedback = "Câmera ativa • mostre uma mão de frente e mantenha a posição até confirmar. Em aparelhos mais lentos, vários quadros podem levar alguns segundos."
        scope.launch { runCatching { repository.recordSession() }.onFailure { feedback = "O treino pode continuar, mas não consegui salvar o início da sessão." } }
    }

    LaunchedEffect(globalMouse.active) { if (globalMouse.active && running) stopCamera() }

    LaunchedEffect(running, owner) {
        if (running) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(500)
                if (frame.timestampMs != 0L && SystemClock.elapsedRealtime() - frame.timestampMs > 3_000L) {
                    frame = HandFrame(); observedLetter = null; observedConfiguration = null; letterMatched = false
                    gate.reset(); letterGate.reset()
                    feedback = "Sem quadro novo do rastreador. Nenhuma letra ou posição é aceita até a análise voltar. Verifique iluminação e se a câmera está disponível."
                }
            }
        }
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
                        .put("studiedLessons", JSONArray(snapshot.studiedLessons.sorted()))
                        .put("lessonProgressIsReadingAndConceptReview", true)
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
        // CameraX pauses its lifecycle-bound camera while this activity is stopped. Keep the
        // requested session so a temporary app switch does not silently finish the activity.
        // Continuous background tracking requires the separate foreground-service architecture.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                frame = HandFrame(); observedConfiguration = null; observedLetter = null; letterMatched = false; gate.reset(); letterGate.reset()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); gate.reset(); letterGate.reset() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("NEXUS Libras · preparação", style = MaterialTheme.typography.titleLarge)
        Text("Comece pela percepção e pelo controle dos dedos. São dois exercícios geométricos de nível inicial; não são sinais completos de Libras.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { freeMode = false; letterPractice = false; gate.reset(); letterGate.reset(); completedThisSession = false; observedConfiguration = null; observedLetter = null; letterMatched = false }) {
                Text(if (!freeMode && !letterPractice) "✓ Atividade" else "Atividade")
            }
            OutlinedButton(onClick = { freeMode = true; letterPractice = false; gate.reset(); letterGate.reset(); observedConfiguration = null; observedLetter = null; letterMatched = false }) {
                Text(if (freeMode) "✓ Modo livre" else "Modo livre")
            }
        }
        OutlinedButton(onClick = {
            freeMode = false; letterPractice = true; gate.reset(); letterGate.reset()
            observedLetter = null; letterMatched = false
        }) { Text(if (letterPractice) "✓ Treino L / V / Y · experimental" else "Treino L / V / Y · experimental") }
        if (freeMode || letterPractice) Text("O experimento procura configurações estáticas de L, V e Y. Mostra somente uma POSSÍVEL letra após vários quadros estáveis; outras letras ficam sem classificação. Não é reconhecimento validado do alfabeto completo.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Antes de praticar", style = MaterialTheme.typography.titleMedium)
                Text("Libras envolve configuração de mãos, orientação, localização, movimento, expressões não manuais e gramática. Este módulo mede somente parte da configuração de quatro dedos. Não avalia significado, orientação correta de um sinal, fluência ou pronúncia em Libras.")
                Text("Os exercícios e esquemas são próprios. O algoritmo ainda precisa de validação com pessoas sinalizantes e educadores; o resultado não é uma correção linguística.", style = MaterialTheme.typography.bodySmall)
            }
        }
        LessonLibrary(progress, onStudied = { lesson ->
            scope.launch {
                runCatching { repository.recordLessonStudied(lesson) }
                    .onSuccess { feedback = "Leitura e revisão conceitual registradas. Isso não certifica conhecimento de Libras." }
                    .onFailure { feedback = "Não consegui salvar a leitura. A aula continua disponível; tente novamente." }
            }
        }, onSourceError = { feedback = it })
        if (!letterPractice && !freeMode) PreparationExercise.entries.forEach { exercise ->
            OutlinedButton(onClick = {
                gate.reset(); selected = exercise; completedThisSession = false; observedConfiguration = null
                feedback = "Referência selecionada: ${exercise.title.lowercase()}."
            }, modifier = Modifier.fillMaxWidth()) {
                val learned = if (exercise.name in progress.completed) " · já praticado" else ""
                Text((if (selected == exercise) "✓ " else "") + exercise.title + learned)
            }
        }
        if (letterPractice) {
            ManualLetterCandidate.entries.forEach { letter ->
                OutlinedButton(onClick = {
                    selectedLetter = letter; letterGate.reset(); observedLetter = null; letterMatched = false
                }, modifier = Modifier.fillMaxWidth()) { Text((if (selectedLetter == letter) "✓ " else "") + "Configuração da letra ${letter.name}") }
            }
            Text("Alvo: ${selectedLetter.name} · treino experimental", style = MaterialTheme.typography.titleMedium)
            Text(selectedLetter.instruction)
            ManualConfigurationIllustration(selectedLetter)
            Text("A correspondência verifica uma geometria limitada, não correção linguística. Ela não concede conquista ou certificação de Libras.", style = MaterialTheme.typography.bodySmall)
        } else if (freeMode) {
            Text("Mostre uma configuração de L, V ou Y. A hipótese estável aparece no canto da câmera; sem hipótese clara, nenhuma letra é exibida.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(selected.title, style = MaterialTheme.typography.titleMedium)
            Text(selected.instruction)
            ReferenceIllustration(selected)
            Text("Esquema estático de referência, sem significado linguístico. O polegar aparece apenas para orientar a leitura; não é avaliado neste exercício preparatório.", style = MaterialTheme.typography.bodySmall)
        }
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
            }, enabled = running || modelAvailable && hasCamera && policy.handTrackingSupported && !globalMouse.active) { Text(if (running) "Parar câmera" else "Praticar com câmera") }
        }
        if (globalMouse.active) {
            Text("A câmera está em uso pelo mouse global. A leitura das referências continua disponível.")
            OutlinedButton(onClick = { GlobalMouseRuntime.stop(context) }) { Text("Parar mouse global e liberar câmera") }
        }
        if (canSwitchCamera) OutlinedButton(onClick = { gate.reset(); letterGate.reset(); frame = HandFrame(); frontCamera = !frontCamera; observedConfiguration = null; observedLetter = null; letterMatched = false }) {
            Text(if (frontCamera) "Usar câmera traseira" else "Usar câmera frontal")
        }
        OutlinedButton(onClick = { showPreview = !showPreview }) { Text(if (showPreview) "Ocultar imagem da câmera" else "Mostrar imagem da câmera") }
        Text("Ao iniciar, a câmera será usada para detectar pontos das mãos neste aparelho. Não gravamos nem enviamos imagens. Salvo apenas contagens de treino e exercícios praticados.", style = MaterialTheme.typography.bodySmall)
        Text("A sessão continua depois de registrar um acerto, até você tocar em Parar câmera. Ao sair do aplicativo, o Android pausa esta câmera; ao voltar a esta tela, o treino pode continuar. Os indicadores de privacidade permanecem visíveis mesmo com a imagem oculta.", style = MaterialTheme.typography.bodySmall)
        if (running) {
            Box(Modifier.fillMaxWidth().height(340.dp).background(Color.Black)
                .border(1.dp, NexusCyan, RoundedCornerShape(10.dp))
                .semantics { contentDescription = "Prévia da câmera ativa para treino preparatório. Os pontos detectados são sobrepostos e o resultado aparece em texto abaixo." }) {
                HandCamera(frontCamera = frontCamera, showPreview = showPreview, modifier = Modifier.fillMaxSize(), onFrame = { detected ->
                    frame = detected
                    if (freeMode || letterPractice) {
                        val letterResult = LibrasLetterCandidates.observe(detected.hands)
                        observedLetter = letterGate.update(letterResult.candidate, detected.timestampMs)
                        observedConfiguration = LibrasPracticeEvaluator.observe(detected.hands).configuration
                        letterMatched = letterPractice && observedLetter == selectedLetter
                        feedback = when {
                            letterMatched -> "Configuração geométrica compatível com ${selectedLetter.name} mantida. Continue praticando; a atividade permanece ativa. Não é validação linguística."
                            observedLetter != null -> "Possível letra ${observedLetter!!.name} · experimental." + if (letterPractice) " Alvo: ${selectedLetter.name}." else ""
                            letterResult.candidate != null -> "Aguardando estabilidade da configuração; mantenha a mão visível."
                            else -> letterResult.message
                        }
                    } else {
                        val result = LibrasPracticeEvaluator.evaluate(detected.hands, selected)
                        observedConfiguration = result.configuration
                        feedback = result.message
                        if (!completedThisSession && gate.update(result.observation, detected.timestampMs)) {
                            completedThisSession = true
                            val completedExercise = selected
                            scope.launch { runCatching { repository.recordCompletion(completedExercise) }.onFailure { feedback = "A posição foi mantida, mas não consegui salvar o progresso. Tente novamente." } }
                        }
                        if (completedThisSession) feedback += " Treino registrado; continue praticando ou escolha outra atividade."
                    }
                }, onError = { message -> stopCamera(); feedback = message })
                DetectedHandsOverlay(frame)
                Text("CÂMERA ATIVA • processamento local", Modifier.align(Alignment.TopCenter).background(Color.Black.copy(alpha = .7f)).padding(8.dp), color = NexusCyan)
                Card(Modifier.align(Alignment.BottomEnd).padding(8.dp).width(155.dp)) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (freeMode || letterPractice) {
                            if (letterPractice) Text("Faça: ${selectedLetter.name}", style = MaterialTheme.typography.titleMedium, color = NexusCyan)
                            Text(observedLetter?.let { "Possível letra ${it.name}" } ?: "Letra não identificada", style = MaterialTheme.typography.bodySmall)
                            Text("EXPERIMENTAL · L / V / Y", style = MaterialTheme.typography.labelSmall)
                            if (letterPractice) Text(if (letterMatched) "Configuração mantida • treino continua" else selectedLetter.instruction, style = MaterialTheme.typography.labelSmall)
                            else Text(observedConfiguration?.title ?: "Mostre uma configuração suportada", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Text("Faça esta configuração", style = MaterialTheme.typography.labelMedium, color = NexusCyan)
                            Text(selected.title, style = MaterialTheme.typography.bodySmall)
                            ReferenceIllustration(selected, Modifier.fillMaxWidth().height(105.dp))
                            if (completedThisSession) Text("✓ Registrado • treino continua", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Text(feedback, color = if (completedThisSession && !freeMode && !letterPractice || letterMatched) NexusCyan else MaterialTheme.colorScheme.onSurface)
        if (running && !freeMode && !letterPractice) {
            val currentAssessment = remember(frame, selected) { LibrasPracticeEvaluator.evaluate(frame.hands, selected) }
            if (currentAssessment.fingers.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Observação geométrica por dedo", style = MaterialTheme.typography.titleSmall)
                    currentAssessment.fingers.forEach { finger ->
                        Text("${finger.finger}: ${finger.posture.label}. ${finger.message}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("O polegar e os outros parâmetros de um sinal não são avaliados aqui.", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (completedThisSession) OutlinedButton(onClick = {
                gate.reset(); completedThisSession = false; observedConfiguration = null
                feedback = "Nova repetição: mantenha a configuração novamente. A câmera e a atividade continuam ativas."
            }) { Text("Repetir este exercício sem parar a câmera") }
        }
        if (running) Text("${frame.hands.size} mão(s) · análise ${frame.processingMs} ms · o resultado usa geometria, não uma porcentagem de acerto em Libras.", style = MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Seu progresso de preparação", style = MaterialTheme.typography.titleMedium)
                Text("${progress.completed.count { name -> PreparationExercise.entries.any { it.name == name } }} de 2 configurações praticadas • ${progress.sessions} sessões iniciadas • ${progress.completions} posições estáveis")
                Text("${progress.studiedLessons.size} de ${LibrasLessons.entries.size} aulas lidas e revisadas. Esse registro é separado da avaliação geométrica.", style = MaterialTheme.typography.bodySmall)
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
        SourceLink("INES · Instituto Nacional de Educação de Surdos", "https://www.gov.br/ines/pt-br") { feedback = it }
        SourceLink("UFSC · estudos de Libras", "https://libras.ufsc.br/") { feedback = it }
        SourceLink(ManualLetterCandidate.REFERENCE_TITLE, ManualLetterCandidate.REFERENCE_URL) { feedback = it }
        Text("A referência descreve aspectos linguísticos e configurações de mão; não valida os limiares deste experimento. Ainda faltam avaliação com educadores e imagens de mãos reais antes de anunciar precisão no reconhecimento de letras.", style = MaterialTheme.typography.bodySmall)
    }
    if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("Excluir este progresso?") },
        text = { Text("As leituras de aulas, contagens e conquistas de preparação serão apagadas deste aparelho.") },
        confirmButton = { TextButton(onClick = {
            deleteDialog = false
            scope.launch { runCatching { repository.clear() }.onSuccess { feedback = "Progresso de preparação excluído." }.onFailure { feedback = "Não consegui excluir o progresso. Tente novamente." } }
        }) { Text("Excluir") } }, dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancelar") } })
}

@Composable
private fun LessonLibrary(
    progress: LibrasProgress,
    onStudied: (LibrasLessonId) -> Unit,
    onSourceError: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(LibrasLessonId.LANGUAGE) }
    var answer by rememberSaveable(selected) { mutableStateOf<Int?>(null) }
    val lesson = LibrasLessons.get(selected)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Biblioteca de introdução e revisão", style = MaterialTheme.typography.titleMedium)
            Text("Três aulas curtas disponíveis offline, com fontes para aprofundar o estudo. Não substituem demonstrações completas ou orientação de educadores.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Recolher aulas" else "Abrir aulas e revisão") }
            if (expanded) {
                LibrasLessons.entries.forEach { entry ->
                    OutlinedButton(onClick = { selected = entry.id }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (selected == entry.id) "✓ " else "") + entry.title +
                            if (entry.id.name in progress.studiedLessons) " · leitura registrada" else "")
                    }
                }
                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                lesson.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text("Confira o conceito", style = MaterialTheme.typography.titleSmall)
                Text(lesson.question)
                lesson.answers.forEachIndexed { index, text ->
                    OutlinedButton(onClick = { answer = index }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (answer == index) "✓ " else "") + text)
                    }
                }
                answer?.let { selectedAnswer ->
                    Text(if (lesson.acceptsAnswer(selectedAnswer)) "Conceito conferido. ${lesson.explanation}"
                        else "Revise a explicação. ${lesson.explanation}", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { onStudied(lesson.id) }, enabled = answer?.let(lesson::acceptsAnswer) == true) {
                    Text(if (lesson.id.name in progress.studiedLessons) "Registrar revisão da leitura" else "Registrar leitura e revisão")
                }
                SourceLink(lesson.sourceTitle, lesson.sourceUrl, onSourceError)
                LibrasLessons.nextToStudy(progress.studiedLessons)?.takeIf { it.id != selected }?.let { next ->
                    TextButton(onClick = { selected = next.id }) { Text("Próxima leitura sugerida: ${next.title}") }
                }
                Text("Responder esta pergunta verifica somente o conceito apresentado. Não é avaliação de produção, tradução ou fluência em Libras.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Authored finger-configuration schematic, deliberately not a full sign demonstration. */
@Composable
private fun ManualConfigurationIllustration(letter: ManualLetterCandidate) {
    val extended = when (letter) {
        ManualLetterCandidate.L -> setOf(0, 1)
        ManualLetterCandidate.V -> setOf(1, 2)
        ManualLetterCandidate.Y -> setOf(0, 4)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(165.dp).background(Color(0xFF071521), RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Esquema de dedos da configuração experimental ${letter.name}. ${letter.instruction} Não demonstra orientação ou movimento linguístico." }) {
            fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
            val base = listOf(point(.38f, .73f), point(.43f, .63f), point(.50f, .61f), point(.57f, .64f), point(.64f, .69f))
            val tips = listOf(point(.21f, .39f), point(.40f, .16f), point(.51f, .11f), point(.62f, .19f), point(.74f, .29f))
            val folded = listOf(point(.48f, .72f), point(.45f, .73f), point(.51f, .74f), point(.57f, .76f), point(.62f, .79f))
            val wrist = point(.51f, .92f)
            base.forEachIndexed { index, start ->
                drawLine(NexusCyan.copy(alpha = .45f), wrist, start, 6f, StrokeCap.Round)
                val end = if (index in extended) tips[index] else folded[index]
                drawLine(if (index in extended) NexusCyan else Color.Gray, start, end, 9f, StrokeCap.Round)
                drawCircle(if (index in extended) NexusCyan else Color.Gray, 5f, end)
            }
        }
        Text("Esquema original apenas dos dedos estendidos/recolhidos. Observe orientação, localização, movimento e uso em contexto nas referências educacionais; o esquema não demonstra um sinal completo.", style = MaterialTheme.typography.bodySmall)
    }
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
private fun ReferenceIllustration(exercise: PreparationExercise, modifier: Modifier = Modifier.fillMaxWidth().height(230.dp)) {
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
    Canvas(modifier.background(Color(0xFF071521), RoundedCornerShape(12.dp))
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
