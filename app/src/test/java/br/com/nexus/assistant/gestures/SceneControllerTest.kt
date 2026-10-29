package br.com.nexus.assistant.gestures

import org.junit.Assert.*
import org.junit.Test

class SceneControllerTest {
    private fun hand(x: Float = .5f, y: Float = .5f, ratio: Float = .8f, label: String = "Left"): TrackedHand {
        val points = MutableList(21) { HandPoint(.5f, .76f) }
        points[0] = HandPoint(.5f, .8f)
        points[9] = HandPoint(.5f, .6f)
        points[8] = HandPoint(x, y)
        points[4] = HandPoint(x + ratio * .2f, y)
        return TrackedHand(points, label, .95f)
    }
    private fun frame(vararg hands: TrackedHand) = HandFrame(hands.toList())

    @Test fun `objects have independent identity and deletion preserves other objects`() {
        val controller = SceneController()
        val first = controller.state.objects.single()
        controller.create(SceneShape.SPHERE)
        val second = controller.state.selectedObject!!
        assertNotEquals(first.id, second.id)
        assertEquals(SceneShape.SPHERE, second.shape)
        controller.setSelectedColor(0xFF70E995.toInt())
        assertEquals(first, controller.state.objects.first())
        controller.deleteSelected()
        assertEquals(listOf(first), controller.state.objects)
        assertEquals(first.id, controller.state.selectedObjectId)
        controller.deleteSelected()
        assertNull(controller.state.selectedObject)
        assertTrue(controller.state.objects.isEmpty())
    }

    @Test fun `duplicate copies geometry and color but gets new identity and location`() {
        val controller = SceneController()
        controller.setSelectedColor(0xFF70E995.toInt())
        val original = controller.state.selectedObject!!
        controller.duplicateSelected()
        val duplicate = controller.state.selectedObject!!
        assertNotEquals(original.id, duplicate.id)
        assertEquals(original.shape, duplicate.shape)
        assertEquals(original.colorArgb, duplicate.colorArgb)
        assertNotEquals(original.transform.center, duplicate.transform.center)
        assertEquals(original, controller.state.objects.first())
    }

    @Test fun `create and duplicate reject object limit without discarding scene`() {
        val controller = SceneController()
        repeat(SceneState.MAX_OBJECTS - 1) { controller.create(SceneShape.TORUS) }
        val old = controller.state
        assertThrows(IllegalArgumentException::class.java) { controller.create(SceneShape.CONE) }
        assertThrows(IllegalArgumentException::class.java) { controller.duplicateSelected() }
        assertEquals(old, controller.state)
    }

    @Test fun `manual transforms affect selection only and reject nonfinite input`() {
        val controller = SceneController()
        val initial = controller.state.objects.single()
        controller.create(SceneShape.CYLINDER)
        controller.setManualScale(8f)
        controller.setManualYaw(12f)
        assertEquals(.65f, controller.state.selectedObject!!.transform.scale, .0001f)
        assertTrue(controller.state.selectedObject!!.transform.yaw in -3.15f..3.15f)
        assertEquals(initial, controller.state.objects.first())
        assertThrows(IllegalArgumentException::class.java) { controller.setManualScale(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { controller.setManualYaw(Float.POSITIVE_INFINITY) }
        controller.state.validated()
    }

    @Test fun `pinch selects upper object and manipulates only that object`() {
        val lower = SceneObject("lower")
        val upper = SceneObject("upper", SceneShape.SPHERE)
        val controller = SceneController(SceneState(listOf(lower, upper), lower.id))
        controller.update(frame(hand(ratio = .2f)), false, .35f)
        assertEquals(upper.id, controller.state.selectedObjectId)
        assertTrue(controller.state.selectedObject!!.transform.selected)
        controller.update(frame(hand(x = .6f, ratio = .2f)), false, .35f)
        assertTrue(controller.state.selectedObject!!.transform.center.x > .5f)
        assertEquals(lower, controller.state.objects.first())
        controller.update(frame(), false, .35f)
        assertFalse(controller.state.selectedObject!!.transform.selected)
    }

    @Test fun `pinch begun outside object must release before it can select`() {
        val controller = SceneController()
        controller.update(frame(hand(x = .05f, y = .05f, ratio = .2f)), false, .35f)
        controller.update(frame(hand(ratio = .2f)), false, .35f)
        assertFalse(controller.state.selectedObject!!.transform.selected)
        controller.update(frame(hand()), false, .35f)
        controller.update(frame(hand(ratio = .2f)), false, .35f)
        assertTrue(controller.state.selectedObject!!.transform.selected)
    }

    @Test fun `two new pinches move rotate scale and release without mouse dispatch`() {
        val controller = SceneController()
        val initial = controller.state.selectedObject!!.transform
        controller.update(frame(hand(x = .44f, ratio = .2f), hand(x = .56f, ratio = .2f, label = "Right")), false, .35f)
        assertTrue(controller.state.selectedObject!!.transform.bimanual)
        controller.update(frame(hand(x = .36f, y = .53f, ratio = .2f), hand(x = .7f, y = .61f, ratio = .2f, label = "Right")), false, .35f)
        val next = controller.state.selectedObject!!.transform
        assertTrue(next.scale > initial.scale)
        assertTrue(next.center.x > initial.center.x && next.center.y > initial.center.y)
        assertNotEquals(initial.yaw, next.yaw)
        controller.release()
        assertFalse(controller.state.selectedObject!!.transform.selected)
        assertFalse(controller.state.selectedObject!!.transform.bimanual)
    }

    @Test fun `a second hand held outside before selection cannot become a new pair`() {
        val controller = SceneController()
        controller.update(frame(hand(), hand(x = .95f, y = .9f, ratio = .2f, label = "Right")), false, .35f)
        controller.update(frame(hand(ratio = .2f), hand(x = .56f, ratio = .2f, label = "Right")), false, .35f)
        assertTrue(controller.state.selectedObject!!.transform.selected)
        assertFalse(controller.state.selectedObject!!.transform.bimanual)
    }

    @Test fun `invalid or duplicate handedness cannot become two hands`() {
        val controller = SceneController()
        val invalid = hand(label = "Right").let { it.copy(points = it.points.toMutableList().apply { this[3] = HandPoint(Float.NaN, .5f) }) }
        controller.update(frame(hand(ratio = .2f), invalid), false, .35f)
        assertFalse(controller.state.selectedObject!!.transform.bimanual)
        controller.update(frame(hand(ratio = .2f), hand(x = .6f, ratio = .2f)), false, .35f)
        assertFalse(controller.state.selectedObject!!.transform.bimanual)
        controller.state.validated()
    }

    @Test fun `restoring a grabbed scene never restores a live grab`() {
        val controller = SceneController()
        val saved = SceneState(listOf(SceneObject("saved", transform = CubeState(selected = true, bimanual = true))))
        controller.restore(saved)
        assertEquals("saved", controller.state.selectedObjectId)
        assertFalse(controller.state.selectedObject!!.transform.selected)
        assertFalse(controller.state.selectedObject!!.transform.bimanual)
        assertThrows(IllegalArgumentException::class.java) { controller.select("missing") }
        assertEquals("saved", controller.state.selectedObjectId)
    }
}
