package br.com.nexus.assistant.gestures.global

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import br.com.nexus.assistant.MainActivity
import br.com.nexus.assistant.R
import br.com.nexus.assistant.device.DeviceCapabilities
import br.com.nexus.assistant.device.DevicePolicy
import br.com.nexus.assistant.device.TrackingConfiguration
import br.com.nexus.assistant.device.TrackingProfilePreferences
import br.com.nexus.assistant.device.TrackingProfiles
import br.com.nexus.assistant.device.voice.NexusSessionControls
import br.com.nexus.assistant.gestures.HandFrame
import br.com.nexus.assistant.gestures.HandPoint
import br.com.nexus.assistant.gestures.CameraFrameTimeline
import br.com.nexus.assistant.gestures.LatestFrameDelivery
import br.com.nexus.assistant.gestures.MouseController
import br.com.nexus.assistant.gestures.ReusableHandImage
import br.com.nexus.assistant.gestures.TrackedHand
import br.com.nexus.assistant.gestures.TrackingCadence
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

/** Explicit, visible, non-restarting camera session. No boot receiver or microphone access. */
class GlobalMouseCameraService : Service(), LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    private val detector = AtomicReference<HandLandmarker?>(null)
    private val controller = MouseController()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var observedCameraState: LiveData<CameraState>? = null
    private var imageBuffer: ReusableHandImage? = null
    private var frameDelivery: LatestFrameDelivery<HandFrame>? = null
    private var started = false
    @Volatile private var paused = false
    private val cameraGeneration = AtomicLong(0)
    private var lastFrameAt = 0L
    private var openedAt = 0L
    private var lastControlCheckAt = 0L
    private var terminalError: String? = null
    private var sensitivity = 1.3f
    private var pinchThreshold = .35f
    private var preferencesLoaded = false
    private var calibrationRequested = false
    @Volatile private var mirrorHorizontally = true
    private val gesturePreferences by lazy { getSharedPreferences("nexus_gestures", MODE_PRIVATE) }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        if (key == "sensitivity") sensitivity = preferences.getFloat("sensitivity", 1.3f).coerceIn(.6f, 2.3f)
        if (key == "pinch") pinchThreshold = preferences.getFloat("pinch", .35f).coerceIn(.2f, .55f)
        if (key == "global_mirror") {
            mirrorHorizontally = preferences.getBoolean("global_mirror", useFrontCamera)
            controller.reset().events.forEach { NexusAccessibilityService.instance?.dispatch(it) }
            NexusAccessibilityService.instance?.updateCursor(null)
        }
    }
    private lateinit var tracking: TrackingConfiguration
    private var useFrontCamera = true
    private val watchdog = object : Runnable {
        override fun run() {
            if (stopped.get() || !started) return
            // stopService/onDestroy are asynchronous. A stop/failure published by
            // an overlay control must block input and stop this owner immediately.
            if (!GlobalMouseRuntime.state.value.active) { stopped.set(true); stopSelf(); return }
            val now = SystemClock.elapsedRealtime()
            if (now - lastControlCheckAt >= 1_500L) {
                lastControlCheckAt = now
                if (!checkSessionControls()) return
            }
            if (paused) { main.postDelayed(this, 250); return }
            if (lastFrameAt != 0L && now - lastFrameAt > 800L) {
                controller.lost().events.forEach { NexusAccessibilityService.instance?.dispatch(it) }
                NexusAccessibilityService.instance?.updateCursor(null)
            }
            if (NexusAccessibilityService.instance == null) {
                fail("O serviço de acessibilidade foi desconectado. A câmera foi encerrada.")
                return
            }
            if ((lastFrameAt == 0L && now - openedAt > 20_000L) || (lastFrameAt != 0L && now - lastFrameAt > 8_000L)) {
                fail("A câmera deixou de fornecer quadros. A sessão foi encerrada; verifique se outro aplicativo está usando a câmera.")
                return
            }
            main.postDelayed(this, 250)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == NexusSessionControls.ACTION_STOP_ALL) {
            NexusSessionControls.stopAll(this)
            return START_NOT_STICKY
        }
        if (intent == null || intent.action == ACTION_STOP) {
            GlobalMouseRuntime.markStopping()
            stopped.set(true)
            NexusAccessibilityService.instance?.endSession()
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) {
            if (stopped.get() || !GlobalMouseRuntime.state.value.active) { stopSelf(); return START_NOT_STICKY }
            if (intent.action == ACTION_PAUSE) pauseSession()
            if (intent.action == ACTION_RESUME && paused && ::tracking.isInitialized) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                    NexusAccessibilityService.instance == null) {
                    fail("Câmera ou acessibilidade indisponível. Encerre e autorize novamente pelo NEXUS.")
                    return START_NOT_STICKY
                }
                paused = false
                GlobalMouseRuntime.update(GlobalMouseRuntime.state.value.copy(paused = false, cameraReady = false,
                    cameraStatus = "Preparando câmera", message = null))
                startCamera()
                publishNotification()
            }
            if (intent.action == ACTION_CALIBRATE) {
                calibrationRequested = true
                GlobalMouseRuntime.update(GlobalMouseRuntime.state.value.copy(message = "Mostre o indicador para centralizar o cursor."))
            }
            return START_NOT_STICKY
        }
        // A stale calibration request after PARAR must never open the camera.
        if (intent.action != ACTION_START) { stopSelf(); return START_NOT_STICKY }
        if (intent.getLongExtra(EXTRA_START_GENERATION, Long.MIN_VALUE) != GlobalMouseRuntime.stopGeneration) {
            stopSelf(); return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail("A permissão da câmera não foi concedida."); return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            fail("Autorize notificações antes de iniciar o mouse global."); return START_NOT_STICKY
        }
        if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            fail("Ative as notificações do NEXUS nas configurações do Android antes de iniciar o controle global.")
            return START_NOT_STICKY
        }
        val accessibility = NexusAccessibilityService.instance
        if (accessibility == null) {
            fail("Ative a acessibilidade do NEXUS e inicie uma sessão visível pelo aplicativo."); return START_NOT_STICKY
        }
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            else startForeground(NOTIFICATION_ID, notification)
            started = true
            openedAt = SystemClock.elapsedRealtime()
            GlobalMouseRuntime.update(GlobalMouseState(active = true))
            accessibility.beginSession()
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            // File-system and PackageManager observations run off the UI thread. The
            // foreground notification is already visible while preparing the session.
            executor.execute {
                try {
                    val capabilities = DeviceCapabilities.read(this)
                    val savedSensitivity = gesturePreferences.getFloat("sensitivity", 1.3f).coerceIn(.6f, 2.3f)
                    val savedPinch = gesturePreferences.getFloat("pinch", .35f).coerceIn(.2f, .55f)
                    val policy = DevicePolicy.evaluate(capabilities)
                    val configuration = TrackingProfiles.resolve(capabilities, TrackingProfilePreferences.read(this), requestedHands = 1, pointerMode = true)
                    main.post {
                        if (!stopped.get()) {
                            if (!policy.handTrackingSupported) {
                                fail(if (!capabilities.hasCamera) "Este aparelho não possui uma câmera disponível para o mouse global."
                                    else "O rastreador de mãos não é compatível com a arquitetura deste aparelho. O mouse global foi desativado.")
                            } else {
                                tracking = configuration
                                sensitivity = savedSensitivity
                                pinchThreshold = savedPinch
                                preferencesLoaded = true
                                gesturePreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
                                useFrontCamera = capabilities.hasFrontCamera
                                mirrorHorizontally = gesturePreferences.getBoolean("global_mirror", useFrontCamera)
                                if (!paused) startCamera()
                            }
                        }
                    }
                } catch (_: Exception) {
                    main.post { if (!stopped.get()) fail("Não foi possível verificar a câmera deste aparelho. A sessão foi encerrada.") }
                }
            }
            main.post(watchdog)
        } catch (_: Exception) {
            fail("Não foi possível iniciar o mouse global. Abra o NEXUS, confira câmera, notificações e acessibilidade e tente novamente.")
        }
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Mouse global e câmera", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Indica a sessão de câmera e permite encerrá-la imediatamente."
        })
        check(manager.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE) {
            "O canal de notificação da sessão está desativado"
        }
        val stop = PendingIntent.getService(this, 20, Intent(this, GlobalMouseCameraService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 21, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopAll = PendingIntent.getService(this, 22,
            Intent(this, GlobalMouseCameraService::class.java).setAction(NexusSessionControls.ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 23,
            Intent(this, GlobalMouseCameraService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_nexus)
            .setContentTitle(if (paused) "NEXUS · mouse global pausado, câmera desligada" else "NEXUS · câmera e mouse global ativos")
            .setContentText(if (paused) "Abra o NEXUS para retomar a câmera ou toque em PARAR." else "Controle autorizado por gestos. Você pode pausar ou parar.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
        if (!paused) builder.addAction(Notification.Action.Builder(null, "PAUSAR", pause).build())
        builder.addAction(Notification.Action.Builder(null, "PARAR", stop).build())
            .addAction(Notification.Action.Builder(null, "ENCERRAR TUDO", stopAll).build())
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.build()
    }

    private fun startCamera() {
        if (paused || stopped.get() || !GlobalMouseRuntime.state.value.active) return
        val ticket = cameraGeneration.incrementAndGet()
        openedAt = SystemClock.elapsedRealtime()
        lastFrameAt = 0L
        val configuration = tracking
        val images = ReusableHandImage().also { imageBuffer = it }
        val delivery = LatestFrameDelivery<HandFrame>({ main.post(it) }) { frame ->
            if (!stopped.get() && !paused && GlobalMouseRuntime.state.value.active && ticket == cameraGeneration.get()) {
                val deliveredAt = SystemClock.elapsedRealtime()
                if (deliveredAt - frame.timestampMs <= 800L) onFrame(frame.copy(deliveredAtMs = deliveredAt))
                else {
                    controller.lost().events.forEach { NexusAccessibilityService.instance?.dispatch(it) }
                    NexusAccessibilityService.instance?.updateCursor(null)
                }
            }
        }.also { frameDelivery = it }
        executor.execute {
            try {
                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
                    .setRunningMode(RunningMode.VIDEO).setNumHands(1)
                    .setMinHandDetectionConfidence(.55f).setMinHandPresenceConfidence(.55f).setMinTrackingConfidence(.55f)
                    .build()
                if (!stopped.get() && !paused && GlobalMouseRuntime.state.value.active && ticket == cameraGeneration.get()) {
                    detector.getAndSet(HandLandmarker.createFromOptions(this, options))?.close()
                }
            } catch (_: Exception) {
                main.post { if (!stopped.get() && !paused && GlobalMouseRuntime.state.value.active && ticket == cameraGeneration.get()) fail("Não foi possível carregar o modelo MediaPipe. A sessão global foi encerrada.") }
            } catch (_: LinkageError) {
                main.post { if (!stopped.get() && !paused && GlobalMouseRuntime.state.value.active && ticket == cameraGeneration.get()) fail("A biblioteca de rastreamento não é compatível com este aparelho. A sessão foi encerrada.") }
            } catch (_: OutOfMemoryError) {
                main.post { if (!stopped.get() && !paused && GlobalMouseRuntime.state.value.active && ticket == cameraGeneration.get()) fail("Não há memória livre suficiente para iniciar o rastreamento. A sessão foi encerrada.") }
            }
        }
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(configuration.cameraWidth, configuration.cameraHeight), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
        val analyzer = ImageAnalysis.Builder().setResolutionSelector(selector)
            .setTargetRotation(displayRotation())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
        analysis = analyzer
        val cadence = TrackingCadence(configuration.analysisIntervalMs, 50L)
        val timeline = CameraFrameTimeline()
        val inferenceFailed = AtomicBoolean(false)
        analyzer.setAnalyzer(executor) { proxy ->
            try {
                val tracker = detector.get()
                if (stopped.get() || paused || !GlobalMouseRuntime.state.value.active || ticket != cameraGeneration.get() || inferenceFailed.get() || tracker == null) return@setAnalyzer
                val stamp = timeline.begin(proxy.imageInfo.timestamp, SystemClock.elapsedRealtimeNanos()) ?: return@setAnalyzer
                val now = stamp.analysisStartedAtMs
                if (!cadence.shouldAnalyze(now)) return@setAnalyzer
                val bitmap = images.read(proxy)
                val conversionMs = SystemClock.elapsedRealtime() - now
                val image = images.buildImage(bitmap)
                val inferenceAt = SystemClock.elapsedRealtime()
                val result = try { tracker.detectForVideo(image, now) } finally { image.close() }
                val inferenceMs = SystemClock.elapsedRealtime() - inferenceAt
                val hands = result.landmarks().mapIndexed { index, landmarks ->
                    val category = result.handedness().getOrNull(index)?.firstOrNull()
                    TrackedHand(landmarks.map { HandPoint(if (mirrorHorizontally) 1f - it.x() else it.x(), it.y(), it.z()) },
                        category?.categoryName() ?: "Unknown", category?.score() ?: 0f)
                }
                val processingMs = SystemClock.elapsedRealtime() - now
                cadence.observeCost(processingMs)
                val frame = HandFrame(hands, bitmap.width, bitmap.height, processingMs, now,
                    conversionMs, inferenceMs, proxy.imageInfo.timestamp, stamp.excessQueueDelayMs)
                if (!stopped.get() && !paused && GlobalMouseRuntime.state.value.active && ticket == cameraGeneration.get()) {
                    delivery.offer(frame)
                }
            } catch (_: Exception) {
                if (inferenceFailed.compareAndSet(false, true)) main.post {
                    if (!stopped.get() && !paused && GlobalMouseRuntime.state.value.active && ticket == cameraGeneration.get()) fail("O rastreamento falhou. A câmera e o controle global foram encerrados.")
                }
            } catch (_: LinkageError) {
                if (inferenceFailed.compareAndSet(false, true)) main.post {
                    if (!stopped.get() && !paused && GlobalMouseRuntime.state.value.active && ticket == cameraGeneration.get()) fail("A biblioteca de rastreamento não está disponível neste aparelho. A sessão foi encerrada.")
                }
            } catch (_: OutOfMemoryError) {
                if (inferenceFailed.compareAndSet(false, true)) main.post {
                    if (!stopped.get() && !paused && GlobalMouseRuntime.state.value.active && ticket == cameraGeneration.get()) fail("O rastreamento foi interrompido por falta de memória. A sessão foi encerrada.")
                }
            } finally {
                proxy.close()
            }
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!stopped.get() && !paused && GlobalMouseRuntime.state.value.active && ticket == cameraGeneration.get()) {
                try {
                    val cameraSelector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    provider = future.get().also {
                        check(it.hasCamera(cameraSelector))
                        val camera = it.bindToLifecycle(this, cameraSelector, analyzer)
                        observedCameraState = camera.cameraInfo.cameraState
                        camera.cameraInfo.cameraState.observe(this) { state ->
                            if (!stopped.get() && !paused && GlobalMouseRuntime.state.value.active && ticket == cameraGeneration.get()) observeCameraState(state)
                        }
                    }
                } catch (_: Exception) {
                    fail("A câmera selecionada está indisponível ou em uso. A sessão global foi encerrada.")
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onFrame(frame: HandFrame) {
        if (paused || stopped.get() || !GlobalMouseRuntime.state.value.active) return
        lastFrameAt = frame.timestampMs
        if (calibrationRequested) frame.hands.firstOrNull()?.let { hand ->
            controller.reset().events.forEach { NexusAccessibilityService.instance?.dispatch(it) }
            controller.calibrate(hand.index, sensitivity)
            calibrationRequested = false
        }
        val update = controller.update(frame, sensitivity, pinchThreshold)
        val accessibility = NexusAccessibilityService.instance ?: run {
            fail("A acessibilidade foi desconectada. A sessão global foi encerrada.")
            return
        }
        accessibility.updateCursor(update.cursor)
        // Updating the overlay can itself fail and request a stop. Do not send
        // this frame's queued tap/drag or re-enable state after that failure.
        if (stopped.get() || !GlobalMouseRuntime.state.value.active) return
        update.events.forEach(accessibility::dispatch)
        GlobalMouseRuntime.updateFromFrame { state -> state.copy(cameraReady = true,
            hands = frame.hands.size, processingMs = frame.processingMs, cameraStatus = "Transmitindo quadros locais",
            frameAgeMs = (SystemClock.elapsedRealtime() - frame.timestampMs).coerceAtLeast(0),
            message = if (calibrationRequested) "Mostre o indicador para calibrar." else state.message) }
    }

    private fun observeCameraState(state: CameraState) {
        val error = state.error
        if (error != null) {
            val reason = when (error.code) {
                CameraState.ERROR_CAMERA_IN_USE -> "Outra aplicação está usando a câmera."
                CameraState.ERROR_MAX_CAMERAS_IN_USE -> "O aparelho não permite mais câmeras simultâneas."
                CameraState.ERROR_CAMERA_DISABLED -> "A câmera foi desativada pelo Android, pelo controle de privacidade ou por uma política do aparelho."
                CameraState.ERROR_CAMERA_FATAL_ERROR -> "O sistema da câmera apresentou uma falha."
                CameraState.ERROR_STREAM_CONFIG -> "A câmera recusou esta resolução ou configuração."
                else -> "O Android informou indisponibilidade da câmera (código ${error.code})."
            }
            // Do not dispatch stale gestures while Android waits for the camera to recover.
            controller.reset().events.forEach { NexusAccessibilityService.instance?.dispatch(it) }
            NexusAccessibilityService.instance?.updateCursor(null)
            GlobalMouseRuntime.update(GlobalMouseRuntime.state.value.copy(cameraReady = false, hands = 0,
                cameraStatus = reason, message = "Sem controle enquanto a câmera estiver indisponível."))
            if (error.type == CameraState.ErrorType.CRITICAL) fail("$reason A sessão foi encerrada.")
            return
        }
        if (!GlobalMouseRuntime.state.value.cameraReady) {
            val label = when (state.type) {
                CameraState.Type.OPEN -> "Câmera aberta; aguardando primeiro quadro"
                CameraState.Type.PENDING_OPEN -> "Aguardando disponibilidade da câmera"
                CameraState.Type.OPENING -> "Abrindo câmera"
                CameraState.Type.CLOSING -> "Liberando câmera"
                CameraState.Type.CLOSED -> "Câmera fechada"
            }
            GlobalMouseRuntime.update(GlobalMouseRuntime.state.value.copy(cameraStatus = label))
        }
    }

    /** Permission/notification revocation is observed without collecting app contents.
     * Bounded polling avoids adding PackageManager/Binder work to every frame.
     */
    private fun checkSessionControls(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail("A permissão da câmera foi revogada. A sessão global foi encerrada.")
            return false
        }
        val manager = getSystemService(NotificationManager::class.java)
        val notificationPermission = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channel = manager.getNotificationChannel(CHANNEL)
        if (!notificationPermission || !manager.areNotificationsEnabled() || channel == null ||
            channel.importance == NotificationManager.IMPORTANCE_NONE) {
            fail("As notificações de controle foram desativadas. O mouse global foi encerrado.")
            return false
        }
        if (NexusAccessibilityService.instance == null) {
            fail("O serviço de acessibilidade foi desconectado. A câmera foi encerrada.")
            return false
        }
        return true
    }

    private fun pauseSession() {
        if (paused) return
        paused = true
        cameraGeneration.incrementAndGet()
        frameDelivery?.close(); frameDelivery = null
        observedCameraState?.removeObservers(this); observedCameraState = null
        analysis?.clearAnalyzer()
        analysis?.let { provider?.unbind(it) }
        analysis = null
        controller.reset().events.forEach { NexusAccessibilityService.instance?.dispatch(it) }
        NexusAccessibilityService.instance?.updateCursor(null)
        val images = imageBuffer.also { imageBuffer = null }
        executor.execute { detector.getAndSet(null)?.close(); images?.close() }
        GlobalMouseRuntime.update(GlobalMouseRuntime.state.value.copy(paused = true, cameraReady = false, hands = 0,
            cameraStatus = "Pausada; câmera liberada", message = "Retome nesta tela visível. O microfone continua independente."))
        publishNotification()
    }

    private fun publishNotification() {
        try {
            check(getSystemService(NotificationManager::class.java).areNotificationsEnabled())
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification())
        } catch (_: Exception) {
            fail("As notificações de controle foram desativadas. O mouse global foi encerrado.")
        }
    }

    private fun fail(message: String) {
        terminalError = message
        stopped.set(true)
        GlobalMouseRuntime.fail(message)
        GlobalMouseRuntime.markStopping()
        NexusAccessibilityService.instance?.endSession()
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = getSystemService(WindowManager::class.java).defaultDisplay.rotation

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        analysis?.targetRotation = displayRotation()
        controller.reset().events.forEach { NexusAccessibilityService.instance?.dispatch(it) }
        NexusAccessibilityService.instance?.updateCursor(null)
    }

    // Swiping the activity away does not revoke an explicitly started foreground session.
    // Android may still stop the process; START_NOT_STICKY never restarts the camera silently.
    override fun onTaskRemoved(rootIntent: Intent?) { super.onTaskRemoved(rootIntent) }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped.set(true)
        main.removeCallbacks(watchdog)
        frameDelivery?.close(); frameDelivery = null
        observedCameraState?.removeObservers(this); observedCameraState = null
        analysis?.clearAnalyzer()
        if (preferencesLoaded) gesturePreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        analysis?.let { provider?.unbind(it) }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        controller.reset().events.forEach { NexusAccessibilityService.instance?.dispatch(it) }
        NexusAccessibilityService.instance?.endSession()
        val images = imageBuffer.also { imageBuffer = null }
        executor.execute { detector.getAndSet(null)?.close(); images?.close() }
        executor.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Publish availability only after the camera lease is released.
        GlobalMouseRuntime.update(GlobalMouseState(error = terminalError ?: GlobalMouseRuntime.state.value.error))
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "nexus_global_mouse"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_START = "br.com.nexus.assistant.START_GLOBAL_MOUSE"
        const val EXTRA_START_GENERATION = "start_generation"
        const val ACTION_CALIBRATE = "br.com.nexus.assistant.CALIBRATE_GLOBAL_MOUSE"
        const val ACTION_PAUSE = "br.com.nexus.assistant.PAUSE_GLOBAL_MOUSE"
        const val ACTION_RESUME = "br.com.nexus.assistant.RESUME_GLOBAL_MOUSE"
        private const val ACTION_STOP = "br.com.nexus.assistant.STOP_GLOBAL_MOUSE"
    }
}
