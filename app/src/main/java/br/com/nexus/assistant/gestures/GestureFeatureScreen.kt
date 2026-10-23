package br.com.nexus.assistant.gestures

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.min

enum class GestureMode { MOUSE, HANDS, CUBE }

private enum class GestureAction(val label: String) {
    NONE("Nenhuma ação"), SELECT("Contar seleção"), COMMANDS("Abrir central de comandos"), BACK("Voltar nesta tela")
}

/** All actions stay inside NEXUS. No AccessibilityService or global mouse is enabled. */
@Composable
fun GestureFeatureScreen(
    mode: GestureMode,
    onNavigateBack: () -> Unit = {},
    onOpenCommands: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("nexus_gestures", Context.MODE_PRIVATE) }
    val modelAvailable = remember(context) {
        runCatching { context.assets.open("hand_landmarker.task").use { it.read() != -1 } }.getOrDefault(false)
    }
    var sensitivity by remember { mutableFloatStateOf(preferences.getFloat("sensitivity", 1.3f).coerceIn(.6f, 2.3f)) }
    var pinchThreshold by remember { mutableFloatStateOf(preferences.getFloat("pinch", .35f).coerceIn(.2f, .55f)) }
    var mapping by remember {
        mutableStateOf(HandConfiguration.entries.filter { it != HandConfiguration.OTHER }.associateWith { gesture ->
            runCatching { GestureAction.valueOf(preferences.getString("action_${gesture.name}", "NONE") ?: "NONE") }
                .getOrDefault(GestureAction.NONE)
        })
    }
    var tracking by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(true) }
    var frame by remember { mutableStateOf(HandFrame()) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cursor by remember { mutableStateOf<HandPoint?>(null) }
    var gesture by remember { mutableStateOf(HandConfiguration.OTHER) }
    var pinching by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var clicks by remember { mutableIntStateOf(0) }
    var doubles by remember { mutableIntStateOf(0) }
    var selections by remember { mutableIntStateOf(0) }
    var draggable by remember { mutableStateOf(HandPoint(.4f, .67f)) }
    var feedback by remember { mutableStateOf("Mostre a mão com boa iluminação, a cerca de 40–80 cm da câmera.") }
    var cubeCenter by remember { mutableStateOf(HandPoint(.5f, .5f)) }
    var cubeYaw by remember { mutableFloatStateOf(.6f) }
    var cubePitch by remember { mutableFloatStateOf(.35f) }
    var cubeScale by remember { mutableFloatStateOf(.15f) }
    var cubeRotate by remember { mutableStateOf(false) }
    var previousCursor by remember { mutableStateOf<HandPoint?>(null) }
    var referenceDistance by remember { mutableStateOf<Float?>(null) }
    var referenceScale by remember { mutableFloatStateOf(.15f) }
    val filter = remember(mode, frontCamera, tracking) { CursorFilter() }
    val pinch = remember(mode, frontCamera, tracking) { PinchInterpreter() }
    val gate = remember(mode, frontCamera, tracking) { StableGestureGate() }
    val neon = Color(0xFF31E7FA)

    fun clearHand() {
        cursor = null
        gesture = HandConfiguration.OTHER
        dragging = false
        pinching = false
        previousCursor = null
        referenceDistance = null
        filter.reset()
        pinch.reset()
        gate.reset()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(when (mode) {
            GestureMode.MOUSE -> "Mouse virtual · laboratório local"
            GestureMode.HANDS -> "Mãos livres · ações no NEXUS"
            GestureMode.CUBE -> "NEXUS 3D · cubo interativo"
        }, style = MaterialTheme.typography.titleLarge)
        Text("MediaPipe no aparelho • nenhuma imagem enviada • use somente com o celular apoiado.",
            style = MaterialTheme.typography.bodySmall)
        if (!modelAvailable) Text("Modelo de mãos ausente neste build. Baixe o modelo oficial com scripts/fetch_hand_model.py e compile novamente. O cubo pode ser explorado pelos controles manuais.", color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tracking = !tracking; clearHand(); frame = HandFrame() }, enabled = modelAvailable) {
                Text(if (tracking) "Parar câmera" else "Iniciar câmera")
            }
            OutlinedButton(onClick = { frontCamera = !frontCamera; clearHand(); frame = HandFrame(); cameraError = null }) {
                Text(if (frontCamera) "Usar traseira" else "Usar frontal")
            }
        }
        Box(Modifier.fillMaxWidth().height(340.dp).background(Color.Black)
            .border(1.dp, neon.copy(alpha = .45f), RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Câmera de rastreamento de mãos. O resultado também aparece em texto abaixo." }) {
            if (tracking) {
                HandCamera(frontCamera, Modifier.fillMaxSize(), onFrame = { next ->
                    frame = next
                    cameraError = null
                    val hand = next.hands.firstOrNull()
                    if (hand == null || hand.points.size != 21) {
                        clearHand()
                    } else {
                        val event = pinch.update(hand.pinchRatio, next.timestampMs, pinchThreshold)
                        pinching = event.pinching
                        gesture = hand.configuration(event.pinching)
                        val point = filter.update(hand.index, sensitivity)
                        cursor = if (mode != GestureMode.MOUSE || hand.fingerExtended(8, 6) || event.pinching || event.clicked) point else null
                        if (mode == GestureMode.MOUSE) {
                            if (event.clicked) {
                                clicks++
                                feedback = "Clique reconhecido por uma pinça curta."
                                if (distance(point, HandPoint(.78f, .3f)) < .17f) { selections++; feedback = "Alvo selecionado!" }
                            }
                            if (event.doubleClicked) { doubles++; feedback = "Duplo clique reconhecido." }
                            if (event.dragStarted) {
                                dragging = distance(point, draggable) < .22f
                                feedback = if (dragging) "Arrastando o objeto. Solte a pinça para liberar." else "Faça a pinça sobre o objeto para arrastá-lo."
                            }
                            if (event.dragging && dragging) draggable = point
                            if (event.dragEnded) { dragging = false; feedback = "Objeto solto." }
                        }
                        if (mode == GestureMode.HANDS) {
                            gate.update(gesture, next.timestampMs)?.let { recognized ->
                                when (mapping[recognized] ?: GestureAction.NONE) {
                                    GestureAction.NONE -> feedback = "${recognized.label} reconhecido; nenhuma ação atribuída."
                                    GestureAction.SELECT -> { selections++; feedback = "Seleção por ${recognized.label.lowercase()}." }
                                    GestureAction.COMMANDS -> onOpenCommands()
                                    GestureAction.BACK -> onNavigateBack()
                                }
                            }
                        }
                        if (mode == GestureMode.CUBE) {
                            if (event.pinching) {
                                if (cubeRotate) {
                                    previousCursor?.let { old ->
                                        cubeYaw += (point.x - old.x) * 6f
                                        cubePitch += (point.y - old.y) * 6f
                                    }
                                } else {
                                    cubeCenter = point
                                }
                            }
                            previousCursor = if (event.pinching) point else null
                            if (next.hands.size >= 2) {
                                val handDistance = distance(next.hands[0].points[9], next.hands[1].points[9]).coerceAtLeast(.04f)
                                if (referenceDistance == null) { referenceDistance = handDistance; referenceScale = cubeScale }
                                cubeScale = (referenceScale * handDistance / (referenceDistance ?: handDistance)).coerceIn(.06f, .32f)
                            } else referenceDistance = null
                        }
                    }
                }, onError = { cameraError = it; frame = HandFrame(); clearHand() })
            }
            Canvas(Modifier.fillMaxSize()) {
                val scale = min(size.width / frame.imageWidth, size.height / frame.imageHeight)
                val imageW = frame.imageWidth * scale
                val imageH = frame.imageHeight * scale
                val left = (size.width - imageW) / 2f
                val top = (size.height - imageH) / 2f
                fun cameraPoint(point: HandPoint) = Offset(left + point.x * imageW, top + point.y * imageH)
                val connections = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8,
                    5 to 9, 9 to 10, 10 to 11, 11 to 12, 9 to 13, 13 to 14, 14 to 15, 15 to 16,
                    13 to 17, 17 to 18, 18 to 19, 19 to 20, 0 to 17)
                frame.hands.forEach { hand ->
                    if (hand.points.size == 21) {
                        connections.forEach { (a, b) -> drawLine(neon.copy(alpha = .65f), cameraPoint(hand.points[a]), cameraPoint(hand.points[b]), 2f) }
                        hand.points.forEach { drawCircle(neon, 3.5f, cameraPoint(it)) }
                    }
                }
                if (mode == GestureMode.MOUSE) {
                    val target = Offset(size.width * .78f, size.height * .3f)
                    drawCircle(Color(0xFF17A8EF).copy(alpha = .28f), 36.dp.toPx(), target)
                    drawCircle(neon, 36.dp.toPx(), target, style = Stroke(2.dp.toPx()))
                    val objectPoint = Offset(draggable.x * size.width, draggable.y * size.height)
                    drawRect(if (dragging) neon else Color(0xFFA588FF), objectPoint - Offset(22.dp.toPx(), 22.dp.toPx()),
                        androidx.compose.ui.geometry.Size(44.dp.toPx(), 44.dp.toPx()), style = Stroke(3.dp.toPx()))
                }
                if (mode == GestureMode.CUBE) {
                    val vertices = projectCube(cubeYaw, cubePitch, cubeScale)
                    val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
                    fun projected(index: Int) = Offset(cubeCenter.x * size.width + vertices[index].x * size.minDimension,
                        cubeCenter.y * size.height + vertices[index].y * size.minDimension)
                    edges.forEach { (a, b) ->
                        val alpha = if (vertices[a].depth + vertices[b].depth > 0f) .45f else 1f
                        drawLine(neon.copy(alpha = alpha * .15f), projected(a), projected(b), 10.dp.toPx())
                        drawLine(neon.copy(alpha = alpha), projected(a), projected(b), 2.dp.toPx())
                    }
                }
                if (mode == GestureMode.MOUSE || (mode == GestureMode.CUBE && pinching)) {
                    cursor?.let { point ->
                        val center = Offset(point.x * size.width, point.y * size.height)
                        drawCircle(neon.copy(alpha = .12f), 22.dp.toPx(), center)
                        drawCircle(if (pinching) Color.White else neon, 13.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                        drawCircle(neon, 3.dp.toPx(), center)
                    }
                }
            }
        }
        cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(when {
            !tracking -> "Câmera desligada."
            cameraError != null -> "Rastreamento indisponível."
            frame.timestampMs == 0L -> "Iniciando câmera e modelo local…"
            else -> "Câmera ativa · ${frame.hands.size} mão(s) · inferência ${frame.processingMs} ms"
        }, color = neon, style = MaterialTheme.typography.bodySmall)
        Text(if (cursor == null && frame.hands.isEmpty()) "Nenhuma mão detectada." else "Gesto: ${gesture.label}${if (pinching) " · pinça ativa" else ""}")
        Text(feedback, style = MaterialTheme.typography.bodyMedium)

        if (mode == GestureMode.MOUSE) {
            Text("Cliques: $clicks · duplos: $doubles · alvos: $selections")
            Text("Levante o indicador para mover o cursor. Faça uma pinça curta para clicar no círculo. Duas pinças rápidas: duplo clique. Segure a pinça sobre o quadrado por 0,45 s para arrastar.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    frame.hands.firstOrNull()?.let { filter.center(it.index, sensitivity); feedback = "Posição atual centralizada." }
                }, enabled = frame.hands.isNotEmpty()) { Text("Calibrar centro") }
                OutlinedButton(onClick = { clicks = 0; doubles = 0; selections = 0; draggable = HandPoint(.4f, .67f) }) { Text("Reiniciar teste") }
            }
            Text("O cursor funciona apenas neste laboratório. O controle de outros aplicativos ainda não foi implementado.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (mode == GestureMode.HANDS) {
            Text("Mantenha o gesto por 0,42 s. Troque a postura antes de repetir. Ações só são executadas nesta sessão visível.")
            Text("Seleções: $selections")
            mapping.forEach { (configuration, action) ->
                var expanded by remember(configuration) { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(configuration.label, style = MaterialTheme.typography.titleSmall)
                        Box {
                            OutlinedButton(onClick = { expanded = true }) { Text(action.label) }
                            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                                GestureAction.entries.forEach { selected ->
                                    DropdownMenuItem(text = { Text(selected.label) }, onClick = {
                                        mapping = mapping + (configuration to selected)
                                        preferences.edit().putString("action_${configuration.name}", selected.name).apply()
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }
                }
            }
            Text("Essas configurações de mão são heurísticas; não constituem reconhecimento de Libras. Deslocamentos laterais e abertura de aplicativos por gestos estão pendentes.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (mode == GestureMode.CUBE) {
            Text("Faça uma pinça para ${if (cubeRotate) "girar" else "mover"} o cubo. Duas mãos visíveis: afaste ou aproxime as palmas para mudar a escala. Solte a pinça para liberar.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { cubeRotate = !cubeRotate; previousCursor = null }) { Text(if (cubeRotate) "Modo: girar" else "Modo: mover") }
                OutlinedButton(onClick = {
                    cubeCenter = HandPoint(.5f, .5f); cubeYaw = .6f; cubePitch = .35f; cubeScale = .15f; referenceDistance = null
                }) { Text("Reiniciar") }
            }
            Text("Controle manual da rotação", style = MaterialTheme.typography.labelLarge)
            Slider(value = cubeYaw.coerceIn(-3.14f, 3.14f), onValueChange = { cubeYaw = it }, valueRange = -3.14f..3.14f)
            Text("O cubo usa projeção 3D sobre a câmera. Não há posicionamento espacial ou rastreamento de realidade aumentada.",
                style = MaterialTheme.typography.bodySmall)
        }
        Text("Sensibilidade: ${"%.1f".format(sensitivity)}×", style = MaterialTheme.typography.labelLarge)
        Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = .6f..2.3f,
            onValueChangeFinished = { preferences.edit().putFloat("sensitivity", sensitivity).apply() })
        Text("Distância de pinça: ${"%.2f".format(pinchThreshold)} (relativa à palma)", style = MaterialTheme.typography.labelLarge)
        Slider(value = pinchThreshold, onValueChange = { pinchThreshold = it }, valueRange = .2f.. .55f,
            onValueChangeFinished = { preferences.edit().putFloat("pinch", pinchThreshold).apply() })
        Text("Rastreamento experimental: iluminação, enquadramento e o aparelho afetam precisão e atraso. Resultados em hardware real ainda precisam ser validados.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
    }
}
