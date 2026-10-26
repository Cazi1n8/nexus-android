package br.com.nexus.assistant.gestures

/** Pure state machine. It emits pointer intentions; Android dispatch stays in the visible host. */
sealed interface MouseEvent {
    data class Tap(val point: HandPoint, val doubleClick: Boolean) : MouseEvent
    data class Down(val point: HandPoint) : MouseEvent
    data class Move(val point: HandPoint) : MouseEvent
    data class Up(val point: HandPoint) : MouseEvent
    data object Cancel : MouseEvent
}

data class MouseUpdate(
    val cursor: HandPoint? = null,
    val configuration: HandConfiguration = HandConfiguration.OTHER,
    val pinching: Boolean = false,
    val dragging: Boolean = false,
    val scrolling: Boolean = false,
    val events: List<MouseEvent> = emptyList()
)

/** Uses both category and proximity. A second visible hand cannot silently take over a drag. */
class ControllingHand {
    private var previous: HandPoint? = null
    private var label: String? = null
    private var lastSeen = 0L

    fun select(hands: List<TrackedHand>, now: Long): TrackedHand? {
        val valid = hands.filter { it.points.size == 21 }
        val old = previous
        val chosen = if (old == null) valid.maxByOrNull { it.confidence } else {
            valid.filter { it.handedness == label }
                .minByOrNull { distance(it.points[0], old) }
                ?.takeIf { distance(it.points[0], old) < .38f }
        }
        if (chosen != null) {
            label = chosen.handedness
            previous = chosen.points[0]
            lastSeen = now
        } else if (now - lastSeen > 650L) reset()
        return chosen
    }

    fun reset() { previous = null; label = null; lastSeen = 0L }
}

class MouseController {
    private val hand = ControllingHand()
    private val filter = CursorFilter()
    private val pinch = PinchInterpreter()
    private var pressed = false
    private var scrolling = false
    private var scrollOrigin: HandPoint? = null
    private var lastPoint: HandPoint? = null

    fun calibrate(point: HandPoint, sensitivity: Float) = filter.center(point, sensitivity)

    fun update(frame: HandFrame, sensitivity: Float, threshold: Float): MouseUpdate {
        val chosen = hand.select(frame.hands, frame.timestampMs) ?: return lost()
        val point = filter.update(chosen.index, sensitivity, frame.timestampMs)
        val twoFingers = chosen.configuration(false) == HandConfiguration.TWO_FINGERS && chosen.pinchRatio > threshold + .16f
        val events = mutableListOf<MouseEvent>()
        if (twoFingers) {
            pinch.reset()
            if (!scrolling) {
                if (pressed) events += MouseEvent.Cancel
                pressed = false
                scrollOrigin = point
                scrolling = true
            } else {
                val origin = scrollOrigin ?: point
                // A still two-finger pose must not create an accidental long press.
                if (!pressed && distance(origin, point) >= .018f) {
                    events += MouseEvent.Down(origin)
                    events += MouseEvent.Move(point)
                    pressed = true
                } else if (pressed) events += MouseEvent.Move(point)
            }
            lastPoint = point
            return MouseUpdate(point, HandConfiguration.TWO_FINGERS, scrolling = true, events = events)
        }
        if (scrolling) {
            if (pressed) events += MouseEvent.Up(lastPoint ?: point)
            scrolling = false
            scrollOrigin = null
            pressed = false
        }
        val event = pinch.update(chosen.pinchRatio, frame.timestampMs, threshold)
        val configuration = chosen.configuration(event.pinching)
        val visible = chosen.fingerExtended(8, 6) || event.pinching || event.clicked || event.dragEnded
        if (event.dragStarted) { events += MouseEvent.Down(point); pressed = true }
        if (event.dragging && !event.dragStarted) events += MouseEvent.Move(point)
        if (event.dragEnded && pressed) { events += MouseEvent.Up(point); pressed = false }
        if (event.clicked) events += MouseEvent.Tap(point, event.doubleClicked)
        lastPoint = point
        return MouseUpdate(if (visible) point else null, configuration, event.pinching,
            event.dragging, false, events)
    }

    /** Lost/stale camera frames always cancel a held synthetic touch, never leave it pressed. */
    fun lost(): MouseUpdate {
        val events = if (pressed) listOf(MouseEvent.Cancel) else emptyList()
        pressed = false; scrolling = false; scrollOrigin = null; lastPoint = null
        pinch.reset(); filter.reset()
        return MouseUpdate(events = events)
    }

    fun reset(): MouseUpdate = lost().also { hand.reset() }
}
