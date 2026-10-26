package br.com.nexus.assistant.gestures

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.nexus.assistant.device.DeviceCapabilities
import kotlinx.coroutines.delay

/** Activity-scoped by rememberGestureSession at the navigation root, never by a destination. */
@Stable
class GestureSession internal constructor(context: Context) {
    private val capabilities = DeviceCapabilities.read(context)
    private val preferences = context.applicationContext.getSharedPreferences("nexus_gestures", Context.MODE_PRIVATE)
    private val controller = MouseController()
    internal var dispatch: ((MouseEvent) -> Unit)? = null
    var mouseActive by mutableStateOf(false); private set
    var frontCamera by mutableStateOf(capabilities.hasFrontCamera); private set
    var frame by mutableStateOf(HandFrame()); private set
    var mouse by mutableStateOf(MouseUpdate()); private set
    var error by mutableStateOf<String?>(null); private set
    var sensitivity by mutableFloatStateOf(preferences.getFloat("sensitivity", 1.3f).coerceIn(.6f, 2.3f)); private set
    var pinchThreshold by mutableFloatStateOf(preferences.getFloat("pinch", .35f).coerceIn(.2f, .55f)); private set
    var showPreview by mutableStateOf(preferences.getBoolean("show_preview", false)); private set
    var clicks by mutableIntStateOf(0); private set
    var doubleClicks by mutableIntStateOf(0); private set

    fun startMouse() {
        controller.reset().events.forEach { dispatch?.invoke(it) }
        error = null; frame = HandFrame(); mouse = MouseUpdate(); mouseActive = true
    }
    fun stopMouse() {
        controller.reset().events.forEach { dispatch?.invoke(it) }
        mouseActive = false; mouse = MouseUpdate(); frame = HandFrame()
    }
    /** Called before deleting local data; the repository clears the persistent preferences. */
    fun resetPreferences() {
        stopMouse()
        sensitivity = 1.3f; pinchThreshold = .35f; frontCamera = capabilities.hasFrontCamera
        showPreview = false
        clicks = 0; doubleClicks = 0; error = null
    }
    fun switchCamera() {
        if (!capabilities.hasFrontCamera || !capabilities.hasRearCamera) return
        controller.reset().events.forEach { dispatch?.invoke(it) }
        frontCamera = !frontCamera; mouse = MouseUpdate(); frame = HandFrame()
    }
    fun updateSensitivity(value: Float) {
        sensitivity = value.coerceIn(.6f, 2.3f)
        preferences.edit().putFloat("sensitivity", sensitivity).apply()
    }
    fun updatePinchThreshold(value: Float) {
        pinchThreshold = value.coerceIn(.2f, .55f)
        preferences.edit().putFloat("pinch", pinchThreshold).apply()
    }
    fun updateShowPreview(value: Boolean) {
        showPreview = value
        preferences.edit().putBoolean("show_preview", value).apply()
    }
    fun calibrate() { frame.hands.firstOrNull()?.let { controller.calibrate(it.index, sensitivity) } }
    internal fun onFrame(next: HandFrame) {
        if (!mouseActive) return
        frame = next
        mouse = controller.update(next, sensitivity, pinchThreshold)
        mouse.events.forEach { event ->
            if (event is MouseEvent.Tap) { clicks++; if (event.doubleClick) doubleClicks++ }
            dispatch?.invoke(event)
        }
    }
    internal fun stale() {
        mouse = controller.lost()
        mouse.events.forEach { dispatch?.invoke(it) }
    }
    internal fun fail(message: String) { stopMouse(); error = message }
}

@Composable
fun rememberGestureSession(): GestureSession {
    val context = LocalContext.current
    return remember(context) { GestureSession(context) }
}

