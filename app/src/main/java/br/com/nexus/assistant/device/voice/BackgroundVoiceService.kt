package br.com.nexus.assistant.device.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import br.com.nexus.assistant.MainActivity
import br.com.nexus.assistant.NexusViewModel
import br.com.nexus.assistant.R
import br.com.nexus.assistant.commands.PhoneCommands
import br.com.nexus.assistant.device.VoiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** User-started microphone FGS with a visible STOP action and no silent restart.
 * Shares the app's model; speech recognition never runs while TTS/model is busy.
 */
class BackgroundVoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var voice: VoiceController
    private lateinit var model: NexusViewModel
    private lateinit var budget: VoiceConversationBudget
    private var started = false
    private var continuous = false
    private var completedTurns = 0
    private var listenRequested = false
    private var awaitingQuery: String? = null
    private var awaitingSince = 0L
    private var speakingReply = false
    private var resumeJob: Job? = null
    private var missingReplyJob: Job? = null
    private var terminalMessage = "Sessão de voz encerrada"
    private var lastNotificationMessage = ""
    private var failed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == NexusSessionControls.ACTION_STOP_ALL) {
            NexusSessionControls.stopAll(this)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            VoiceSessionRuntime.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null || VoiceSessionRuntime.stopRequested) { stopSelf(); return START_NOT_STICKY }
        if (started) {
            if (intent.action == ACTION_LISTEN) requestListen(manual = true)
            if (intent.action == ACTION_INTERRUPT) interruptTurn()
            return START_NOT_STICKY
        }
        if (intent.action != ACTION_START) { stopSelf(); return START_NOT_STICKY }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            failStart("Autorize o microfone antes de iniciar a sessão de voz."); return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            failStart("Autorize notificações para manter a voz e o botão PARAR visíveis."); return START_NOT_STICKY
        }
        if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            failStart("As notificações do NEXUS precisam estar ativas para esta sessão."); return START_NOT_STICKY
        }
        try {
            continuous = intent.getBooleanExtra(EXTRA_CONTINUOUS, false)
            createChannel()
            val notification = notification("Preparando a sessão de voz")
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIFICATION, notification)
            started = true
            model = SharedNexusModel.get(this)
            voice = VoiceController(this, backgroundSessionOwner = true)
            budget = VoiceConversationBudget(SystemClock.elapsedRealtime())
            publish(VoiceSessionPhase.PREPARING, "Preparando voz e IA; microfone iniciado somente quando pronto")
            observeSession()
            scope.launch {
                delay(budget.durationMs)
                terminalMessage = "Limite de 15 minutos atingido. Abra o NEXUS para iniciar outra sessão."
                stopSelf()
            }
            requestListen(manual = true)
        } catch (_: Exception) {
            failStart("O Android não permitiu a sessão de microfone. Inicie pelo NEXUS visível e confira as permissões.")
        }
        return START_NOT_STICKY
    }

    private fun observeSession() {
        scope.launch { model.settings.collect {
            voice.selectVoice(it.voiceName)
            voice.setLanguage(it.language)
        } }
        scope.launch {
            voice.state.collect { state ->
                if (VoiceSessionRuntime.stopRequested) return@collect
                when {
                    state.isListening -> publish(VoiceSessionPhase.LISTENING, "Ouvindo nesta sessão autorizada")
                    state.isSpeaking -> publish(VoiceSessionPhase.SPEAKING, "NEXUS falando; microfone pausado")
                    state.preparingSpeech -> publish(VoiceSessionPhase.PREPARING, "Preparando a voz do Android")
                    speakingReply && state.error != null -> {
                        speakingReply = false
                        listenRequested = false
                        publish(VoiceSessionPhase.PAUSED, state.error)
                    }
                    listenRequested -> tryListen()
                }
            }
        }
        scope.launch {
            combine(model.messages, model.busy) { messages, busy -> messages to busy }.collect { (messages, busy) ->
                if (VoiceSessionRuntime.stopRequested) return@collect
                val query = awaitingQuery
                if (query == null) {
                    if (!busy && listenRequested) tryListen()
                    return@collect
                }
                val userIndex = messages.indexOfLast { it.role == "user" && it.content == query && it.createdAt >= awaitingSince }
                val reply = if (userIndex >= 0) messages.drop(userIndex + 1).firstOrNull { it.role == "assistant" } else null
                if (reply != null) {
                    missingReplyJob?.cancel()
                    awaitingQuery = null
                    speakingReply = true
                    val settings = model.settings.value
                    voice.speak(reply.content, settings.language, settings.voiceRate, settings.voiceName, settings.voicePitch) {
                        speakingReply = false
                        if (budget.expired(SystemClock.elapsedRealtime())) {
                            terminalMessage = "Limite desta sessão atingido. Inicie outra sessão pelo NEXUS."
                            stopSelf()
                        } else if (continuous) {
                            resumeJob?.cancel()
                            resumeJob = scope.launch { delay(900); resumeJob = null; requestListen(manual = false) }
                        } else publish(VoiceSessionPhase.READY, "Resposta concluída. Toque em OUVIR para falar novamente.")
                    }
                } else if (!busy) {
                    // Room observers can publish just after the job clears busy.
                    missingReplyJob?.cancel()
                    missingReplyJob = scope.launch {
                        delay(1_000)
                        if (awaitingQuery == query && !model.busy.value) {
                            awaitingQuery = null
                            publish(VoiceSessionPhase.PAUSED, "A resposta foi interrompida. Toque em OUVIR para uma nova pergunta.")
                        }
                    }
                }
            }
        }
        scope.launch {
            model.error.collect { error ->
                if (error != null && awaitingQuery != null) {
                    awaitingQuery = null
                    publish(VoiceSessionPhase.PAUSED, error)
                }
            }
        }
    }

    private fun requestListen(manual: Boolean) {
        if (!started || VoiceSessionRuntime.stopRequested) return
        if (budget.expired(SystemClock.elapsedRealtime())) {
            terminalMessage = "Limite desta sessão atingido. Inicie outra sessão pelo NEXUS."
            stopSelf(); return
        }
        // An explicit OUVIR action can interrupt this service's current response.
        // Never cancel a generation started elsewhere in the Chat.
        if (manual && (awaitingQuery != null || speakingReply)) interruptTurn()
        if (model.busy.value || awaitingQuery != null || speakingReply) {
            if (awaitingQuery == null && !speakingReply) listenRequested = true
            publish(VoiceSessionPhase.THINKING, "Aguarde a resposta ou toque em PARAR para encerrar.")
            return
        }
        resumeJob?.cancel()
        if (manual) {
            budget.manualListen()
            voice.refreshLanguageVoices()
        }
        listenRequested = true
        tryListen()
    }

    private fun interruptTurn() {
        if (!started) return
        resumeJob?.cancel(); resumeJob = null
        missingReplyJob?.cancel(); missingReplyJob = null
        listenRequested = false
        val ownsGeneration = awaitingQuery != null
        awaitingQuery = null
        speakingReply = false
        if (ownsGeneration) model.cancelResponse()
        voice.stopListening()
        voice.stopSpeaking()
        publish(VoiceSessionPhase.READY, "Resposta interrompida. Toque em OUVIR para fazer outra pergunta.")
    }

    private fun tryListen() {
        if (!started || VoiceSessionRuntime.stopRequested || !listenRequested || model.busy.value || awaitingQuery != null || speakingReply) return
        val state = voice.state.value
        if (state.isListening || state.isSpeaking || state.preparingSpeech) return
        if (!state.available) {
            listenRequested = false
            publish(VoiceSessionPhase.PAUSED, "Não há reconhecedor de fala instalado. Use o Chat e instale um serviço de voz do Android.")
            return
        }
        if (!state.ttsReady || !state.languageReady) {
            if (state.ttsReady && !state.languageReady) {
                listenRequested = false
                publish(VoiceSessionPhase.PAUSED, "Instale uma voz no idioma escolhido em Configurações do Android antes de conversar por áudio.")
            } else if (state.error != null) {
                listenRequested = false
                publish(VoiceSessionPhase.PAUSED, state.error)
            } else publish(VoiceSessionPhase.PREPARING, "Preparando uma voz no idioma escolhido; confira as vozes instaladas no Android.")
            return
        }
        // Recognition itself needs neither a downloaded LLM nor a configured
        // server. The shared ViewModel validates each recognized request: local
        // tools can work in CORE; general chat reports its real missing model.
        listenRequested = false
        voice.clearError()
        voice.startListening(model.settings.value.language, onResult = { heard ->
            val query = PhoneCommands.removeWakePhrase(heard)
            if (query.isBlank()) {
                publish(VoiceSessionPhase.READY, "Diga a pergunta completa. Esta sessão não é um detector permanente de palavra-chave.")
            } else {
                awaitingSince = System.currentTimeMillis()
                awaitingQuery = query
                if (model.send(query)) {
                    budget.acceptedTurn(); completedTurns++
                    publish(VoiceSessionPhase.THINKING, "Processando a pergunta; microfone pausado")
                } else {
                    awaitingQuery = null
                    publish(VoiceSessionPhase.PAUSED, model.error.value ?: "A pergunta não foi aceita. Confira a IA no aplicativo.")
                }
            }
        }, onEnd = {
            if (awaitingQuery == null && !speakingReply) {
                val error = voice.state.value.error
                if (error != null) {
                    val code = voice.state.value.lastRecognitionErrorCode
                    val wait = budget.nextSilenceRetry(SystemClock.elapsedRealtime(), continuous,
                        code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    if (wait != null) {
                        publish(VoiceSessionPhase.PAUSED, "Sem fala reconhecida. Nova tentativa em ${wait / 1000f} s; PARAR encerra.")
                        resumeJob = scope.launch { delay(wait); resumeJob = null; requestListen(manual = false) }
                    } else publish(VoiceSessionPhase.PAUSED, "$error Toque em OUVIR para continuar.")
                }
            }
        })
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Sessão de voz do NEXUS", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Mostra quando a sessão de voz está ativa e permite ouvir ou parar."
        })
        check(manager.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE)
    }

    private fun notification(message: String): Notification {
        val stop = PendingIntent.getService(this, 220, Intent(this, BackgroundVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val listen = PendingIntent.getService(this, 221, Intent(this, BackgroundVoiceService::class.java).setAction(ACTION_LISTEN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 222, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopAll = PendingIntent.getService(this, 223,
            Intent(this, BackgroundVoiceService::class.java).setAction(NexusSessionControls.ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_nexus)
            .setContentTitle("NEXUS · sessão de voz ativa")
            .setContentText(message.take(160)).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "OUVIR", listen).build())
            .addAction(Notification.Action.Builder(null, "PARAR", stop).build())
            .addAction(Notification.Action.Builder(null, "ENCERRAR TUDO", stopAll).build())
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.build()
    }

    private fun publish(phase: VoiceSessionPhase, message: String) {
        if (VoiceSessionRuntime.stopRequested) return
        VoiceSessionRuntime.update(BackgroundVoiceState(true, phase, message, continuous, completedTurns))
        if (started && message != lastNotificationMessage) {
            lastNotificationMessage = message
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(message))
        }
    }

    private fun failStart(message: String) {
        failed = true
        terminalMessage = message
        VoiceSessionRuntime.update(BackgroundVoiceState(phase = VoiceSessionPhase.FAILED, message = message))
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onTaskRemoved(rootIntent: Intent?) { super.onTaskRemoved(rootIntent) }
    override fun onDestroy() {
        started = false
        resumeJob?.cancel(); missingReplyJob?.cancel()
        if (::voice.isInitialized) voice.close()
        if (::model.isInitialized) {
            if (awaitingQuery != null) model.cancelResponse()
            if (!SharedNexusModel.uiVisible) model.onAppBackgrounded()
        }
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        VoiceSessionRuntime.update(BackgroundVoiceState(phase = if (failed) VoiceSessionPhase.FAILED else VoiceSessionPhase.IDLE,
            message = terminalMessage))
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "br.com.nexus.assistant.START_BACKGROUND_VOICE"
        const val ACTION_LISTEN = "br.com.nexus.assistant.LISTEN_BACKGROUND_VOICE"
        const val ACTION_STOP = "br.com.nexus.assistant.STOP_BACKGROUND_VOICE"
        const val ACTION_INTERRUPT = "br.com.nexus.assistant.INTERRUPT_BACKGROUND_VOICE"
        const val EXTRA_CONTINUOUS = "continuous"
        private const val CHANNEL = "nexus_voice_session"
        private const val NOTIFICATION = 2201
    }
}
