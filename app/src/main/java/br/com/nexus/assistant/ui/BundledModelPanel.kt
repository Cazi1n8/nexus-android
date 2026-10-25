package br.com.nexus.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.nexus.assistant.NexusViewModel
import br.com.nexus.assistant.data.local.LocalModelStatus
import br.com.nexus.assistant.device.DevicePolicy

/** Reflects real asset-copy and whole-file verification, never a simulated download. */
@Composable
internal fun BundledModelPanel(model: NexusViewModel, onUseCustom: () -> Unit, showDetails: Boolean) {
    val state by model.localModelState.collectAsStateWithLifecycle()
    val spec by model.localModelSpec.collectAsStateWithLifecycle()
    val device by model.deviceCapabilities.collectAsStateWithLifecycle()
    val policy by model.devicePolicy.collectAsStateWithLifecycle()
    val active by model.localModelDownloadActive.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val profile by model.effectiveLocalProfile.collectAsStateWithLifecycle()
    val compatible = policy.localAiSupported && DevicePolicy.canRunProfile(device, profile)
    var confirmRemove by remember { mutableStateOf(false) }
    val preparing = active || state.status in setOf(LocalModelStatus.COPYING, LocalModelStatus.VERIFYING)
    Card(Modifier.fillMaxWidth().padding(horizontal = if (showDetails) 0.dp else 12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("IA INCLUÍDA NO APK", style = MaterialTheme.typography.labelLarge)
            Text(when (state.status) {
                LocalModelStatus.READY -> "IA local pronta para conversar"
                LocalModelStatus.COPYING -> "Copiando IA do APK para o armazenamento local…"
                LocalModelStatus.VERIFYING -> "Verificando a integridade da IA no aparelho…"
                LocalModelStatus.PAUSED -> "Preparação pausada"
                LocalModelStatus.FAILED -> "Não foi possível preparar a IA"
                else -> "Preparar IA incluída"
            })
            if (!compatible) Text(policy.reason)
            else if (preparing) {
                val done = if (state.status == LocalModelStatus.VERIFYING) state.verifiedBytes else state.downloadedBytes
                val fraction = (done.toDouble() / spec.bytes).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text("${done / 1_000_000} / ${spec.bytes / 1_000_000} MB · sem baixar outro arquivo", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = model::cancelLocalModelDownload) { Text("Pausar preparação") }
            } else if (state.status != LocalModelStatus.READY) {
                Button(onClick = model::prepareLocalAi) { Text(if (state.status == LocalModelStatus.PAUSED) "Continuar preparação" else "Preparar IA incluída") }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (showDetails) {
                Text("Qwen2.5 0.5B · texto · processamento ajustado à RAM e CPU. A IA pode errar. Imagens: leitura de texto local; descrição de cenas exige provedor multimodal opcional.", style = MaterialTheme.typography.bodySmall)
                Text("A preparação usa cerca de 492 MB adicionais. Apagar a cópia não reduz o APK; abrir o Chat permite prepará-la novamente.", style = MaterialTheme.typography.bodySmall)
                if (!preparing) TextButton(onClick = { confirmRemove = true }, enabled = !busy) { Text("Remover cópia preparada") }
                OutlinedButton(onClick = onUseCustom, enabled = !preparing) { Text("Configurar provedor opcional") }
            }
        }
    }
    if (confirmRemove) AlertDialog(onDismissRequest = { confirmRemove = false },
        title = { Text("Remover cópia local da IA?") },
        text = { Text("As conversas serão preservadas. O modelo continua incluído no APK e poderá ser preparado novamente sem internet.") },
        confirmButton = { TextButton(onClick = { confirmRemove = false; model.deleteLocalModel() }) { Text("Remover") } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } })
}