/** Must be the last child of the full-screen root Box. Cursor Canvas never intercepts touches. */
@Composable
fun GestureSessionHost(
    session: GestureSession,
    onNavigateBack: () -> Unit = {},
    onOpenCommands: () -> Unit = {}
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val dispatcher = remember(context) { context.activityOrNull()?.let(::AppPointerDispatcher) }
    DisposableEffect(session, owner, dispatcher) {
        session.dispatch = { dispatcher?.dispatch(it) }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.stopMouse()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            session.stopMouse()
            session.dispatch = null
            owner.lifecycle.removeObserver(observer)
            dispatcher?.close()
        }
    }
    LaunchedEffect(session.mouseActive) {
        while (session.mouseActive) {
            delay(250)
            if (session.frame.timestampMs != 0L && SystemClock.elapsedRealtime() - session.frame.timestampMs > 800L) session.stale()
        }
    }
    if (!session.mouseActive) return
    if (dispatcher == null) {
        LaunchedEffect(Unit) { session.fail("O mouse precisa de uma janela Android ativa.") }
        return
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { layout ->
        val origin = layout.positionInWindow()
        dispatcher.updateBounds(origin.x, origin.y, layout.size.width.toFloat(), layout.size.height.toFloat())
    }) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Cursor virtual do NEXUS" }) {
            session.mouse.cursor?.let { point ->
                val center = Offset(point.x * size.width, point.y * size.height)
                val neon = if (session.mouse.pinching || session.mouse.scrolling) Color.White else Color(0xFF31E7FA)
                drawCircle(neon.copy(alpha = .14f), 22.dp.toPx(), center)
                drawCircle(neon, 12.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                drawCircle(neon, 3.dp.toPx(), center)
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 24.dp).width(146.dp)
            .background(Color(0xF0101820)).border(1.dp, Color(0xFF31E7FA)).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Câmera · mouse ativo", style = MaterialTheme.typography.labelSmall, color = Color(0xFF31E7FA))
            if (session.showPreview) {
                Box(Modifier.fillMaxWidth().height(126.dp)) {
                    HandCamera(session.frontCamera, Modifier.fillMaxSize(), session::onFrame, session::fail, pointerMode = true)
                    HandSkeleton(session.frame, Modifier.fillMaxSize())
                }
            } else {
                HandCamera(session.frontCamera, Modifier.size(1.dp), session::onFrame, session::fail, showPreview = false, pointerMode = true)
            }
            Text(when {
                session.frame.timestampMs == 0L -> "Iniciando rastreador…"
                session.mouse.scrolling -> "Rolando · mova a mão"
                session.mouse.dragging -> "Arrastando · solte a pinça"
                session.mouse.cursor == null -> "Mostre o indicador"
                else -> "${session.frame.processingMs} ms · ${session.mouse.configuration.label}"
            }, style = MaterialTheme.typography.labelSmall)
            Button(onClick = session::stopMouse, modifier = Modifier.fillMaxWidth()
                .semantics { contentDescription = "Parar mouse e câmera imediatamente" }) { Text("PARAR") }
        }
    }
}

private tailrec fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}

/** Official in-app input dispatch only. It neither reads nor controls another application's UI. */
private class AppPointerDispatcher(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private var downAt = 0L
    private var last = HandPoint(.5f, .5f)
    private var pressed = false
    private var tapUp: Runnable? = null
    private var bounds: RectF? = null

    fun updateBounds(left: Float, top: Float, width: Float, height: Float) {
        if (width > 0f && height > 0f) bounds = RectF(left, top, left + width, top + height)
    }

    fun dispatch(event: MouseEvent) {
        when (event) {
            is MouseEvent.Tap -> {
                cancel()
                down(event.point)
                tapUp = Runnable { if (pressed) up(event.point) }.also { handler.postDelayed(it, 24L) }
            }
            is MouseEvent.Down -> { cancel(); down(event.point) }
            is MouseEvent.Move -> if (pressed) send(MotionEvent.ACTION_MOVE, event.point)
            is MouseEvent.Up -> if (pressed) up(event.point)
            MouseEvent.Cancel -> cancel()
        }
    }
    private fun down(point: HandPoint) {
        if (activity.isFinishing || activity.isDestroyed) return
        downAt = SystemClock.uptimeMillis(); pressed = true; send(MotionEvent.ACTION_DOWN, point)
    }
    private fun up(point: HandPoint) { send(MotionEvent.ACTION_UP, point); pressed = false }
    private fun send(action: Int, point: HandPoint) {
        val decor = activity.window.decorView
        if (decor.width <= 0 || decor.height <= 0) return
        val area = bounds ?: RectF(0f, 0f, decor.width.toFloat(), decor.height.toFloat())
        last = point
        val motion = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action,
            area.left + point.x.coerceIn(0f, .999f) * area.width(),
            area.top + point.y.coerceIn(0f, .999f) * area.height(), 0)
        motion.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        try { activity.dispatchTouchEvent(motion) } finally { motion.recycle() }
    }
    private fun cancel() {
        tapUp?.let(handler::removeCallbacks); tapUp = null
        if (pressed) send(MotionEvent.ACTION_CANCEL, last)
        pressed = false
    }
    fun close() = cancel()
}
