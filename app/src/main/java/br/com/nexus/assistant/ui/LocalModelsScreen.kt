package br.com.nexus.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.nexus.assistant.NexusViewModel
import br.com.nexus.assistant.data.AiMode
import br.com.nexus.assistant.data.local.*
import br.com.nexus.assistant.device.DevicePolicy
import br.com.nexus.assistant.storage.formatStorageBytes

/** A complete inventory, including inactive models left by an older installation. */
@Composable
fun LocalModelsScreen(model: NexusViewModel, onOpenSettings: () -> Unit, onOpenStorage: () -> Unit) {
    val states by model.localModelStates.collectAsStateWithLifecycle()
    val operation by model.localModelOperationProfile.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val capabilities by model.deviceCapabilities.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Pair<LocalModelProfile, Boolean>?>(null) }
    LaunchedEffect(model) { model.refreshLocalModels() }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MODELOS DE IA OPCIONAIS", style = MaterialTheme.typography.headlineSmall)
        Text("O CORE e o mouse offline funcionam sem estes arquivos. Baixe somente o perfil que quiser usar. Modelos já preparados pela versão antiga são reutilizados, sem nova cópia.")
        Text("A IA online não precisa de pesos no celular; exige um servidor real e autorização. Não há serviço NEXUS público implantado.")
        OutlinedButton(onClick = onOpenSettings) { Text("Configurar IA online ou personalidade") }
        LocalModelProfile.entries.forEach { profile ->
            val spec = LocalModelCatalog.forProfile(profile)
            val state = states[profile] ?: LocalModelState(totalBytes = spec.bytes)
            val compatible = DevicePolicy.canRunProfile(capabilities, profile)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(spec.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("Arquivo completo: ${formatStorageBytes(spec.bytes)}")
                    Text("Dados locais do modelo: ${formatStorageBytes(state.downloadedBytes)} · ${state.status}")
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (!compatible) Text("Este perfil não cabe nas condições de ABI/RAM detectadas. Isso não desativa o mouse ou outras funções do CORE.", style = MaterialTheme.typography.bodySmall)
                    if (operation == profile) {
                        LinearProgressIndicator(progress = { (state.downloadedBytes.toDouble() / spec.bytes).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = model::cancelLocalModelDownload) { Text("Pausar preparação") }
                    } else {
                        if (state.status != LocalModelStatus.READY) Button(onClick = { pending = profile to false }, enabled = compatible && operation == null && !busy && state.status != LocalModelStatus.CHECKING) {
                            Text(if (state.downloadedBytes > 0) "Continuar download" else "Baixar este modelo")
                        }
                        if (state.status == LocalModelStatus.READY) OutlinedButton(onClick = {
                            model.saveSettings(settings.copy(aiMode = AiMode.NEXUS_LOCAL, automaticProfile = false, localModelProfile = profile))
                        }, enabled = compatible && operation == null && !busy) { Text("Usar este modelo no chat") }
                        if (state.downloadedBytes > 0 || state.status == LocalModelStatus.READY) TextButton(onClick = { pending = profile to true }, enabled = operation == null && !busy) { Text("Remover modelo e download parcial") }
                    }
                }
            }
        }
        Text("A seleção automática recomenda um perfil por recursos medidos, mas não inicia downloads. A RAM disponível varia. Os modelos pequenos continuam sujeitos a erros de interpretação e escrita.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { model.refreshLocalModels() }, enabled = operation == null && !busy) { Text("Atualizar inventário") }
        OutlinedButton(onClick = onOpenStorage) { Text("Ver armazenamento utilizado") }
    }
    pending?.let { (profile, remove) ->
        val spec = LocalModelCatalog.forProfile(profile)
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(if (remove) "Remover dados deste modelo?" else "Baixar IA offline opcional?") },
            text = { Text(if (remove) "Serão removidos somente ${spec.displayName} e seu arquivo parcial. A inferência será encerrada antes da remoção. Conversas, configurações e rastreamento de mãos serão preservados."
                else "Download de ${formatStorageBytes(spec.bytes)} da revisão oficial Qwen/Hugging Face. O arquivo será verificado por tamanho e SHA-256. Isso não envia suas conversas e não melhora automaticamente a capacidade do modelo.") },
            confirmButton = { TextButton(onClick = {
                pending = null
                if (remove) model.deleteLocalModel(profile) else model.downloadLocalModel(profile)
            }) { Text(if (remove) "Remover" else "Baixar") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar") } })
    }
}
