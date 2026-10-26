package br.com.nexus.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.nexus.assistant.device.voice.SharedNexusModel
import br.com.nexus.assistant.device.voice.VoiceSessionRuntime
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.nexus.assistant.device.VoiceController
import br.com.nexus.assistant.ui.NexusApp
import br.com.nexus.assistant.ui.NexusTheme

class MainActivity : ComponentActivity() {
    internal val model: NexusViewModel get() = SharedNexusModel.get(application)
    private lateinit var voice: VoiceController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        voice = VoiceController(this)
        setContent {
            val capabilities by model.deviceCapabilities.collectAsStateWithLifecycle()
            val reducedEffects = capabilities.lowRam || capabilities.totalRamBytes < 4L * 1024 * 1024 * 1024 || capabilities.cpuCores <= 2
            NexusTheme(reducedEffects = reducedEffects) { NexusApp(model, voice) }
        }
    }
    override fun onStart() {
        super.onStart()
        SharedNexusModel.onUiStarted()
    }
    override fun onResume() {
        super.onResume()
        if (::voice.isInitialized) voice.refreshLanguageVoices()
    }
    override fun onStop() {
        if (::voice.isInitialized) { voice.stopListening(); voice.stopSpeaking() }
        SharedNexusModel.onUiStopped()
        if (!VoiceSessionRuntime.state.value.active) model.onAppBackgrounded()
        super.onStop()
    }
    override fun onDestroy() {
        if (::voice.isInitialized) voice.close()
        super.onDestroy()
    }
}
