package br.com.nexus.assistant.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.nexus.assistant.BuildConfig
import br.com.nexus.assistant.NexusViewModel
import br.com.nexus.assistant.data.AiMode
import br.com.nexus.assistant.data.NexusSettings
import br.com.nexus.assistant.data.local.NexusNativeBridge
import br.com.nexus.assistant.data.local.LocalModelCatalog
import br.com.nexus.assistant.device.DeviceCapabilities
import br.com.nexus.assistant.device.DevicePolicy
import br.com.nexus.assistant.device.VoiceController
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private data class LocalCheck(val label: String, val result: String)

/** Checks require an explicit tap and do not activate camera, microphone or a server. */
@Composable
fun NexusDiagnosticsScreen(
    model: NexusViewModel,
    voice: VoiceController,
    settings: NexusSettings,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceState by voice.state.collectAsStateWithLifecycle()
    val voices by voice.voices.collectAsStateWithLifecycle()
    val localModel by model.localModelState.collectAsStateWithLifecycle()
    val policy by model.devicePolicy.collectAsStateWithLifecycle()
    val effectiveProfile by model.effectiveLocalProfile.collectAsStateWithLifecycle()
    val startupCapabilities by model.deviceCapabilities.collectAsStateWithLifecycle()
    val compatibleLocal = policy.localAiSupported && DevicePolicy.canRunProfile(startupCapabilities, effectiveProfile)
    // Reuse the app's snapshot instead of querying files and system memory on
    // the UI thread every time this screen is opened.
    var capabilities by remember { mutableStateOf(startupCapabilities) }
    var showDetails by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf(emptyList<LocalCheck>()) }
    var checking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Central de diagnóstico", style = MaterialTheme.typography.titleLarge)
        Text("Verifique os recursos do NEXUS. Esta tela não abre câmera ou microfone e não envia os dados do diagnóstico.")
        CheckRow(LocalCheck("Versão instalada", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${if (BuildConfig.DEBUG) "teste" else "distribuição"}"))
        CheckRow(LocalCheck("IA local", if (compatibleLocal) "Compatível com o perfil de processamento atual" else "Indisponível neste perfil; provedor personalizado opcional"))
        CheckRow(LocalCheck("Ajuste automático", policy.reason))
        CheckRow(LocalCheck("Rastreamento de mãos", if (policy.handTrackingSupported) "Pode ser testado com sua autorização" else "Indisponível para a câmera ou arquitetura informada pelo Android"))
        CheckRow(LocalCheck("Cubo 3D", if (policy.supportsGles2) "Recurso gráfico declarado pelo Android; precisa de teste" else "Recurso gráfico necessário indisponível"))
        CheckRow(LocalCheck("Permissão da câmera", permissionStatus(context, Manifest.permission.CAMERA)))
        CheckRow(LocalCheck("Permissão do microfone", permissionStatus(context, Manifest.permission.RECORD_AUDIO)))
        CheckRow(LocalCheck("Reconhecimento de fala", when {
            !capabilities.hasMicrophone -> "Microfone não informado pelo Android; entrada por texto disponível"
            !voiceState.available -> "Nenhum serviço disponível; entrada por texto disponível"
            else -> "Serviço do Android disponível; a escuta precisa ser testada"
        }))
        CheckRow(LocalCheck("Síntese de voz", if (voiceState.ttsReady) "Mecanismo inicializado" else "Ainda indisponível"))
        CheckRow(LocalCheck("Idioma ${settings.language}", if (voiceState.languageReady) "Voz disponível no mecanismo" else "Dados de voz ausentes ou mecanismo ainda não pronto"))
        CheckRow(LocalCheck("Vozes deste idioma", "${voices.count { !it.requiresNetwork }} locais · ${voices.count { it.requiresNetwork }} de rede"))
        CheckRow(LocalCheck("IA selecionada", if (settings.aiMode == AiMode.NEXUS_LOCAL) "NEXUS AI local" else "Provedor personalizado"))
        CheckRow(LocalCheck("Modelo local", when (localModel.status) {
            br.com.nexus.assistant.data.local.LocalModelStatus.READY -> "Preparado e verificado"
            br.com.nexus.assistant.data.local.LocalModelStatus.ABSENT -> if (BuildConfig.BUNDLED_MODEL) "Incluído no APK; prepare ao abrir o Chat" else "Ainda não preparado"
            br.com.nexus.assistant.data.local.LocalModelStatus.FAILED -> "Falha na preparação; veja Configurações"
            else -> "Preparação em andamento ou pausada; veja o Chat ou Configurações"
        }))
        CheckRow(LocalCheck("IA remota opcional", if (settings.endpoint.isNotBlank() && settings.model.isNotBlank()) "Configurada; exige internet e consentimento. Conexão não testada aqui" else "Pode ser configurada mesmo sem IA local; exige servidor real e internet"))
        OutlinedButton(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) "Ocultar detalhes locais" else "Mostrar detalhes locais")
        }
        if (showDetails) {
            Text("Informações fornecidas pelo Android deste aparelho, consultadas apenas aqui. O nome pode ser um código do fabricante ou da ROM. RAM e núcleos disponíveis ao app não são uma ficha técnica comercial. Não consultamos IMEI, número de série ou identificadores de publicidade.", style = MaterialTheme.typography.bodySmall)
            hardwareSnapshot(capabilities).forEach { CheckRow(it) }
            CheckRow(LocalCheck("Modelo de IA em uso", LocalModelCatalog.forProfile(effectiveProfile).displayName))
            CheckRow(LocalCheck("Processamento adaptativo", if (compatibleLocal) "${policy.inferenceThreads} threads · ${policy.contextTokens} tokens de contexto · até ${policy.maxOutputTokens} tokens por resposta" else "Não ativado neste perfil"))
            CheckRow(LocalCheck("Análise de câmera", "${policy.cameraWidth} × ${policy.cameraHeight} · intervalo mínimo ${policy.analysisIntervalMs} ms · até ${policy.maxHands} mão(s)"))
            Text("O perfil de processamento é calculado ao iniciar o NEXUS. O teste atualiza esta leitura de recursos, sem trocar o modelo durante uma conversa. RAM livre varia com outros aplicativos; os critérios não garantem fluidez em todo aparelho.", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = {
            checking = true
            checks = emptyList()
            voice.refreshLanguageVoices()
            scope.launch {
                try {
                    checks = runLocalChecks(context)
                    capabilities = withContext(Dispatchers.IO) { DeviceCapabilities.read(context) }
                } finally { checking = false }
            }
        }, enabled = !checking) { Text(if (checking) "Verificando bibliotecas…" else "Executar testes locais") }
        checks.forEach { CheckRow(it) }
        if (checks.isNotEmpty()) Text("Os tempos medem inicialização neste aparelho. Não medem velocidade da IA nem qualidade dos gestos. Abrir o modelo de mãos sem erro não comprova que uma mão foi reconhecida.", style = MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Testes com você presente", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = {
                    voice.speak("Olá! O NEXUS está falando no idioma que você escolheu.", settings.language,
                        settings.voiceRate, settings.voiceName, settings.voicePitch)
                }, enabled = voiceState.ttsReady && voiceState.languageReady) { Text("Reproduzir frase de teste") }
                if (voiceState.isSpeaking) OutlinedButton(onClick = voice::stopSpeaking) { Text("Parar reprodução") }
                if (voiceState.speechUsesNetwork) Text("A voz selecionada informa uso de rede pelo mecanismo de fala.", style = MaterialTheme.typography.bodySmall)
                voiceState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (capabilities.hasMicrophone && voiceState.available) OutlinedButton(onClick = { onNavigate("voice") }) { Text("Abrir teste de microfone e conversa") }
                if (capabilities.hasFrontCamera || capabilities.hasRearCamera) {
                    OutlinedButton(onClick = { onNavigate("vision") }) { Text("Abrir câmera e captura") }
                    if (policy.handTrackingSupported) OutlinedButton(onClick = { onNavigate("mouse") }) { Text("Abrir teste de mãos e cursor") }
                } else Text("Sem câmera disponível, use texto, voz compatível e os materiais visuais de Libras. Rastreamento de mãos e captura ficam desativados.", style = MaterialTheme.typography.bodySmall)
                if (policy.supportsGles2 && policy.handTrackingSupported) OutlinedButton(onClick = { onNavigate("cube") }) { Text("Abrir teste do cubo e duas mãos") }
                OutlinedButton(onClick = { onNavigate("chat") }) { Text("Abrir chat para testar inferência real") }
                Text("Use as telas para confirmar áudio compreensível, captura, pontos sobre a mão e resposta real da IA. Nenhum desses resultados será marcado automaticamente como aprovado.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CheckRow(check: LocalCheck) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(check.label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(check.result, Modifier.weight(1.3f))
    }
    HorizontalDivider()
}

