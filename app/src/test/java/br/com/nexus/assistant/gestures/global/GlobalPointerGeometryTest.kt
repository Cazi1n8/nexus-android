package br.com.nexus.assistant.gestures.global

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalPointerGeometryTest {
    @Test fun cursorCenterMatchesDispatchAtEdgesAndMiddle() {
        for (extent in listOf(720, 1280, 1600, 2400)) {
            for (pointer in listOf(64, 128, 192)) {
                for (normalized in listOf(-.4f, 0f, .001f, .12f, .5f, .99f, 1f, 1.4f)) {
                    val center = GlobalPointerGeometry.overlayOrigin(normalized, extent, pointer) + pointer / 2f
                    assertEquals(GlobalPointerGeometry.pixel(normalized, extent), center, .51f)
                }
            }
        }
    }

    @Test fun decorativeHaloMayExtendBeyondDisplayWithoutChangingTouch() {
        assertTrue(GlobalPointerGeometry.overlayOrigin(0f, 720, 192) < 0)
        assertTrue(GlobalPointerGeometry.overlayOrigin(1f, 720, 192) + 192 > 720)
        assertTrue(GlobalPointerGeometry.pixel(0f, 720) > 0f)
        assertTrue(GlobalPointerGeometry.pixel(1f, 720) < 720f)
    }

    @Test fun invalidCoordinatesResolveToSafeCenter() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(360f, GlobalPointerGeometry.pixel(invalid, 720), 0f)
        }
    }
}
