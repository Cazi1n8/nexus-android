package br.com.nexus.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import br.com.nexus.assistant.device.VoiceController
import br.com.nexus.assistant.ui.NexusApp
import br.com.nexus.assistant.ui.NexusTheme

class MainActivity : ComponentActivity() {
    private val model: NexusViewModel by viewModels()
    private lateinit var voice: VoiceController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        voice = VoiceController(this)
        setContent { NexusTheme { NexusApp(model, voice) } }
    }
    override fun onStop() {
        if (::voice.isInitialized) { voice.stopListening(); voice.stopSpeaking() }
        super.onStop()
    }
    override fun onDestroy() {
        if (::voice.isInitialized) voice.close()
        super.onDestroy()
    }
}