private fun permissionStatus(context: Context, permission: String): String =
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) "Concedida" else "Não concedida"

private fun hardwareSnapshot(capabilities: DeviceCapabilities): List<LocalCheck> {
    val runtime = Runtime.getRuntime()
    val pageBytes = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(0L)
    return listOf(
        LocalCheck("Nome informado pelo Android", "${Build.MANUFACTURER} ${Build.MODEL}".trim()),
        LocalCheck("Versão informada pelo Android", "${Build.VERSION.RELEASE} · API ${capabilities.sdkInt}"),
        LocalCheck("Arquiteturas do aparelho", capabilities.supportedAbis.joinToString()),
        LocalCheck("Processo do aplicativo", if (capabilities.process64Bit) "64 bits" else "32 bits; IA local indisponível"),
        LocalCheck("Arquitetura do processo", capabilities.processAbi.ifBlank { "Não informada pelo Android" }),
        LocalCheck("Núcleos disponíveis ao app", capabilities.cpuCores.toString()),
        LocalCheck("Página de memória", if (pageBytes > 0L) "$pageBytes bytes" else "Não foi possível medir"),
        LocalCheck("RAM utilizável pelo sistema / livre na leitura", "${capabilities.totalRamBytes / 1_048_576} / ${capabilities.availableRamBytes / 1_048_576} MiB; pode diferir da RAM anunciada"),
        LocalCheck("Android sinaliza pouca RAM", if (capabilities.lowRam) "Sim" else "Não"),
        LocalCheck("Limite de heap Java", "${runtime.maxMemory() / 1_048_576} MiB; não inclui todo o uso nativo"),
        LocalCheck("Armazenamento privado livre", "${capabilities.freeDiskBytes / 1_048_576} MiB"),
        LocalCheck("Câmera frontal / traseira", "${if (capabilities.hasFrontCamera) "Disponível" else "Ausente"} / ${if (capabilities.hasRearCamera) "Disponível" else "Ausente"}"),
        LocalCheck("Microfone informado pelo Android", if (capabilities.hasMicrophone) "Disponível; gravação ainda precisa de teste" else "Ausente"),
        LocalCheck("OpenGL ES declarado", "${capabilities.glesVersion ushr 16}.${capabilities.glesVersion and 0xffff}")
    )
}

