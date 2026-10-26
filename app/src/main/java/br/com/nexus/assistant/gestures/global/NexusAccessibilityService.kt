package br.com.nexus.assistant.gestures.global

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import br.com.nexus.assistant.gestures.HandPoint
import br.com.nexus.assistant.gestures.MouseEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Opt-in gesture dispatch only: no window-content retrieval and no event collection. */
class NexusAccessibilityService : AccessibilityService() {
    private lateinit var windows: WindowManager
    private var cursor: CursorView? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var stopButton: Button? = null
    private var gestures: PointerGestures? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windows = getSystemService(WindowManager::class.java)
        serviceInfo = serviceInfo.apply {
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0
        }
        instance = this
        mutableConnected.value = true
        gestures = PointerGestures(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { GlobalMouseRuntime.stop(this); endSession() }

    internal fun beginSession() {
        if (cursor != null) return
        gestures?.beginSession()
        val size = (64 * resources.displayMetrics.density).toInt()
        val pointer = CursorView(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }
        val params = WindowManager.LayoutParams(size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        val button = Button(this).apply {
            text = "PARAR NEXUS"
            contentDescription = "Parar imediatamente o mouse global e a câmera do NEXUS"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 24, 32)); cornerRadius = 12 * resources.displayMetrics.density
                setStroke((2 * resources.displayMetrics.density).toInt(), Color.CYAN)
            }
            setOnClickListener { GlobalMouseRuntime.stop(this@NexusAccessibilityService) }
        }
        val buttonParams = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.END
                x = (8 * resources.displayMetrics.density).toInt()
                y = (72 * resources.displayMetrics.density).toInt()
            }
        try {
            windows.addView(pointer, params)
            cursor = pointer; cursorParams = params
            windows.addView(button, buttonParams)
            stopButton = button
        } catch (error: Exception) {
            endSession()
            throw error
        }
    }

    internal fun updateCursor(point: HandPoint?) {
        val pointer = cursor ?: return
        val params = cursorParams ?: return
        pointer.visibility = if (point == null) View.GONE else View.VISIBLE
        if (point != null) {
            val size = screenSize()
            val nextX = (point.x.coerceIn(0f, 1f) * size.x - params.width / 2).toInt().coerceIn(0, (size.x - params.width).coerceAtLeast(0))
            val nextY = (point.y.coerceIn(0f, 1f) * size.y - params.height / 2).toInt().coerceIn(0, (size.y - params.height).coerceAtLeast(0))
            if (params.x != nextX || params.y != nextY) {
                params.x = nextX; params.y = nextY
                runCatching { windows.updateViewLayout(pointer, params) }
            }
        }
    }

    internal fun dispatch(event: MouseEvent) { gestures?.accept(event) }
    internal fun endSession() {
        gestures?.cancel()
        cursor?.let { runCatching { windows.removeView(it) } }
        stopButton?.let { runCatching { windows.removeView(it) } }
        cursor = null; cursorParams = null; stopButton = null
    }

    internal fun screenSize(): Point = if (Build.VERSION.SDK_INT >= 30) {
        windows.currentWindowMetrics.bounds.let { Point(it.width(), it.height()) }
    } else {
        @Suppress("DEPRECATION")
        Point().also { windows.defaultDisplay.getRealSize(it) }
    }

    override fun onDestroy() {
        GlobalMouseRuntime.stop(this)
        endSession()
        gestures = null
        if (instance === this) instance = null
        mutableConnected.value = false
        super.onDestroy()
    }

    companion object {
        private val mutableConnected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()
        @Volatile internal var instance: NexusAccessibilityService? = null
            private set
    }
}

private class CursorView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = width * .22f
        paint.color = Color.argb(45, 49, 231, 250); paint.style = Paint.Style.FILL
        canvas.drawCircle(width / 2f, height / 2f, radius * 1.6f, paint)
        paint.color = Color.CYAN; paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density * 2
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(width / 2f, height / 2f, radius * .18f, paint)
    }
}

/** API 26 stroke continuations preserve a real held touch across camera frames. */
private class PointerGestures(private val service: NexusAccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private var stroke: GestureDescription.StrokeDescription? = null
    private var last = HandPoint(.5f, .5f)
    private var pendingMove: HandPoint? = null
    private var pendingTap: HandPoint? = null
    private var pendingDown: HandPoint? = null
    private var finishing = false
    private var inFlight = false
    private var generation = 0L

    fun beginSession() {
        // Preserve an in-flight release from a previous session until its callback;
        // resetting it early could leave Android holding the old touch.
        cancel()
    }

    fun accept(event: MouseEvent) {
        when (event) {
            is MouseEvent.Tap -> {
                // The first short pinch already dispatched a tap. Dispatching this second
                // release once lets Android recognize the double tap without a third tap.
                pendingTap = event.point
                pendingDown = null
                if (stroke != null) finishing = true
                pump()
            }
            is MouseEvent.Down -> {
                pendingTap = null
                pendingDown = event.point
                if (stroke != null) finishing = true
                pump()
            }
            is MouseEvent.Move -> {
                if (pendingDown != null) pendingDown = event.point
                else if (stroke != null) { pendingMove = event.point; pump() }
            }
            is MouseEvent.Up -> {
                if (pendingDown != null) {
                    // A queued press must not start after its physical gesture ended.
                    pendingDown = null
                } else if (stroke != null) { pendingMove = event.point; finishing = true; pump() }
            }
            MouseEvent.Cancel -> cancel()
        }
    }

    private fun pump() {
        if (inFlight) return
        val current = stroke
        if (current != null) {
            val point = pendingMove ?: last
            if (pendingMove == null && !finishing) return
            val finish = finishing
            pendingMove = null
            val next = current.continueStroke(path(last, point), 0, if (finish) 30 else 70, !finish)
            last = point
            stroke = if (finish) null else next
            finishing = false
            submit(next)
        } else {
            val down = pendingDown
            if (down != null) {
                pendingDown = null
                last = down
                val first = GestureDescription.StrokeDescription(path(down, down), 0, 10, true)
                stroke = first
                submit(first)
            } else pendingTap?.let { point ->
                pendingTap = null
                submit(GestureDescription.StrokeDescription(path(point, point), 0, 35))
            }
        }
    }

    private fun submit(value: GestureDescription.StrokeDescription) {
        val ticket = ++generation
        inFlight = true
        val accepted = service.dispatchGesture(GestureDescription.Builder().addStroke(value).build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (ticket != generation) return
                    inFlight = false
                    pump()
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (ticket != generation) return
                    inFlight = false; stroke = null; pendingMove = null; pendingTap = null; pendingDown = null; finishing = false
                }
            }, main)
        if (!accepted) { inFlight = false; stroke = null; pendingMove = null; pendingTap = null; pendingDown = null; finishing = false }
    }

    private fun path(from: HandPoint, to: HandPoint): Path {
        val size = service.screenSize()
        fun x(point: HandPoint) = point.x.coerceIn(.001f, .999f) * size.x
        fun y(point: HandPoint) = point.y.coerceIn(.001f, .999f) * size.y
        return Path().apply { moveTo(x(from), y(from)); lineTo(x(to), y(to)) }
    }

    fun cancel() {
        pendingTap = null
        pendingDown = null
        pendingMove = null
        if (stroke != null) { finishing = true; pump() }
    }
}
