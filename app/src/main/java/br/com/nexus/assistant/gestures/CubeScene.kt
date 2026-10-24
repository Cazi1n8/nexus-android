package br.com.nexus.assistant.gestures

import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** A real OpenGL ES 2.0 surface, composited over the camera. No spatial AR is claimed. */
@Composable
internal fun CubeScene(state: CubeState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val renderer = remember { CubeRenderer() }
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
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { surface.onResume(); surface.requestRender() }
                Lifecycle.Event.ON_PAUSE -> surface.onPause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) surface.onResume()
        onDispose { owner.lifecycle.removeObserver(observer); surface.onPause() }
    }
}

private class CubeRenderer : GLSurfaceView.Renderer {
    @Volatile var state = CubeState()
    private var program = 0
    private var position = 0
    private var matrixHandle = 0
    private var colorHandle = 0
    private var aspect = 1f
    private val projection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val vertices = ByteBuffer.allocateDirect(8 * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, -1f, 1f, -1f,
            -1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, 1f)); position(0)
    }
    private val indices = ByteBuffer.allocateDirect(24 * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
        put(shortArrayOf(0, 1, 1, 2, 2, 3, 3, 0, 4, 5, 5, 6, 6, 7, 7, 4, 0, 4, 1, 5, 2, 6, 3, 7)); position(0)
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vertex = shader(GLES20.GL_VERTEX_SHADER, "uniform mat4 uMatrix; attribute vec3 aPosition; void main(){gl_Position=uMatrix*vec4(aPosition,1.0);}")
        val fragment = shader(GLES20.GL_FRAGMENT_SHADER, "precision mediump float; uniform vec4 uColor; void main(){gl_FragColor=uColor;}")
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment); GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { "Falha ao vincular o renderer OpenGL: ${GLES20.glGetProgramInfoLog(program)}" }
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
        position = GLES20.glGetAttribLocation(program, "aPosition")
        matrixHandle = GLES20.glGetUniformLocation(program, "uMatrix")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.orthoM(projection, 0, -aspect, aspect, -1f, 1f, -10f, 10f)
    }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val snapshot = state
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, (snapshot.center.x * 2f - 1f) * aspect, 1f - snapshot.center.y * 2f, 0f)
        Matrix.rotateM(model, 0, Math.toDegrees(snapshot.yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(model, 0, Math.toDegrees(snapshot.pitch.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.scaleM(model, 0, snapshot.scale, snapshot.scale, snapshot.scale)
        Matrix.multiplyMM(mvp, 0, projection, 0, model, 0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0)
        if (snapshot.selected) GLES20.glUniform4f(colorHandle, 1f, 1f, 1f, 1f)
        else GLES20.glUniform4f(colorHandle, .19f, .91f, .98f, .95f)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, vertices)
        GLES20.glLineWidth(2f)
        GLES20.glDrawElements(GLES20.GL_LINES, 24, GLES20.GL_UNSIGNED_SHORT, indices)
        GLES20.glDisableVertexAttribArray(position)
    }
    private fun shader(kind: Int, code: String): Int {
        val result = GLES20.glCreateShader(kind)
        GLES20.glShaderSource(result, code); GLES20.glCompileShader(result)
        val status = IntArray(1)
        GLES20.glGetShaderiv(result, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { "Falha no shader OpenGL: ${GLES20.glGetShaderInfoLog(result)}" }
        return result
    }
}
