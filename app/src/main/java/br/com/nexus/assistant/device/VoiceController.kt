package br.com.nexus.assistant.device

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/** Owned by the foreground UI. This class never starts a background microphone service. */
class VoiceController(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val onDeviceAvailable = runCatching {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
    }.getOrDefault(false)
    private val mutableState = MutableStateFlow(
        VoiceState(
            available = onDeviceAvailable || runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false),
            onDevice = onDeviceAvailable
        )
    )
    val state: StateFlow<VoiceState> = mutableState.asStateFlow()
    private val mutableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = mutableVoices.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var closed = false
    private var resultCallback: ((String) -> Unit)? = null
    private var endCallback: (() -> Unit)? = null
    private var speechDone: (() -> Unit)? = null
    private var finalUtterance: String? = null
    private var activeSpeechPrefix: String? = null
    private var selectedVoice: String? = null
    private var selectedLanguage = "pt-BR"
    private var recognitionTimeout: Runnable? = null

    init {
        onMain {
            try {
                tts = TextToSpeech(appContext) { status ->
                    // An engine may call back during construction. Posting guarantees that
                    // tts has been assigned before readiness or listener state is published.
                    main.post { finishTtsInitialization(status) }
                }
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(ttsReady = false,
                    error = "Não foi possível inicializar a voz do Android. Confira o mecanismo de texto para fala nas configurações do aparelho.")
            }
        }
    }

    private fun finishTtsInitialization(status: Int) {
        if (closed) return
        try {
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                mutableState.value = mutableState.value.copy(ttsReady = false,
                    error = "A síntese de voz não está disponível. Instale ou ative um mecanismo de texto para fala nas configurações do Android.")
                return
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = onMain {
                    if (!closed && finalUtterance != null && utteranceId?.startsWith(activeSpeechPrefix.orEmpty()) == true) {
                        mutableState.value = mutableState.value.copy(isSpeaking = true)
                    }
                }
                override fun onDone(utteranceId: String?) = finishSpeech(utteranceId, null)
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finishSpeech(utteranceId, "A leitura foi interrompida pelo mecanismo de voz.")
                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String?) = finishSpeech(utteranceId, "Não foi possível reproduzir a resposta. Verifique o mecanismo de voz do Android.")
                override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
            })
            refreshVoices()
            mutableState.value = mutableState.value.copy(ttsReady = true)
            updateLanguageAvailability()
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(ttsReady = false,
                error = "O mecanismo de voz não respondeu durante a inicialização. Confira as configurações de texto para fala do Android.")
        }
    }

    /** A selected network voice may use its engine's network service; prefer installed local voices. */
    fun selectVoice(name: String?) = onMain { selectedVoice = name }

    /** Changing the assistant language also changes the eligible voices, never the reverse. */
    fun setLanguage(language: String) = onMain {
        selectedLanguage = language
        refreshVoices()
        updateLanguageAvailability()
    }

    fun refreshLanguageVoices() = onMain {
        if (!closed) {
            refreshVoices()
            updateLanguageAvailability()
        }
    }

    private fun updateLanguageAvailability() {
        val available = mutableVoices.value.isNotEmpty() && runCatching {
            tts?.isLanguageAvailable(Locale.forLanguageTag(selectedLanguage))?.let { it >= TextToSpeech.LANG_AVAILABLE } == true
        }.getOrDefault(false)
        mutableState.value = mutableState.value.copy(languageReady = available, language = selectedLanguage)
    }

    fun startListening(
        language: String = "pt-BR",
        onResult: (String) -> Unit,
        onEnd: (() -> Unit)? = null
    ) = onMain {
        if (closed || mutableState.value.isListening) return@onMain
        if (!mutableState.value.available) {
            mutableState.value = mutableState.value.copy(error = "Nenhum serviço de reconhecimento de fala está instalado neste aparelho.")
            return@onMain
        }
        stopSpeakingInternal()
        resultCallback = onResult
        endCallback = onEnd
        beginRecognition(language, preferOnDevice = true)
    }

    private fun beginRecognition(language: String, preferOnDevice: Boolean) {
        try {
            // Recreate each session: callbacks from a cancelled session cannot deliver stale results.
            releaseRecognizer()
            val useOnDevice = preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            val session = if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
            recognizer = session
            session.setRecognitionListener(object : RecognitionListener {
                private fun current() = !closed && recognizer === session
                override fun onReadyForSpeech(params: Bundle?) {
                    if (current()) {
                        mutableState.value = mutableState.value.copy(isListening = true, error = null)
                        armRecognitionTimeout(session, 90_000L)
                    }
                }
                override fun onBeginningOfSpeech() {
                    if (current()) armRecognitionTimeout(session, 90_000L)
                }
                override fun onRmsChanged(rmsdB: Float) {
                    if (current()) mutableState.value = mutableState.value.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    if (current()) {
                        mutableState.value = mutableState.value.copy(level = 0f)
                        armRecognitionTimeout(session, 12_000L)
                    }
                }
                override fun onError(error: Int) {
                    if (!current()) return
                    // Availability of an on-device engine does not guarantee that pt-BR is
                    // installed. Retry once through the system recognizer, whose possible
                    // network use is explained before the microphone permission is requested.
                    if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) &&
                        runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)) {
                        beginRecognition(language, preferOnDevice = false)
                        return
                    }
                    mutableState.value = mutableState.value.copy(isListening = false, level = 0f, error = recognitionError(error))
                    finishRecognition(null)
                }
                override fun onResults(results: Bundle?) {
                    if (!current()) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    mutableState.value = mutableState.value.copy(
                        isListening = false,
                        level = 0f,
                        partialText = text,
                        error = if (text.isBlank()) "Nenhuma fala foi reconhecida. Tente novamente." else null
                    )
                    finishRecognition(text.takeIf { it.isNotBlank() })
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (current()) mutableState.value = mutableState.value.copy(
                        partialText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    )
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            mutableState.value = mutableState.value.copy(isListening = true, onDevice = useOnDevice, error = null, partialText = "", level = 0f)
            armRecognitionTimeout(session, 15_000L)
            session.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOnDevice)
            })
        } catch (_: SecurityException) {
            mutableState.value = mutableState.value.copy(isListening = false, error = "Autorize o microfone para usar o assistente por voz.")
            finishRecognition(null)
        } catch (_: Exception) {
            if (preferOnDevice && runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)) {
                beginRecognition(language, preferOnDevice = false)
                return
            }
            mutableState.value = mutableState.value.copy(isListening = false, error = "Não foi possível iniciar o reconhecimento de fala. Verifique o serviço de voz do Android.")
            finishRecognition(null)
        }
    }

    fun stopListening() = onMain { stopListeningInternal() }

    private fun stopListeningInternal() {
        // Cancellation deliberately does not invoke onEnd; callers may be leaving the screen.
        resultCallback = null
        endCallback = null
        releaseRecognizer()
        mutableState.value = mutableState.value.copy(isListening = false, level = 0f)
    }

    private fun releaseRecognizer() {
        recognitionTimeout?.let(main::removeCallbacks)
        recognitionTimeout = null
        val previous = recognizer
        recognizer = null
        runCatching { previous?.cancel() }
        runCatching { previous?.destroy() }
    }

    private fun armRecognitionTimeout(session: SpeechRecognizer, delayMillis: Long) {
        recognitionTimeout?.let(main::removeCallbacks)
        val timeout = Runnable {
            if (!closed && recognizer === session) {
                mutableState.value = mutableState.value.copy(isListening = false, level = 0f,
                    error = "O serviço de fala demorou para responder. Toque no microfone para tentar novamente.")
                finishRecognition(null)
            }
        }
        recognitionTimeout = timeout
        main.postDelayed(timeout, delayMillis)
    }

    private fun finishRecognition(text: String?) {
        val result = resultCallback
        val end = endCallback
        resultCallback = null
        endCallback = null
        releaseRecognizer()
        text?.let { result?.invoke(it) }
        end?.invoke()
    }

    fun speak(
        text: String,
        language: String = "pt-BR",
        rate: Float = 1f,
        voiceName: String = "",
        pitch: Float = 1f,
        onDone: (() -> Unit)? = null
    ) = onMain {
        if (closed || text.isBlank()) return@onMain
        val engine = tts
        if (engine == null || !mutableState.value.ttsReady) {
            mutableState.value = mutableState.value.copy(error = "A voz ainda não está pronta. Confira o mecanismo de texto para fala do Android.")
            return@onMain
        }
        stopListeningInternal()
        stopSpeakingInternal()
        try {
            selectedLanguage = language
            refreshVoices()
            updateLanguageAvailability()
            if (!mutableState.value.languageReady || engine.setLanguage(Locale.forLanguageTag(language)) < TextToSpeech.LANG_AVAILABLE) {
                mutableState.value = mutableState.value.copy(error = "O idioma selecionado não está instalado no mecanismo de voz do Android.")
                return@onMain
            }
            val requestedVoice = voiceName.takeIf { it.isNotBlank() } ?: selectedVoice
            val eligible = engine.voices.orEmpty().filter { voiceMatchesLanguage(it.locale.toLanguageTag(), language) }
                .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
            // A persisted foreign/missing voice is ignored. setVoice must never override pt-BR.
            val chosenVoice = eligible.find { it.name == requestedVoice } ?: eligible.firstOrNull { !it.isNetworkConnectionRequired }
            if (chosenVoice == null) {
                mutableState.value = mutableState.value.copy(error = "Nenhuma voz local foi encontrada neste idioma. Instale os dados de voz ou selecione explicitamente uma voz de rede nas configurações do NEXUS.")
                return@onMain
            }
            if (chosenVoice != null && engine.setVoice(chosenVoice) == TextToSpeech.ERROR) {
                mutableState.value = mutableState.value.copy(error = "O mecanismo de fala não conseguiu selecionar esta voz. Escolha outra voz nas configurações do NEXUS.")
                return@onMain
            }
            mutableState.value = mutableState.value.copy(speechUsesNetwork = engine.voice?.isNetworkConnectionRequired == true)
            engine.setSpeechRate(if (rate.isFinite()) rate.coerceIn(0.5f, 2f) else 1f)
            engine.setPitch(if (pitch.isFinite()) pitch.coerceIn(0.5f, 2f) else 1f)
            val chunks = speechChunks(text)
            val id = UUID.randomUUID().toString()
            activeSpeechPrefix = id
            finalUtterance = "$id-${chunks.lastIndex}"
            speechDone = onDone
            mutableState.value = mutableState.value.copy(isSpeaking = true, error = null)
            for ((index, chunk) in chunks.withIndex()) {
                val result = engine.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$id-$index")
                if (result == TextToSpeech.ERROR) {
                    stopSpeakingInternal()
                    mutableState.value = mutableState.value.copy(error = "O mecanismo de voz não conseguiu ler a resposta.")
                    break
                }
            }
        } catch (_: Exception) {
            stopSpeakingInternal()
            mutableState.value = mutableState.value.copy(error = "O mecanismo de voz não respondeu. Confira as vozes instaladas nas configurações do Android.")
        }
    }

    fun stopSpeaking() = onMain { stopSpeakingInternal() }

    private fun stopSpeakingInternal() {
        finalUtterance = null
        activeSpeechPrefix = null
        speechDone = null
        runCatching { tts?.stop() }
        mutableState.value = mutableState.value.copy(isSpeaking = false)
    }

    private fun finishSpeech(utteranceId: String?, error: String?) = onMain {
        if (closed || finalUtterance == null || utteranceId?.startsWith(activeSpeechPrefix.orEmpty()) != true) return@onMain
        // Errors on any queued chunk terminate the queue; success completes on its last chunk.
        if (error != null || utteranceId == finalUtterance) {
            val callback = speechDone
            finalUtterance = null
            activeSpeechPrefix = null
            speechDone = null
            if (error != null) runCatching { tts?.stop() }
            mutableState.value = mutableState.value.copy(isSpeaking = false, error = error)
            if (error == null) callback?.invoke()
        }
    }

    fun clearError() = onMain { mutableState.value = mutableState.value.copy(error = null) }

    private fun refreshVoices() {
        mutableVoices.value = runCatching { tts?.voices }.getOrNull().orEmpty().sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.locale.toLanguageTag() }, { it.name }))
            .filter { voiceMatchesLanguage(it.locale.toLanguageTag(), selectedLanguage) }
            .map { VoiceOption(it.name, "${it.locale.displayName} · ${it.name}", it.locale.toLanguageTag(), it.isNetworkConnectionRequired) }
    }

    override fun close() = onMain {
        if (!closed) {
            closed = true
            stopListeningInternal()
            stopSpeakingInternal()
            runCatching { tts?.shutdown() }
            tts = null
            mutableState.value = mutableState.value.copy(ttsReady = false)
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }
}

