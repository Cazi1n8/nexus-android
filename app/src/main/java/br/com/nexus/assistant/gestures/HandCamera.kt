package br.com.nexus.assistant.gestures

import android.graphics.Bitmap
import android.graphics.Matrix
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
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
    val timestampMs: Long = 0L
)

/** CPU inference is bounded to about 9 frames/s. Frames never leave this device. */
@Composable
internal fun HandCamera(
    frontCamera: Boolean,
    modifier: Modifier = Modifier,
    onFrame: (HandFrame) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentFrame = rememberUpdatedState(onFrame)
    val currentError = rememberUpdatedState(onError)
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(context, owner, frontCamera) {
        val stopped = AtomicBoolean(false)
        val detector = AtomicReference<HandLandmarker?>(null)
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        var provider: ProcessCameraProvider? = null
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
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
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumHands(2)
                    .setMinHandDetectionConfidence(.55f)
                    .setMinHandPresenceConfidence(.55f)
                    .setMinTrackingConfidence(.55f)
                    .build()
                detector.set(HandLandmarker.createFromOptions(context, options))
            } catch (_: Exception) {
                report("Não foi possível carregar o rastreador de mãos. Confira o modelo MediaPipe e a compatibilidade deste aparelho.")
            }
        }

        var lastFrameAt = 0L
        var inferenceFailed = false
        analysis.setAnalyzer(executor) { proxy ->
            var bitmap: Bitmap? = null
            var rotated: Bitmap? = null
            try {
                val tracker = detector.get()
                val now = SystemClock.elapsedRealtime()
                if (stopped.get() || tracker == null || now - lastFrameAt < 110L) return@setAnalyzer
                lastFrameAt = now
                bitmap = proxy.toBitmap()
                val matrix = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
                rotated = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, false)
                val image = BitmapImageBuilder(rotated!!).build()
                val result = try { tracker.detect(image) } finally { image.close() }
                val hands = result.landmarks().map { landmarks ->
                    TrackedHand(landmarks.map { landmark ->
                        HandPoint(if (frontCamera) 1f - landmark.x() else landmark.x(), landmark.y(), landmark.z())
                    })
                }
                val frame = HandFrame(hands, rotated!!.width, rotated!!.height,
                    SystemClock.elapsedRealtime() - now, now)
                main.execute { if (!stopped.get()) currentFrame.value(frame) }
            } catch (_: Exception) {
                if (!inferenceFailed) {
                    inferenceFailed = true
                    report("O rastreamento foi interrompido. Feche esta tela e tente novamente; a câmera ou o modelo podem não ser compatíveis.")
                }
            } finally {
                if (rotated != null && rotated !== bitmap) rotated?.recycle()
                bitmap?.recycle()
                proxy.close()
            }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!stopped.get()) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val cameraSelector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.bindToLifecycle(owner, cameraSelector, preview, analysis)
                } catch (_: Exception) {
                    report("Não foi possível abrir esta câmera. Verifique a permissão e se outro aplicativo está usando a câmera.")
                }
            }
        }, main)

        onDispose {
            stopped.set(true)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            executor.execute { detector.getAndSet(null)?.close() }
            executor.shutdown()
        }
    }
}
