package br.com.nexus.assistant.device.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.nexus.assistant.gestures.global.GlobalMouseRuntime
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/** Explicitly started periodic local image classification; no upload or claimed video LLM. */
@Composable
fun NexusVisionLivePanel(
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
    onSpeak: (String) -> Unit = {},
    onActiveChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val hasFront = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) }
    val hasRear = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA) }
    val global by GlobalMouseRuntime.state.collectAsState()
    var active by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(!hasRear && hasFront) }
    var preview by remember { mutableStateOf(true) }
    var interval by remember { mutableFloatStateOf(1.5f) }
    var result by remember { mutableStateOf<ImageObservation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val activeCallback by rememberUpdatedState(onActiveChanged)
    fun stop() { active = false; activeCallback(false) }
    fun start() {
        if (GlobalMouseRuntime.state.value.active) { error = "Pare o mouse global antes de usar a câmera no Vision."; return }
        activeCallback(true)
        result = null; error = null; active = true
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else error = "Autorize a câmera para iniciar a análise local ao vivo."
    }
    LaunchedEffect(global.active) { if (global.active && active) stop() }
    DisposableEffect(Unit) { onDispose { activeCallback(false) } }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("NEXUS VISION · objetos ao vivo", style = MaterialTheme.typography.titleMedium)
            Text("Avalia quadros da câmera neste aparelho, sem enviar imagens. Reconhece categorias aproximadas de objetos; não interpreta a cena inteira nem ações de um vídeo.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (active) stop()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) start()
                    else permission.launch(Manifest.permission.CAMERA)
                }, enabled = active || (hasFront || hasRear) && !global.active) { Text(if (active) "Parar Vision ao vivo" else "Iniciar Vision ao vivo") }
                if (hasFront && hasRear) OutlinedButton(onClick = { front = !front; result = null }) { Text("Trocar câmera") }
            }
            if (global.active) Text("Câmera em uso pelo mouse global. Encerre essa sessão antes de iniciar o Vision.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(preview, { preview = it })
                Text("Mostrar imagem da câmera")
            }
            Text("Intervalo entre análises: ${String.format(Locale.ROOT, "%.1f", interval)} s", style = MaterialTheme.typography.bodySmall)
            Slider(interval, { interval = it }, valueRange = .75f..5f)
            if (active) {
                Box(Modifier.fillMaxWidth().height(if (preview) 260.dp else 80.dp).background(Color.Black)) {
                    ObjectCamera(front, preview, (interval * 1_000).toLong(), Modifier.fillMaxSize(),
                        onResult = { result = it }, onFailure = { message -> error = message; stop(); onError(message) })
                    Text("● Câmera ativa · análise local", Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = .7f)).padding(8.dp), color = Color(0xFF31E7FA))
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            result?.let { observation ->
                Text(when {
                    observation.lowDetail -> "Não há detalhes suficientes neste quadro. Mostre um objeto bem iluminado."
                    observation.candidates.isEmpty() -> "Nenhuma categoria atingiu a pontuação mínima."
                    observation.confidentCandidate == null -> "Resultado incerto. Aproxime o objeto ou mude o enquadramento."
                    else -> "Possível objeto: ${observation.confidentCandidate!!.label}"
                })
                observation.candidates.forEach { candidate ->
                    Text("${candidate.label} · pontuação ${String.format(Locale.ROOT, "%.2f", candidate.score)}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Último quadro: ${observation.elapsedMs} ms · categorias do modelo em inglês", style = MaterialTheme.typography.labelSmall)
                if (observation.confidentCandidate != null) OutlinedButton(onClick = {
                    onSpeak("O classificador sugere ${observation.confidentCandidate!!.label}. É uma hipótese, não uma identificação garantida.")
                }) { Text("Ouvir resultado") }
            }
            Text("A câmera pausa ao sair desta tela e pode retomar ao voltar durante a sessão. PARAR encerra a sessão. Os indicadores de privacidade do Android permanecem.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ObjectCamera(front: Boolean, showPreview: Boolean, intervalMs: Long, modifier: Modifier,
                         onResult: (ImageObservation) -> Unit, onFailure: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val resultCallback by rememberUpdatedState(onResult)
    val errorCallback by rememberUpdatedState(onFailure)
    val currentInterval by rememberUpdatedState(intervalMs)
    val previewView = remember(context) { PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FIT_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    } }
    if (showPreview) AndroidView(factory = { previewView }, modifier = modifier) else Box(modifier)
    DisposableEffect(context, owner, front, showPreview) {
        val closed = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
        val classifier = LocalObjectClassifier(context)
        val pending = AtomicReference<ImageObservation?>(null)
        val deliver = Runnable { pending.getAndSet(null)?.let { if (!closed.get()) resultCallback(it) } }
        val failed = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(480, 360), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()
        val previewCase = Preview.Builder().setResolutionSelector(selector).build()
        val analysis = ImageAnalysis.Builder().setResolutionSelector(selector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
        var nextAnalysisAt = 0L
        analysis.setAnalyzer(executor) { frame ->
            var original: Bitmap? = null
            var oriented: Bitmap? = null
            try {
                val now = SystemClock.elapsedRealtime()
                if (closed.get() || failed.get() || now < nextAnalysisAt) return@setAnalyzer
                nextAnalysisAt = now + currentInterval.coerceAtLeast(750)
                original = frame.toBitmap()
                val transform = Matrix().apply { postRotate(frame.imageInfo.rotationDegrees.toFloat()) }
                oriented = Bitmap.createBitmap(original!!, 0, 0, original!!.width, original!!.height, transform, false)
                val observation = runBlocking { classifier.classify(oriented!!) }
                pending.set(observation)
                main.removeCallbacks(deliver)
                main.post(deliver)
            } catch (cancelled: CancellationException) {
                // Disposing a camera session discards an already-started result.
            } catch (failure: Exception) {
                if (failed.compareAndSet(false, true)) main.post { if (!closed.get()) errorCallback(failure.message ?: "A análise local deste quadro falhou.") }
            } catch (_: OutOfMemoryError) {
                main.post { if (!closed.get()) errorCallback("Falta memória para a câmera e a classificação. Feche outros aplicativos e tente novamente.") }
            } finally {
                if (oriented != null && oriented !== original) oriented?.recycle()
                original?.recycle()
                frame.close()
            }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!closed.get()) try {
                provider = future.get()
                val camera = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                check(provider!!.hasCamera(camera))
                if (showPreview) {
                    previewCase.setSurfaceProvider(previewView.surfaceProvider)
                    provider!!.bindToLifecycle(owner, camera, previewCase, analysis)
                } else provider!!.bindToLifecycle(owner, camera, analysis)
            } catch (_: Exception) { errorCallback("Não foi possível abrir a câmera para a análise ao vivo.") }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            closed.set(true)
            analysis.clearAnalyzer()
            provider?.unbind(previewCase, analysis)
            classifier.close()
            pending.set(null)
            main.removeCallbacksAndMessages(null)
            executor.shutdown()
        }
    }
}
