package br.com.nexus.assistant.device

import android.Manifest
import android.content.pm.PackageManager
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import br.com.nexus.assistant.ui.icons.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Binds only this panel's use cases; no camera, image or audio is sent to a server here. */
@Composable
fun CameraPanel(
    modifier: Modifier = Modifier,
    frontCamera: Boolean = false,
    onCapture: (File) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onCaptureCurrent by rememberUpdatedState(onCapture)
    val onErrorCurrent by rememberUpdatedState(onError)
    val active = remember { AtomicBoolean(true) }
    val bindingGeneration = remember { AtomicInteger(0) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var ready by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        active.set(true)
        onDispose { active.set(false) }
    }

    DisposableEffect(lifecycleOwner, frontCamera, previewView) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var imageCapture: ImageCapture? = null
        bindingGeneration.incrementAndGet()
        capturing = false
        ready = false
        capture = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onErrorCurrent("Autorize a câmera para abrir a visualização ao vivo.")
        } else {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) {
                    try {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        val preferred = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        val alternative = if (frontCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                        val selector = when {
                            cameraProvider.hasCamera(preferred) -> preferred
                            cameraProvider.hasCamera(alternative) -> alternative
                            else -> null
                        }
                        if (selector == null) {
                            onErrorCurrent("Nenhuma câmera está disponível neste aparelho.")
                        } else {
                            preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .setJpegQuality(85)
                                .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                                .build()
                            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview!!, imageCapture!!)
                            capture = imageCapture
                            ready = true
                        }
                    } catch (_: Exception) {
                        onErrorCurrent("Não foi possível abrir a câmera. Feche outros aplicativos que possam estar usando a câmera e tente novamente.")
                    }
                }
            }, executor)
        }
        onDispose {
            disposed = true
            bindingGeneration.incrementAndGet()
            ready = false
            capture = null
            // Never unbindAll(): other app-owned use cases must not be stopped by this panel.
            preview?.let { provider?.unbind(it) }
            imageCapture?.let { provider?.unbind(it) }
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.76f)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (ready) "● Câmera ativa · captura somente ao tocar" else "Preparando a câmera…",
                color = if (ready) Color(0xFF31E6EF) else Color.White,
                style = MaterialTheme.typography.labelMedium
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = ready && !capturing,
                onClick = {
                    val useCase = capture
                    if (useCase != null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        capturing = true
                        val generation = bindingGeneration.get()
                        var pendingFile: File? = null
                        try {
                            val file = File.createTempFile("nexus-camera-", ".jpg", context.cacheDir)
                            pendingFile = file
                            useCase.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                            useCase.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        if (generation == bindingGeneration.get()) capturing = false
                                        if (active.get() && generation == bindingGeneration.get() && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                            onCaptureCurrent(file)
                                        } else file.delete()
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        if (generation == bindingGeneration.get()) capturing = false
                                        file.delete()
                                        if (active.get() && generation == bindingGeneration.get()) onErrorCurrent("Não foi possível capturar a imagem. Verifique o espaço disponível e tente novamente.")
                                    }
                                }
                            )
                        } catch (_: Exception) {
                            capturing = false
                            pendingFile?.delete()
                            onErrorCurrent("Não foi possível iniciar a captura. Verifique o espaço disponível e tente novamente.")
                        }
                    }
                }
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(if (capturing) "Capturando…" else "Capturar imagem")
            }
        }
    }
}
