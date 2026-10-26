package br.com.nexus.assistant.device.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.nexus.assistant.NexusViewModel

/** The microphone is requested/started only after a clear user action and disclosure. */
@Composable
fun BackgroundVoicePanel(model: NexusViewModel, onBeforeStart: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by VoiceSessionRuntime.state.collectAsStateWithLifecycle()
    var consent by rememberSaveable { mutableStateOf(false) }
    var continuous by rememberSaveable { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    fun begin() {
        if (!consent || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        onBeforeStart()
        permissionError = null
        VoiceSessionRuntime.start(context, continuous)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.isNotEmpty() && grants.values.all { it }) begin()
        else permissionError = "Microfone e notificações são necessários para esta sessão explícita."
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Voz fora do aplicativo", style = MaterialTheme.typography.titleMedium)
            Text("Sessão iniciada por você, com notificação OUVIR/PARAR. Usa a mesma conversa e a mesma IA do Chat; não carrega um segundo modelo. O reconhecedor e a voz do Android podem usar a rede.")
            Text("O modo básico ouve uma pergunta e espera outro toque em OUVIR. A opção contínua alterna escuta, resposta e voz por até 15 minutos ou 30 perguntas. Silêncio tem no máximo duas novas tentativas; depois aguarda você. Não há detector permanente de “Ei, NEXUS”.", style = MaterialTheme.typography.bodySmall)
            if (!state.active) {
                Row {
                    Checkbox(continuous, { continuous = it })
                    Text("Continuar os turnos de conversa nesta sessão", Modifier.padding(top = 12.dp))
                }
                Row {
                    Checkbox(consent, { consent = it })
                    Text("Autorizo o microfone nesta sessão, inclusive fora do NEXUS, e o uso do serviço de fala do Android.", Modifier.padding(top = 12.dp))
                }
                Button(onClick = {
                    val required = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) required += Manifest.permission.POST_NOTIFICATIONS
                    val missing = required.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                    if (missing.isEmpty()) begin() else permissions.launch(missing.toTypedArray())
                }, enabled = consent) { Text("Iniciar sessão de voz") }
            } else {
                Text(state.message)
                Text("${state.completedTurns} pergunta(s) nesta sessão", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { VoiceSessionRuntime.listen(context) },
                        enabled = state.phase in setOf(VoiceSessionPhase.READY, VoiceSessionPhase.PAUSED)) { Text("OUVIR") }
                    Button(onClick = { VoiceSessionRuntime.stop(context) }) { Text("PARAR voz e microfone") }
                }
                if (state.phase == VoiceSessionPhase.SPEAKING || state.phase == VoiceSessionPhase.THINKING) {
                    OutlinedButton(onClick = { VoiceSessionRuntime.interrupt(context) }) { Text("Interromper resposta") }
                }
            }
            if (!state.active) Text(state.message, style = MaterialTheme.typography.bodySmall)
            permissionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                    .onFailure { model.report("Abra Configurações do Android → Aplicativos → NEXUS para conferir permissões e notificações.") }
            }) { Text("Conferir permissões e notificações") }
            Text("O Android pode interromper a sessão por memória, bateria, permissões ou indisponibilidade do serviço de fala. Ela não reinicia sozinha nem inicia no boot. Ações propostas pela IA continuam exigindo confirmação no aplicativo.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
