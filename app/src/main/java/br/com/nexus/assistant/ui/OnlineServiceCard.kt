package br.com.nexus.assistant.ui

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.nexus.assistant.NexusViewModel
import br.com.nexus.assistant.data.NexusSettings

/** Account connection for ordinary users: public service address belongs to the build. */
@Composable
internal fun OnlineServiceCard(model: NexusViewModel, settings: NexusSettings) {
    val connection by model.onlineConnection.collectAsStateWithLifecycle()
    // A one-time invitation is a credential: keep it out of saved instance state.
    var invitation by remember { mutableStateOf("") }
    var consent by remember(settings.onlineConsent) { mutableStateOf(settings.onlineConsent) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("NEXUS AI", style = MaterialTheme.typography.titleLarge)
            Text(connection.message)
            Text("Assistente online integrado. A personalidade, o nome pelo qual você é chamado e o histórico continuam nas configurações do NEXUS. Os modelos offline são opcionais.", style = MaterialTheme.typography.bodySmall)
            if (connection.serviceConfigured) {
                Text("Ao conversar neste modo, mensagens, histórico selecionado, preferências e memórias autorizadas são enviados ao serviço NEXUS e ao provedor de IA operado pelo desenvolvedor. Fotos só são enviadas quando anexadas ou capturadas em uma sessão Vision autorizada. Isso pode consumir dados móveis. Não é acesso contínuo à câmera ou ao conteúdo de outros aplicativos.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = {
                        consent = it
                        // Revocation takes effect immediately, including an active generation.
                        model.saveSettings(settings.copy(onlineConsent = it))
                    }, enabled = !connection.working)
                    Text("Li e autorizo o envio ao NEXUS AI", Modifier.weight(1f))
                }
                if (!connection.connected) {
                    OutlinedTextField(invitation, { invitation = it.take(200) },
                        label = { Text("Convite da sua conta NEXUS") },
                        supportingText = { Text("Código de uso único fornecido pelo responsável. Não é uma chave API; nenhuma URL precisa ser digitada.") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        enabled = !connection.working, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val code = invitation
                        invitation = ""
                        model.connectOnline(code, consent)
                    }, enabled = consent && invitation.isNotBlank() && !connection.working, modifier = Modifier.fillMaxWidth()) {
                        Text(if (connection.working) "Conectando…" else "Conectar NEXUS AI")
                    }
                }
            } else {
                Text("O serviço NEXUS AI ainda não está disponível nesta versão. Você pode usar as ferramentas locais ou escolher um modelo offline opcional.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = {
                invitation = ""
                model.disconnectOnline()
            }, enabled = !connection.working, modifier = Modifier.fillMaxWidth()) { Text("Desconectar e apagar sessão deste aparelho") }
            Text("Sua sessão fica protegida neste aparelho. Você pode desconectar e apagar o acesso a qualquer momento.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
