package br.com.nexus.assistant.gestures

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.nexus.assistant.device.DeviceCapabilities
import br.com.nexus.assistant.device.DevicePolicy
import br.com.nexus.assistant.device.TrackingProfile
import br.com.nexus.assistant.device.TrackingProfilePreferences
import br.com.nexus.assistant.device.TrackingProfiles
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class HandFrame(
    val hands: List<TrackedHand> = emptyList(),
    val imageWidth: Int = 1,
    val imageHeight: Int = 1,
    val processingMs: Long = 0L,
    /** Monotonic app-clock start of this observation's analysis. */
    val timestampMs: Long = 0L,
    val conversionMs: Long = 0L,
    val inferenceMs: Long = 0L,
    /** Raw camera clock, useful for ordering; not directly comparable to uptime. */
    val cameraTimestampNs: Long = 0L,
    /** Excess queue estimate relative to the best observed camera/app offset. */
    val queueDelayMs: Long = 0L,
    val deliveredAtMs: Long = 0L,
)

/** Resolution, hand count and pacing adapt to the device. Frames never leave this device. */
@Composable
internal fun HandCamera(
    frontCamera: Boolean,
    modifier: Modifier = Modifier,
    onFrame: (HandFrame) -> Unit,
    onError: (String) -> Unit,
    requestedHands: Int = 1,
    showPreview: Boolean = true,
    pointerMode: Boolean = false,
    trackingProfile: TrackingProfile? = null,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentFrame = rememberUpdatedState(onFrame)
    val currentError = rememberUpdatedState(onError)
    val capabilities = remember(context) { DeviceCapabilities.read(context) }
    val policy = remember(capabilities) { DevicePolicy.evaluate(capabilities) }
    // Some devices have only one camera. Mirror according to the selected lens,
    // never according to a front-camera preference which could not be honored.
    val useFrontCamera = if (frontCamera) capabilities.hasFrontCamera else !capabilities.hasRearCamera
    val preference = trackingProfile ?: remember(context) { TrackingProfilePreferences.read(context) }
    val configuration = remember(capabilities, preference, requestedHands, pointerMode) {
        TrackingProfiles.resolve(capabilities, preference, requestedHands, pointerMode)
    }
    val handCount = configuration.maxHands
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    if (showPreview) AndroidView(factory = { previewView }, modifier = modifier)
    else Box(modifier)

    DisposableEffect(context, owner, useFrontCamera, handCount, policy, showPreview, configuration) {
        val stopped = AtomicBoolean(false)
        if (!policy.handTrackingSupported) {
            ContextCompat.getMainExecutor(context).execute {
                if (!stopped.get()) currentError.value(if (!capabilities.hasCamera) "Este aparelho não possui uma câmera disponível para rastrear mãos."
                    else "O rastreador de mãos não está disponível para a arquitetura deste aparelho. As outras funções do NEXUS continuam disponíveis.")
            }
            return@DisposableEffect onDispose { stopped.set(true) }
        }
        val detector = AtomicReference<HandLandmarker?>(null)
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val frameHandler = Handler(Looper.getMainLooper())
        val delivery = LatestFrameDelivery<HandFrame>(schedule = { frameHandler.post(it) }) { frame ->
            if (!stopped.get()) {
                val delivered = SystemClock.elapsedRealtime()
                // Never act on old results after a busy UI. An empty observation
                // cancels an outstanding drag rather than holding a stale touch.
                currentFrame.value(frame.copy(
                    hands = if (delivered - frame.timestampMs <= 800L) frame.hands else emptyList(),
                    deliveredAtMs = delivered,
                ))
            }
        }
        var provider: ProcessCameraProvider? = null
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(configuration.cameraWidth, configuration.cameraHeight), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
        val preview = Preview.Builder().setResolutionSelector(selector).build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(selector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        fun report(message: String) {
            main.execute { if (!stopped.get()) currentError.value(message) }
        }
        executor.execute {
            try {
                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(handCount)
                    .setMinHandDetectionConfidence(.55f)
                    .setMinHandPresenceConfidence(.55f)
                    .setMinTrackingConfidence(.55f)
                    .build()
                if (!stopped.get()) detector.set(HandLandmarker.createFromOptions(context, options))
            } catch (_: Exception) {
                report("Não foi possível carregar o rastreador de mãos. Confira o modelo MediaPipe e a compatibilidade deste aparelho.")
            } catch (_: LinkageError) {
                report("A biblioteca de rastreamento não é compatível com este aparelho. A câmera será encerrada.")
            } catch (_: OutOfMemoryError) {
                report("Não há memória livre suficiente para iniciar o rastreamento. Feche outros aplicativos e tente novamente.")
            }
        }

        val cadence = TrackingCadence(configuration.analysisIntervalMs, 50L)
        val timeline = CameraFrameTimeline()
        val images = ReusableHandImage()
        var inferenceFailed = false
        analysis.setAnalyzer(executor) { proxy ->
            try {
                val tracker = detector.get()
                if (stopped.get() || inferenceFailed || tracker == null) return@setAnalyzer
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
                    TrackedHand(landmarks.map { landmark ->
                        HandPoint(if (useFrontCamera) 1f - landmark.x() else landmark.x(), landmark.y(), landmark.z())
                    }, handedness = category?.categoryName() ?: "Unknown", confidence = category?.score() ?: 0f)
                }
                val processingMs = SystemClock.elapsedRealtime() - now
                cadence.observeCost(processingMs)
                delivery.offer(HandFrame(hands, bitmap.width, bitmap.height, processingMs, now,
                    conversionMs, inferenceMs, proxy.imageInfo.timestamp, stamp.excessQueueDelayMs))
            } catch (_: Exception) {
                if (!inferenceFailed) {
                    inferenceFailed = true
                    report("O rastreamento foi interrompido. Feche esta tela e tente novamente; a câmera ou o modelo podem não ser compatíveis.")
                }
            } catch (_: LinkageError) {
                if (!inferenceFailed) { inferenceFailed = true; report("A biblioteca de rastreamento não está disponível neste aparelho.") }
            } catch (_: OutOfMemoryError) {
                if (!inferenceFailed) { inferenceFailed = true; report("O rastreamento foi interrompido por falta de memória livre.") }
            } finally {
                proxy.close()
            }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!stopped.get()) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    if (showPreview) preview.setSurfaceProvider(previewView.surfaceProvider)
                    val cameraSelector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    check(cameraProvider.hasCamera(cameraSelector))
                    if (showPreview) cameraProvider.bindToLifecycle(owner, cameraSelector, preview, analysis)
                    else cameraProvider.bindToLifecycle(owner, cameraSelector, analysis)
                } catch (_: Exception) {
                    report("Não foi possível abrir esta câmera. Verifique a permissão e se outro aplicativo está usando a câmera.")
                }
            }
        }, main)

        onDispose {
            stopped.set(true)
            delivery.close()
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            executor.execute { try { detector.getAndSet(null)?.close() } finally { images.close() } }
            executor.shutdown()
        }
    }
}
