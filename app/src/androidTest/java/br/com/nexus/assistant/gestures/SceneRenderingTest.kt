package br.com.nexus.assistant.gestures

import android.opengl.EGL14
import android.opengl.GLES20
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real shader compilation and off-screen GLES rendering, without camera/hand simulation. */
@RunWith(AndroidJUnit4::class)
class SceneRenderingTest {
    @Test(timeout = 30_000L)
    fun sixShapesAndMultiObjectSceneRenderOnActualEglContext() {
        assumeTrue("Opt in with nexusSceneSmoke=true on an Android test device",
            InstrumentationRegistry.getArguments().getString("nexusSceneSmoke") == "true")
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertNotEquals(EGL14.EGL_NO_DISPLAY, display)
        val version = IntArray(2)
        assertTrue(EGL14.eglInitialize(display, version, 0, version, 1))
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE)
        assertTrue(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0))
        assertTrue("GLES2 pbuffer configuration unavailable", count[0] > 0)
        val config = requireNotNull(configs[0])
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        assertNotEquals(EGL14.EGL_NO_CONTEXT, context)
        val surface = EGL14.eglCreatePbufferSurface(display, config,
            intArrayOf(EGL14.EGL_WIDTH, 128, EGL14.EGL_HEIGHT, 128, EGL14.EGL_NONE), 0)
        assertNotEquals(EGL14.EGL_NO_SURFACE, surface)
        assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
        val failures = mutableListOf<String>()
        val renderer = SceneGlRenderer { failures.add(it) }
        try {
            renderer.onSurfaceCreated(null, null)
            renderer.onSurfaceChanged(null, 128, 128)
            SceneShape.entries.forEach { shape ->
                renderer.state = SceneState(listOf(SceneObject("shape", shape)))
                renderer.onDrawFrame(null)
                GLES20.glFinish()
                assertEquals("GLES error rendering $shape", GLES20.GL_NO_ERROR, GLES20.glGetError())
                val pixels = ByteBuffer.allocateDirect(128 * 128 * 4).order(ByteOrder.nativeOrder())
                GLES20.glReadPixels(0, 0, 128, 128, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
                val colored = (0 until 128 * 128).count { index -> (pixels.get(index * 4 + 3).toInt() and 255) > 0 }
                assertTrue("$shape did not produce pixels", colored > 50)
            }
            renderer.state = SceneState(SceneShape.entries.mapIndexed { index, shape ->
                SceneObject("shape-$index", shape, CubeState(center = HandPoint(.2f + index % 3 * .3f,
                    .3f + index / 3 * .4f), scale = .18f))
            })
            renderer.onDrawFrame(null)
            GLES20.glFinish()
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
            assertTrue("Renderer reported a failure", failures.isEmpty())
        } finally {
            renderer.releaseOnGlThread()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}
