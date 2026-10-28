package br.com.nexus.assistant.gestures.global

import android.Manifest
import android.content.Intent
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.nexus.assistant.device.DeviceCapabilities
import br.com.nexus.assistant.device.DevicePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Starting a session always originates from an explicit tap in this visible screen. */
@Composable
fun GlobalMousePanel(onBeforeStart: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val observedCapabilities by produceState<DeviceCapabilities?>(null, context) {
        value = withContext(Dispatchers.IO) { DeviceCapabilities.read(context) }
    }
    val capabilities = observedCapabilities
    if (capabilities == null) {
        Text("Verificando os recursos do aparelho para o mouse global…")
        return
    }
    val policy = remember(capabilities) { DevicePolicy.evaluate(capabilities) }
    val state by GlobalMouseRuntime.state.collectAsState()
    val connected by NexusAccessibilityService.connected.collectAsState()
    val scope = rememberCoroutineScope()
    var consent by rememberSaveable { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    val gesturePreferences = remember(context) { context.getSharedPreferences("nexus_gestures", android.content.Context.MODE_PRIVATE) }
    var mirror by remember(capabilities.hasFrontCamera) { mutableStateOf(gesturePreferences.getBoolean("global_mirror", capabilities.hasFrontCamera)) }

    fun begin() {
        if (!consent || starting || state.active || !policy.handTrackingSupported) return
        if (!NexusAccessibilityService.connected.value) {
            GlobalMouseRuntime.fail("Ative o serviço NEXUS nas configurações de acessibilidade antes de iniciar.")
            return
        }
        starting = true
        val stopGeneration = GlobalMouseRuntime.stopGeneration
        onBeforeStart()
        scope.launch {
            // Let the Compose camera host unbind before acquiring the camera in the service.
            delay(350)
            try {
                if (stopGeneration != GlobalMouseRuntime.stopGeneration) return@launch
                if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    GlobalMouseRuntime.fail("Abra esta tela para iniciar uma sessão de câmera visível.")
                    return@launch
                }
                ContextCompat.startForegroundService(context, Intent(context, GlobalMouseCameraService::class.java)
                    .setAction(GlobalMouseCameraService.ACTION_START)
                    .putExtra(GlobalMouseCameraService.EXTRA_START_GENERATION, stopGeneration))
            } catch (_: Exception) {
                GlobalMouseRuntime.fail("O Android não permitiu iniciar a câmera em primeiro plano. Volte a esta tela e tente novamente.")
            } finally {
                starting = false
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.isNotEmpty() && grants.values.all { it }) begin()
        else GlobalMouseRuntime.fail("Câmera e notificações são necessárias para manter o controle global visível.")
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mouse global · usar fora do NEXUS", style = MaterialTheme.typography.titleMedium)
            Text("Usa a câmera ${if (capabilities.hasFrontCamera) "frontal" else "traseira disponível"} para mover o cursor e o serviço de acessibilidade para executar toques e arrastos em outros apps. Não lê o conteúdo das telas, não grava imagens e não envia quadros para servidores.")
            if (!policy.handTrackingSupported) Text(if (!capabilities.hasCamera) "Mouse global indisponível: este aparelho não informa uma câmera."
                else "Mouse global indisponível: o rastreador de mãos não é compatível com esta arquitetura.", color = MaterialTheme.colorScheme.error)
            Text("A prévia da câmera não aparece sobre os outros apps. A notificação, o botão PARAR e os indicadores de privacidade do Android continuam visíveis. A sessão continua ao sair do NEXUS; o sistema pode interrompê-la por bateria, memória ou uso da câmera por outro app. Recursos protegidos podem recusar gestos.", style = MaterialTheme.typography.bodySmall)
            Row {
                Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !state.active && !starting && policy.handTrackingSupported)
                Text("Autorizo esta sessão de câmera e controle por gestos em outros aplicativos.", modifier = Modifier.padding(top = 8.dp))
            }
            Text("1. Leia e autorize a sessão acima. 2. Ative a acessibilidade do NEXUS. 3. Toque em Ativar mouse global e aguarde a câmera fornecer quadros. Depois você pode abrir outro app.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, enabled = consent && !state.active) {
                Text(if (connected) "Acessibilidade NEXUS conectada" else "Abrir acessibilidade do Android")
            }
            Text("Acessibilidade: ${if (connected) "conectada" else "desconectada"} · câmera: ${if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) "autorizada" else "a solicitar ao ativar"}", style = MaterialTheme.typography.bodySmall)
            Row {
                Checkbox(mirror, {
                    mirror = it
                    gesturePreferences.edit().putBoolean("global_mirror", it).apply()
                })
                Text("Espelhar movimento horizontal", modifier = Modifier.padding(top = 8.dp))
            }
            if (!context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Autorizar notificações de controle") }
            }
            if (state.active) {
                Text(when {
                    state.paused -> "Mouse global pausado · câmera liberada"
                    state.cameraReady -> "Câmera global ativa · ${state.hands} mão(s) · processamento ${state.processingMs} ms"
                    else -> "Preparando mouse global…"
                })
                Text("Câmera: ${state.cameraStatus}", style = MaterialTheme.typography.bodySmall)
                if (state.cameraReady) Text("Idade do resultado na entrega: ${state.frameAgeMs} ms. Gestos aceitos pelo Android: ${state.acceptedGestures}; recusados: ${state.rejectedGestures}; cancelados: ${state.cancelledGestures}.", style = MaterialTheme.typography.bodySmall)
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (state.paused) {
                    Button(onClick = {
                        GlobalMouseRuntime.resumeFromVisiblePanel(context, owner.lifecycle)
                    }) { Text("Retomar mouse global nesta tela") }
                } else {
                    OutlinedButton(onClick = { GlobalMouseRuntime.pause(context) }) { Text("Pausar e liberar câmera") }
                    OutlinedButton(onClick = { GlobalMouseRuntime.calibrate(context) }, enabled = state.cameraReady) { Text("Centralizar cursor pela mão") }
                }
                Button(onClick = { GlobalMouseRuntime.stop(context) }) { Text("PARAR câmera e mouse global") }
            } else {
                Button(onClick = {
                    val required = mutableListOf(Manifest.permission.CAMERA)
                    if (Build.VERSION.SDK_INT >= 33) required += Manifest.permission.POST_NOTIFICATIONS
                    val missing = required.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                    if (missing.isEmpty()) begin() else permission.launch(missing.toTypedArray())
                }, enabled = consent && connected && !starting && policy.handTrackingSupported) { Text(if (starting) "Iniciando…" else "Ativar mouse global") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Indicador: mover • pinça curta: toque • pinça mantida: arrastar • dois dedos: rolar. O segundo toque de duas pinças rápidas forma o duplo toque.", style = MaterialTheme.typography.bodySmall)
            Text("Este modo é diferente do mouse interno. Em aparelhos com restrições de câmera em segundo plano, o estado acima informa a indisponibilidade; volte ao NEXUS ou encerre a sessão. O botão PARAR fica na notificação e sobre os outros aplicativos.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