data class VoiceState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val error: String? = null,
    val available: Boolean = false,
    val onDevice: Boolean = false,
    val ttsReady: Boolean = false,
    val level: Float = 0f,
    val partialText: String = "",
    val speechUsesNetwork: Boolean = false,
    val languageReady: Boolean = false,
    val language: String = "pt-BR"
)

data class VoiceOption(val name: String, val label: String, val language: String, val requiresNetwork: Boolean)

/** Country is significant: a Brazilian Portuguese request cannot silently select pt-PT. */
internal fun voiceMatchesLanguage(voiceTag: String, requestedTag: String): Boolean {
    val voice = Locale.forLanguageTag(voiceTag)
    val requested = Locale.forLanguageTag(requestedTag)
    return requested.language.isNotBlank() && requested.language == voice.language &&
        (requested.country.isBlank() || requested.country == voice.country)
}

internal fun speechChunks(text: String, limit: Int = TextToSpeech.getMaxSpeechInputLength() - 1): List<String> {
    require(limit >= 2)
    val chunks = mutableListOf<String>()
    var remaining = text.trim()
    while (remaining.length > limit) {
        val candidate = remaining.take(limit)
        var split = candidate.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ' ')).takeIf { it > limit / 2 }?.plus(1) ?: limit
        if (Character.isHighSurrogate(remaining[split - 1]) && Character.isLowSurrogate(remaining[split])) split--
        chunks += remaining.take(split)
        remaining = remaining.drop(split).trimStart()
    }
    if (remaining.isNotEmpty()) chunks += remaining
    return chunks
}

private fun recognitionError(code: Int): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        code in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
        return "O idioma solicitado não está instalado no serviço de fala. Instale o pacote de voz correspondente nas configurações do Android."
    }
    return when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "Não foi possível acessar o áudio do microfone."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Autorize o microfone nas configurações do aplicativo."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "O serviço de fala precisa de conexão. Verifique a internet ou instale um modelo de fala local."
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nenhuma fala foi reconhecida. Toque no microfone para tentar novamente."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O reconhecimento de fala está ocupado. Aguarde um instante e tente novamente."
    SpeechRecognizer.ERROR_SERVER -> "O serviço de reconhecimento de fala não está disponível agora."
    SpeechRecognizer.ERROR_CLIENT -> "O reconhecimento de fala foi interrompido. Tente novamente."
    else -> "Não foi possível reconhecer a fala (código $code). Tente novamente."
    }
}
