package br.com.nexus.assistant.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** No file access occurs while composing; all measurements and cleanup run through suspend APIs. */
@Composable
fun StorageCenterScreen(
    onManageModels: () -> Unit,
    onManageExtensions: () -> Unit,
    onManageData: () -> Unit,
    onClearCache: suspend () -> String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) { StorageRepository(context) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<StorageSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var cleaning by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refresh) {
        loading = true
        try { snapshot = repository.measure()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { notice = "Não foi possível concluir a medição. Tente atualizar novamente." }
        finally { loading = false }
    }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Central de armazenamento", style = MaterialTheme.typography.headlineSmall)
        Text("Medição local deste aplicativo. Não lemos nem enviamos conteúdo de conversas, fotos ou arquivos de outros apps.")
        if (loading || cleaning) CircularProgressIndicator()
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        snapshot?.let { state ->
            state.system?.let { system ->
                StoragePanel("Valores informados pelo Android") {
                    SizeLine("Aplicativo", system.appBytes)
                    SizeLine("Dados sem cache", system.dataBytesExcludingCache)
                    SizeLine("Cache", system.cacheBytes)
                    SizeLine("Total", system.totalBytes)
                    Text("O Android contabiliza o cache dentro dos dados. Ele foi separado acima e não foi somado duas vezes.", style = MaterialTheme.typography.bodySmall)
                }
            } ?: Text("O Android não forneceu os totais do sistema nesta consulta. A medição por arquivos abaixo continua disponível; não solicitamos permissão para observar outros aplicativos.", style = MaterialTheme.typography.bodySmall)
            StoragePanel("Arquivos da instalação") {
                SizeLine("APK e pacotes de instalação", state.apkBytes)
                Text("${state.apkFiles} arquivo(s) de instalação. As bibliotecas embutidas já fazem parte desse valor.", style = MaterialTheme.typography.bodySmall)
                SizeLine("Bibliotecas nativas extraídas pelo Android", state.extractedNativeBytes)
                Text("Este detalhamento não é somado aos totais do Android. Otimizações do sistema e blocos do disco podem produzir valores diferentes do tamanho lógico dos arquivos.", style = MaterialTheme.typography.bodySmall)
                if (state.bundledGenerativeModelBytes > 0) {
                    SizeLine("Modelos generativos dentro do APK", state.bundledGenerativeModelBytes)
                    Text("Esse valor é parte do APK, não um dado extra. Só uma atualização com instalação menor remove os pesos que vieram embutidos; limpar cache não altera o APK.", style = MaterialTheme.typography.bodySmall)
                }
            }
            StoragePanel("Arquivos privados medidos") {
                state.privateFiles.buckets.forEach { bucket ->
                    SizeLine(bucket.category.title, bucket.bytes)
                    Text("${bucket.files} arquivo(s). ${bucket.category.explanation}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                }
                SizeLine("Total lógico dos arquivos privados", state.privateFiles.totalBytes)
                Text("Atalhos para outras pastas são ignorados. Nomes que apontam para o mesmo arquivo físico são contados uma vez. Esta medição não substitui a contabilização de espaço do Android.", style = MaterialTheme.typography.bodySmall)
                val unreadable = state.privateFiles.unreadableItems + state.packageReadErrors
                if (unreadable > 0) Text("Medição parcial: $unreadable item(ns) indisponível(is). Atualize após concluir operações de arquivos.", style = MaterialTheme.typography.bodySmall)
                if (state.privateFiles.skippedSymlinks > 0) Text("${state.privateFiles.skippedSymlinks} link(s) ignorado(s) para preservar os limites desta medição.", style = MaterialTheme.typography.bodySmall)
            }
            SizeLine("Espaço livre no volume de dados", state.freeDataBytes)
            Text("Atualizado às ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.measuredAtMillis))}. Valores podem variar durante downloads e uso do app.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { refresh++ }, enabled = !loading && !cleaning, modifier = Modifier.fillMaxWidth()) { Text("Atualizar medição") }
        Button(onClick = onManageModels, enabled = !cleaning, modifier = Modifier.fillMaxWidth()) { Text("Gerenciar modelos de IA") }
        OutlinedButton(onClick = onManageExtensions, enabled = !cleaning, modifier = Modifier.fillMaxWidth()) { Text("Gerenciar extensões e pacotes") }
        OutlinedButton(onClick = onManageData, enabled = !cleaning, modifier = Modifier.fillMaxWidth()) { Text("Gerenciar conversas e memórias") }
        OutlinedButton(onClick = { confirmClear = true }, enabled = !loading && !cleaning && (snapshot?.privateFiles?.bytes(StorageCategory.CACHE) ?: 0) > 0,
            modifier = Modifier.fillMaxWidth()) { Text("Limpar cache temporário") }
        Text("A limpeza acima não apaga modelos, extensões, conversas, memórias, configurações ou cache de código. Modelos e pacotes são removidos nos controles próprios. Arquivos já instalados em seus dados são preservados ao atualizar o NEXUS.", style = MaterialTheme.typography.bodySmall)
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Limpar arquivos temporários?") },
        text = { Text("As importações temporárias serão interrompidas antes da limpeza. Modelos de IA, pacotes e conversas serão preservados. O espaço realmente disponível será medido novamente depois.") },
        confirmButton = { TextButton(onClick = {
            confirmClear = false
            cleaning = true
            scope.launch {
                try { notice = onClearCache()
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { notice = "Não foi possível concluir a limpeza. Seus dados permanentes foram preservados." }
                finally { cleaning = false; refresh++ }
            }
        }) { Text("Limpar temporários") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun StoragePanel(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF101D29))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SizeLine(label: String, bytes: Long) {
    Text("$label: ${formatStorageBytes(bytes)}", style = MaterialTheme.typography.bodyMedium)
    Text("$bytes bytes", style = MaterialTheme.typography.labelSmall)
}

fun formatStorageBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.forLanguageTag("pt-BR"), "%.2f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> String.format(Locale.forLanguageTag("pt-BR"), "%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000L -> String.format(Locale.forLanguageTag("pt-BR"), "%.1f kB", bytes / 1_000.0)
    else -> "$bytes B"
}
