package br.com.nexus.assistant.gestures.global

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import br.com.nexus.assistant.MainActivity
import br.com.nexus.assistant.R
import br.com.nexus.assistant.device.DeviceCapabilities
import br.com.nexus.assistant.device.DevicePolicy
import br.com.nexus.assistant.gestures.HandFrame
import br.com.nexus.assistant.gestures.HandPoint
import br.com.nexus.assistant.gestures.MouseController
import br.com.nexus.assistant.gestures.TrackedHand
import br.com.nexus.assistant.gestures.TrackingCadence
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
    private var started = false
    private var lastFrameAt = 0L
    private var openedAt = 0L
    private var terminalError: String? = null
    private lateinit var sessionPolicy: DevicePolicy
    private var useFrontCamera = true
    private val watchdog = object : Runnable {
        override fun run() {
            if (stopped.get() || !started) return
            val now = SystemClock.elapsedRealtime()
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
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (started) return START_NOT_STICKY
        val capabilities = DeviceCapabilities.read(this)
        sessionPolicy = DevicePolicy.evaluate(capabilities)
        if (!sessionPolicy.handTrackingSupported) {
            fail(if (!capabilities.hasCamera) "Este aparelho não possui uma câmera disponível para o mouse global."
                else "O rastreador de mãos não é compatível com a arquitetura deste aparelho. O mouse global foi desativado.")
            return START_NOT_STICKY
        }
        useFrontCamera = capabilities.hasFrontCamera
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
            startCamera()
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
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_nexus)
            .setContentTitle("NEXUS · câmera e mouse global ativos")
            .setContentText("Controle autorizado por gestos. Toque em PARAR para encerrar.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "PARAR", stop).build())
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.build()
    }

    private fun startCamera() {
        val policy = sessionPolicy
        executor.execute {
            try {
                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
                    .setRunningMode(RunningMode.VIDEO).setNumHands(1)
                    .setMinHandDetectionConfidence(.55f).setMinHandPresenceConfidence(.55f).setMinTrackingConfidence(.55f)
                    .build()
                if (!stopped.get()) detector.set(HandLandmarker.createFromOptions(this, options))
            } catch (_: Exception) {
                main.post { if (!stopped.get()) fail("Não foi possível carregar o modelo MediaPipe. A sessão global foi encerrada.") }
            } catch (_: LinkageError) {
                main.post { if (!stopped.get()) fail("A biblioteca de rastreamento não é compatível com este aparelho. A sessão foi encerrada.") }
            } catch (_: OutOfMemoryError) {
                main.post { if (!stopped.get()) fail("Não há memória livre suficiente para iniciar o rastreamento. A sessão foi encerrada.") }
            }
        }
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(policy.cameraWidth, policy.cameraHeight), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
        val analyzer = ImageAnalysis.Builder().setResolutionSelector(selector)
            .setTargetRotation(displayRotation())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
        analysis = analyzer
        val cadence = TrackingCadence(policy.analysisIntervalMs)
        val inferenceFailed = AtomicBoolean(false)
        analyzer.setAnalyzer(executor) { proxy ->
            var bitmap: Bitmap? = null
            var rotated: Bitmap? = null
            try {
                val tracker = detector.get()
                val now = SystemClock.elapsedRealtime()
                if (stopped.get() || tracker == null || !cadence.shouldAnalyze(now)) return@setAnalyzer
                bitmap = proxy.toBitmap()
                val matrix = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
                rotated = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, false)
                val image = BitmapImageBuilder(rotated!!).build()
                val result = try { tracker.detectForVideo(image, now) } finally { image.close() }
                val hands = result.landmarks().mapIndexed { index, landmarks ->
                    val category = result.handedness().getOrNull(index)?.firstOrNull()
                    TrackedHand(landmarks.map { HandPoint(if (useFrontCamera) 1f - it.x() else it.x(), it.y(), it.z()) },
                        category?.categoryName() ?: "Unknown", category?.score() ?: 0f)
                }
                val processingMs = SystemClock.elapsedRealtime() - now
                cadence.observeCost(processingMs)
                val frame = HandFrame(hands, rotated!!.width, rotated!!.height, processingMs, now)
                main.post { if (!stopped.get()) onFrame(frame) }
            } catch (_: Exception) {
                if (inferenceFailed.compareAndSet(false, true)) main.post {
                    if (!stopped.get()) fail("O rastreamento falhou. A câmera e o controle global foram encerrados.")
                }
            } catch (_: LinkageError) {
                if (inferenceFailed.compareAndSet(false, true)) main.post {
                    if (!stopped.get()) fail("A biblioteca de rastreamento não está disponível neste aparelho. A sessão foi encerrada.")
                }
            } catch (_: OutOfMemoryError) {
                if (inferenceFailed.compareAndSet(false, true)) main.post {
                    if (!stopped.get()) fail("O rastreamento foi interrompido por falta de memória. A sessão foi encerrada.")
                }
            } finally {
                if (rotated != null && rotated !== bitmap) rotated?.recycle()
                bitmap?.recycle()
                proxy.close()
            }
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!stopped.get()) {
                try {
                    val cameraSelector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    provider = future.get().also {
                        check(it.hasCamera(cameraSelector))
                        it.bindToLifecycle(this, cameraSelector, analyzer)
                    }
                } catch (_: Exception) {
                    fail("A câmera selecionada está indisponível ou em uso. A sessão global foi encerrada.")
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onFrame(frame: HandFrame) {
        lastFrameAt = frame.timestampMs
        val preferences = getSharedPreferences("nexus_gestures", MODE_PRIVATE)
        val update = controller.update(frame, preferences.getFloat("sensitivity", 1.3f).coerceIn(.6f, 2.3f),
            preferences.getFloat("pinch", .35f).coerceIn(.2f, .55f))
        NexusAccessibilityService.instance?.let { accessibility ->
            accessibility.updateCursor(update.cursor)
            update.events.forEach(accessibility::dispatch)
        }
        GlobalMouseRuntime.update(GlobalMouseState(active = true, cameraReady = true, hands = frame.hands.size, processingMs = frame.processingMs))
    }

    private fun fail(message: String) {
        terminalError = message
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

    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf(); super.onTaskRemoved(rootIntent) }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped.set(true)
        main.removeCallbacks(watchdog)
        analysis?.clearAnalyzer()
        analysis?.let { provider?.unbind(it) }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        controller.reset().events.forEach { NexusAccessibilityService.instance?.dispatch(it) }
        NexusAccessibilityService.instance?.endSession()
        executor.execute { detector.getAndSet(null)?.close() }
        executor.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Publish availability only after the camera lease is released.
        GlobalMouseRuntime.update(GlobalMouseState(error = terminalError))
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "nexus_global_mouse"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "br.com.nexus.assistant.STOP_GLOBAL_MOUSE"
    }
}
