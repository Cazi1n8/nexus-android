package br.com.nexus.assistant.gestures

import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Compatibility wrapper for the original single-cube destination. */
@Composable
internal fun CubeScene(state: CubeState, modifier: Modifier = Modifier) {
    SceneView(SceneState(listOf(SceneObject("initial-cube", transform = state)), "initial-cube"), modifier)
}

/** Actual indexed OpenGL ES meshes; camera composition does not imply spatial AR. */
@Composable
internal fun SceneView(state: SceneState, modifier: Modifier = Modifier, onRenderError: (String) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentError = rememberUpdatedState(onRenderError)
    val closed = remember { AtomicBoolean(false) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val renderer = remember {
        SceneGlRenderer { message -> main.post { if (!closed.get()) currentError.value(message) } }
    }
    val surface = remember(context) {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            setZOrderOnTop(true)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }
    AndroidView(factory = { surface }, modifier = modifier, update = {
        renderer.state = state
        it.requestRender()
    })
    DisposableEffect(surface, owner) {
        closed.set(false)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { surface.onResume(); surface.requestRender() }
                Lifecycle.Event.ON_PAUSE -> surface.onPause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) surface.onResume()
        onDispose {
            closed.set(true)
            owner.lifecycle.removeObserver(observer)
            surface.onPause()
            // GLSurfaceView owns and releases the EGL context and its program.
        }
    }
}

/** Internal for actual Android/EGL rendering tests, never for mouse event injection. */
internal class SceneGlRenderer(private val reportError: (String) -> Unit = {}) : GLSurfaceView.Renderer {
    @Volatile var state = SceneState()
    private var program = 0
    private var position = 0
    private var matrixHandle = 0
    private var colorHandle = 0
    private var aspect = 1f
    private var reportedFailure = false
    private val projection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val geometry by lazy { SceneShape.entries.associateWith { Buffers(SceneMeshes.forShape(it)) } }

    private class Buffers(mesh: SceneMesh) {
        val vertices: FloatBuffer = ByteBuffer.allocateDirect(mesh.vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(mesh.vertices); position(0) }
        val triangles: ShortBuffer = ByteBuffer.allocateDirect(mesh.triangles.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(mesh.triangles); position(0) }
        val edges: ShortBuffer = ByteBuffer.allocateDirect(mesh.edges.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(mesh.edges); position(0) }
        val triangleCount = mesh.triangles.size
        val edgeCount = mesh.edges.size
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        reportedFailure = false
        try {
            val vertex = shader(GLES20.GL_VERTEX_SHADER,
                "uniform mat4 uMatrix; attribute vec3 aPosition; varying float vShade; void main(){vShade=0.75+0.25*clamp(aPosition.y,-1.0,1.0);gl_Position=uMatrix*vec4(aPosition,1.0);}")
            val fragment = shader(GLES20.GL_FRAGMENT_SHADER,
                "precision mediump float; uniform vec4 uColor; varying float vShade; void main(){gl_FragColor=vec4(uColor.rgb*vShade,uColor.a);}")
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment); GLES20.glLinkProgram(program)
            GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
            check(linked[0] != 0) { "OpenGL program link failed" }
            position = GLES20.glGetAttribLocation(program, "aPosition")
            matrixHandle = GLES20.glGetUniformLocation(program, "uMatrix")
            colorHandle = GLES20.glGetUniformLocation(program, "uColor")
            check(position >= 0 && matrixHandle >= 0 && colorHandle >= 0)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        } catch (_: RuntimeException) { fail() }
          catch (_: LinkageError) { fail() }
          catch (_: OutOfMemoryError) { fail() }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.coerceAtLeast(1).toFloat() / height.coerceAtLeast(1)
        Matrix.orthoM(projection, 0, -aspect, aspect, -1f, 1f, -10f, 10f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return
        try {
            val snapshot = state
            GLES20.glUseProgram(program)
            GLES20.glEnableVertexAttribArray(position)
            snapshot.objects.take(SceneState.MAX_OBJECTS).forEachIndexed { index, item ->
                val transform = item.transform
                val mesh = geometry.getValue(item.shape)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, (transform.center.x * 2f - 1f) * aspect,
                    1f - transform.center.y * 2f, index * .03f)
                Matrix.rotateM(model, 0, Math.toDegrees(transform.yaw.toDouble()).toFloat(), 0f, 1f, 0f)
                Matrix.rotateM(model, 0, Math.toDegrees(transform.pitch.toDouble()).toFloat(), 1f, 0f, 0f)
                Matrix.scaleM(model, 0, transform.scale, transform.scale, transform.scale)
                Matrix.multiplyMM(mvp, 0, projection, 0, model, 0)
                GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0)
                GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, mesh.vertices)
                val selected = item.id == snapshot.selectedObjectId
                val r = ((item.colorArgb ushr 16) and 255) / 255f
                val g = ((item.colorArgb ushr 8) and 255) / 255f
                val b = (item.colorArgb and 255) / 255f
                GLES20.glUniform4f(colorHandle, r, g, b, if (transform.selected) .25f else if (selected) .2f else .12f)
                GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
                GLES20.glPolygonOffset(1f,1f)
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.triangleCount, GLES20.GL_UNSIGNED_SHORT, mesh.triangles)
                GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
                if (transform.selected) GLES20.glUniform4f(colorHandle, 1f, 1f, 1f, 1f)
                else GLES20.glUniform4f(colorHandle, r, g, b, if (selected) 1f else .8f)
                GLES20.glLineWidth(if (selected) 2f else 1f)
                GLES20.glDrawElements(GLES20.GL_LINES, mesh.edgeCount, GLES20.GL_UNSIGNED_SHORT, mesh.edges)
            }
            GLES20.glDisableVertexAttribArray(position)
        } catch (_: RuntimeException) { fail() }
          catch (_: OutOfMemoryError) { fail() }
    }

    /** Caller must own the current EGL context (instrumentation uses this before destruction). */
    fun releaseOnGlThread() { if (program != 0) GLES20.glDeleteProgram(program); program = 0 }

    private fun fail() {
        releaseOnGlThread()
        if (!reportedFailure) {
            reportedFailure = true
            reportError("Não foi possível renderizar a cena 3D neste aparelho. A câmera pode ser encerrada; os outros recursos continuam disponíveis.")
        }
    }

    private fun shader(kind: Int, code: String): Int {
        val result = GLES20.glCreateShader(kind)
        GLES20.glShaderSource(result, code); GLES20.glCompileShader(result)
        val status = IntArray(1)
        GLES20.glGetShaderiv(result, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) { GLES20.glDeleteShader(result); error("OpenGL shader compile failed") }
        return result
    }
}
