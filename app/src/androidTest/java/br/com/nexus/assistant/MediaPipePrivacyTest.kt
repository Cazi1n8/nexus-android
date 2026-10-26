package br.com.nexus.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.core.logging.TasksStatsLoggerFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Confirms the documented build-time variant is actually selected on Android.
 * This checks the SDK factory, not all network traffic or Play Protect approval.
 */
@RunWith(AndroidJUnit4::class)
class MediaPipePrivacyTest {
    @Test
    fun packagedFactorySelectsUpstreamNoOpLogger() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val logger = TasksStatsLoggerFactory.create(context, "HandLandmarker", "IMAGE")
        assertEquals(
            "The packaged SDK must use the documented upstream no-op factory",
            "com.google.mediapipe.tasks.core.logging.TasksStatsDummyLogger",
            logger.javaClass.name,
        )
        logger.logSessionStart()
        logger.logSessionClone()
        logger.recordCpuInputArrival(0L)
        logger.recordGpuInputArrival(0L)
        logger.recordInvocationEnd(1L)
        logger.logInitError()
        logger.logSessionEnd()
    }
}