private suspend fun runLocalChecks(context: Context): List<LocalCheck> = withContext(Dispatchers.IO) {
    val results = mutableListOf<LocalCheck>()
    val started = SystemClock.elapsedRealtime()
    val nativeReady = NexusNativeBridge.available
    results += LocalCheck("Biblioteca de IA via JNI", when {
        nativeReady -> "Carregada nesta arquitetura · ${SystemClock.elapsedRealtime() - started} ms"
        !NexusNativeBridge.supports64BitProcess -> "IA local indisponível em processo de 32 bits; as demais funções compatíveis continuam disponíveis"
        else -> "Falha ao carregar a biblioteca de 64 bits; confira se instalou o APK compatível"
    })
    coroutineContext.ensureActive()
    if (!DevicePolicy.detect(context).handTrackingSupported) {
        results += LocalCheck("MediaPipe no aparelho", "Rastreamento não disponível para esta câmera ou arquitetura. Nenhuma câmera foi aberta.")
        return@withContext results
    }
    val handsStart = SystemClock.elapsedRealtime()
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open("hand_landmarker.task").use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val checksum = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        check(checksum == "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1")
        results += LocalCheck("Integridade do modelo de mãos", "SHA-256 corresponde ao modelo fixado no projeto")
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE).setNumHands(2).build()
        val tracker = HandLandmarker.createFromOptions(context, options)
        try {
            coroutineContext.ensureActive()
            results += LocalCheck("MediaPipe no aparelho", "Rastreador inicializado e encerrado · ${SystemClock.elapsedRealtime() - handsStart} ms")
        } finally { tracker.close() }
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (_: Exception) {
        results += LocalCheck("MediaPipe no aparelho", "Falha ao verificar ou inicializar o modelo; a câmera não foi aberta")
    } catch (_: LinkageError) {
        results += LocalCheck("MediaPipe no aparelho", "Biblioteca nativa incompatível com este aparelho")
    } catch (_: OutOfMemoryError) {
        results += LocalCheck("MediaPipe no aparelho", "RAM insuficiente para inicializar o rastreador; feche outros aplicativos e tente novamente")
    }
    results
}
