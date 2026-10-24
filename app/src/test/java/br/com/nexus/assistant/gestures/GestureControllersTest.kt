package br.com.nexus.assistant.gestures

import org.junit.Assert.*
import org.junit.Test

class GestureControllersTest {
    private fun hand(x: Float = .5f, y: Float = .5f, ratio: Float = .8f,
        label: String = "Left", twoFingers: Boolean = false, confidence: Float = .9f): TrackedHand {
        val points = MutableList(21) { HandPoint(.5f, .76f) }
        points[0] = HandPoint(.5f, .8f)
        points[9] = HandPoint(.5f, .6f)
        points[6] = HandPoint(.5f, .68f)
        points[8] = HandPoint(x, y)
        points[4] = HandPoint(x + ratio * .2f, y)
        points[10] = HandPoint(.6f, .7f)
        if (twoFingers) points[12] = HandPoint(.6f, .45f)
        points[14] = HandPoint(.5f, .65f)
        points[18] = HandPoint(.5f, .65f)
        return TrackedHand(points, label, confidence)
    }
    private fun frame(time: Long, vararg hands: TrackedHand) = HandFrame(hands.toList(), timestampMs = time)

    @Test fun `short pinch emits actual tap intention once and double tap keeps both taps`() {
        val mouse = MouseController()
        mouse.update(frame(1000, hand(ratio = .2f)), 1f, .35f)
        val first = mouse.update(frame(1120, hand()), 1f, .35f)
        assertEquals(1, first.events.filterIsInstance<MouseEvent.Tap>().size)
        assertFalse((first.events.single() as MouseEvent.Tap).doubleClick)
        assertTrue(mouse.update(frame(1180, hand()), 1f, .35f).events.isEmpty())
        mouse.update(frame(1240, hand(ratio = .2f)), 1f, .35f)
        assertTrue((mouse.update(frame(1350, hand()), 1f, .35f).events.single() as MouseEvent.Tap).doubleClick)
    }

    @Test fun `lost hand during drag cancels pressed pointer without a click`() {
        val mouse = MouseController()
        mouse.update(frame(1000, hand(ratio = .2f)), 1f, .35f)
        assertTrue(mouse.update(frame(1450, hand(ratio = .2f)), 1f, .35f).events.single() is MouseEvent.Down)
        assertTrue(mouse.update(frame(1550, hand(x = .6f, ratio = .2f)), 1f, .35f).events.single() is MouseEvent.Move)
        assertEquals(listOf(MouseEvent.Cancel), mouse.update(frame(1650), 1f, .35f).events)
        assertTrue(mouse.update(frame(1750), 1f, .35f).events.isEmpty())
        assertTrue(mouse.update(frame(1850, hand()), 1f, .35f).events.isEmpty())
    }

    @Test fun `two finger scroll emits down move up and no tap`() {
        val mouse = MouseController()
        assertTrue(mouse.update(frame(1000, hand(twoFingers = true)), 1f, .35f).events.isEmpty())
        val scroll = mouse.update(frame(1150, hand(y = .3f, twoFingers = true)), 1f, .35f)
        assertTrue(scroll.scrolling)
        assertTrue(scroll.events[0] is MouseEvent.Down)
        assertTrue(scroll.events[1] is MouseEvent.Move)
        assertTrue(mouse.update(frame(1300, hand()), 1f, .35f).events.single() is MouseEvent.Up)
    }

    @Test fun `still two finger pose never generates a long press`() {
        val mouse = MouseController()
        repeat(12) { index ->
            assertTrue(mouse.update(frame(1000L + index * 150L, hand(twoFingers = true)), 1f, .35f).events.isEmpty())
        }
        assertTrue(mouse.update(frame(3000, hand()), 1f, .35f).events.isEmpty())
    }

    @Test fun `hand list reordering does not change controlling hand`() {
        val selector = ControllingHand()
        val left = hand(label = "Left", confidence = .95f)
        val right = hand(x = .8f, label = "Right", confidence = .8f)
        assertEquals("Left", selector.select(listOf(left, right), 1000)?.handedness)
        assertEquals("Left", selector.select(listOf(right.copy(confidence = .99f), left), 1100)?.handedness)
        assertNull(selector.select(listOf(right), 1200))
    }

    @Test fun `pinch outside cube cannot teleport or select it until released`() {
        val cube = CubeController()
        cube.update(frame(1000, hand(x = .1f, y = .1f, ratio = .2f)), false, .35f)
        assertFalse(cube.state.selected)
        cube.update(frame(1100, hand(ratio = .2f)), false, .35f)
        assertFalse(cube.state.selected)
        assertEquals(HandPoint(.5f, .5f), cube.state.center)
        cube.update(frame(1200, hand()), false, .35f)
        cube.update(frame(1300, hand(ratio = .2f)), false, .35f)
        assertTrue(cube.state.selected)
        cube.update(frame(1400), false, .35f)
        assertFalse(cube.state.selected)
    }

    @Test fun `cube scale changes only after two pinches on object`() {
        val cube = CubeController()
        val initial = cube.state.scale
        cube.update(frame(1000, hand(x = .44f), hand(x = .56f, label = "Right")), false, .35f)
        cube.update(frame(1100, hand(x = .2f), hand(x = .8f, label = "Right")), false, .35f)
        assertEquals(initial, cube.state.scale, .0001f)
        cube.update(frame(1200, hand(x = .44f, ratio = .2f), hand(x = .56f, ratio = .2f, label = "Right")), false, .35f)
        assertTrue(cube.state.bimanual)
        cube.update(frame(1300, hand(x = .34f, ratio = .2f), hand(x = .66f, ratio = .2f, label = "Right")), false, .35f)
        assertTrue(cube.state.scale > initial)
        assertTrue(cube.state.scale <= .65f)
    }

    @Test fun `second pinch started outside cube cannot grab by entering while held`() {
        val cube = CubeController()
        cube.update(frame(1000, hand(ratio = .2f)), false, .35f)
        cube.update(frame(1100, hand(ratio = .2f), hand(x = .95f, y = .9f, ratio = .2f, label = "Right")), false, .35f)
        assertTrue(cube.state.selected)
        assertFalse(cube.state.bimanual)
        cube.update(frame(1200, hand(ratio = .2f), hand(x = .56f, ratio = .2f, label = "Right")), false, .35f)
        assertFalse(cube.state.bimanual)
        cube.update(frame(1300, hand(x = .56f, ratio = .2f, label = "Right")), false, .35f)
        assertFalse(cube.state.selected)
    }
}
