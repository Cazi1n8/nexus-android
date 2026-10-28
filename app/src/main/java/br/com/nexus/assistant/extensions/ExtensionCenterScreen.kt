package br.com.nexus.assistant.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExtensionCenterScreen(
    onOpenAi: () -> Unit,
    onOpenVision: () -> Unit,
    onOpenThreeD: () -> Unit,
    onOpenLibras: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenStorage: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) { ExtensionRepository.get(context) }
    val states by repository.states.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<ExtensionPackId, Boolean>?>(null) }
    LaunchedEffect(repository) {
        runCatching { repository.refresh() }.onFailure { message = it.message ?: "Não foi possível verificar os pacotes." }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CENTRAL DE EXTENSÕES", style = MaterialTheme.typography.headlineSmall)
        Text("O mouse offline e seu rastreador fazem parte do CORE. Aqui você escolhe dados adicionais; nenhum código executável é baixado.")
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        ModuleCard("NEXUS AI", "IA online exige um serviço real configurado. Os modelos generativos offline são opcionais, com tamanho e compatibilidade informados antes do download.", "Gerenciar IA", onOpenAi)
        ExtensionCatalog.packs.forEach { pack ->
            val state = states[pack.id] ?: ExtensionPackState(totalBytes = pack.bytes)
            val working = state.status in setOf(ExtensionPackStatus.DOWNLOADING, ExtensionPackStatus.VERIFYING)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pack.title, style = MaterialTheme.typography.titleMedium)
                    Text(pack.description)
                    Text("Pacote: ${megabytes(pack.bytes)} MB · cópia local: ${megabytes(state.installedBytes)} MB")
                    Text(when (state.status) {
                        ExtensionPackStatus.CHECKING -> "Verificando dados locais…"
                        ExtensionPackStatus.ABSENT -> "Não instalado"
                        ExtensionPackStatus.DOWNLOADING -> "Baixando com sua autorização…"
                        ExtensionPackStatus.VERIFYING -> "Conferindo integridade SHA-256…"
                        ExtensionPackStatus.READY -> "Instalado e verificado"
                        ExtensionPackStatus.BUNDLED -> "Disponível nos assets deste APK"
                        ExtensionPackStatus.PAUSED -> "Download pausado; pode continuar"
                        ExtensionPackStatus.FAILED -> "Falha; confira a conexão e tente novamente"
                    })
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (working) {
                        LinearProgressIndicator(progress = { (state.downloadedBytes.toDouble() / pack.bytes).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${megabytes(state.downloadedBytes)} / ${megabytes(pack.bytes)} MB", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { repository.cancel(pack.id) }) { Text("Pausar download") }
                    } else {
                        if (state.status !in setOf(ExtensionPackStatus.READY, ExtensionPackStatus.BUNDLED, ExtensionPackStatus.CHECKING)) {
                            Button(onClick = { pending = pack.id to false }) { Text("Baixar pacote") }
                        }
                        if (state.installedBytes > 0 || state.status == ExtensionPackStatus.PAUSED) {
                            TextButton(onClick = { pending = pack.id to true }) { Text("Remover dados deste pacote") }
                        }
                        if (state.status == ExtensionPackStatus.BUNDLED) Text("Os assets de um APK completo não podem ser removidos separadamente. Apagar a cópia preparada preserva os assets do APK.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        OutlinedButton(onClick = onOpenVision) { Text("Abrir NEXUS Vision") }
        ModuleCard("NEXUS 3D", "Código integrado, sem download adicional. Usa o rastreador de mãos já presente no CORE; não é um plugin remoto.", "Abrir 3D", onOpenThreeD)
        ModuleCard("NEXUS Libras", "Treino integrado e experimental; compartilha o rastreador do CORE. Não certifica o alfabeto completo.", "Abrir Libras", onOpenLibras)
        ModuleCard("NEXUS Voice", "Integração opcional com voz do Android. Dados de voz e reconhecimento dependem do mecanismo instalado, que pode utilizar rede.", "Abrir voz", onOpenVoice)
        OutlinedButton(onClick = onOpenStorage) { Text("Central de armazenamento") }
        OutlinedButton(onClick = { scope.launch { runCatching { repository.refresh() }.onFailure { message = it.message } } }) { Text("Atualizar pacotes locais") }
    }
    pending?.let { (id, remove) ->
        val pack = ExtensionCatalog.pack(id)
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(if (remove) "Remover pacote?" else "Baixar pacote opcional?") },
            text = { Text(if (remove) "Somente os arquivos locais de ${pack.title} serão removidos. Encerre análises em uso antes de remover. O mouse, conversas e configurações serão preservados."
                else "${pack.title}: até ${megabytes(pack.bytes)} MB dos servidores oficiais Google/Tesseract. Usa internet somente para obter esses dados; as imagens não são enviadas por este download. Os arquivos passam por verificação de tamanho e SHA-256.") },
            confirmButton = { TextButton(onClick = {
                pending = null
                scope.launch {
                    try {
                        if (remove) repository.remove(id) else repository.download(id)
                        message = if (remove) "Dados do pacote removidos." else "Pacote instalado e verificado."
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (failure: Exception) { message = failure.message ?: "Não foi possível concluir a operação." }
                }
            }) { Text(if (remove) "Remover" else "Baixar") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar") } })
    }
}

/** Keeps optional cameras from starting until their model data exists. */
@Composable
fun ExtensionGate(required: Set<ExtensionPackId>, onManageExtensions: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) { ExtensionRepository.get(context) }
    val states by repository.states.collectAsStateWithLifecycle()
    LaunchedEffect(repository) {
        if (required.any { repository.states.value[it]?.status == ExtensionPackStatus.CHECKING }) {
            runCatching { repository.refresh() }
        }
    }
    if (required.all { states[it]?.status in setOf(ExtensionPackStatus.READY, ExtensionPackStatus.BUNDLED) }) content()
    else Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Dados opcionais do NEXUS Vision", style = MaterialTheme.typography.titleMedium)
            Text("Para usar esta função, instale os pacotes de dados na Central de Extensões. A câmera não foi iniciada. O mouse offline continua disponível no CORE.")
            Button(onClick = onManageExtensions) { Text("Abrir Central de Extensões") }
        }
    }
}

@Composable private fun ModuleCard(title: String, description: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description)
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

private fun megabytes(bytes: Long): String = String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", bytes / 1_000_000.0)
