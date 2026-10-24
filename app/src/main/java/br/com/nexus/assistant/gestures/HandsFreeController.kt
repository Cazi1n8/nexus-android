package br.com.nexus.assistant.gestures

import kotlin.math.abs

enum class GestureTrigger(val label: String) {
    OPEN("Mão aberta"), FIST("Punho fechado"), TWO_FINGERS("Dois dedos"),
    PINCH("Pinça"), SWIPE_LEFT("Mover para a esquerda"), SWIPE_RIGHT("Mover para a direita")
}

data class HandsFreeUpdate(
    val configuration: HandConfiguration = HandConfiguration.OTHER,
    val trigger: GestureTrigger? = null
)

/** Independent of MouseController: this class never emits pointer/touch events. */
class HandsFreeController {
    private val selector = ControllingHand()
    private val pinch = PinchInterpreter()
    private val gate = StableGestureGate()
    private var origin: HandPoint? = null
    private var originAt = 0L
    private var lastSwipe = Long.MIN_VALUE / 2

    fun update(frame: HandFrame, threshold: Float): HandsFreeUpdate {
        val hand = selector.select(frame.hands, frame.timestampMs) ?: return resetPose()
        val configuration = hand.configuration(pinch.update(hand.pinchRatio, frame.timestampMs, threshold).pinching)
        val now = frame.timestampMs
        if (origin == null || now - originAt > 600L) { origin = hand.points[9]; originAt = now }
        val start = origin ?: hand.points[9]
        val dx = hand.points[9].x - start.x
        val dy = hand.points[9].y - start.y
        if (configuration == HandConfiguration.OPEN && abs(dx) > .25f && abs(dy) < .15f && now - lastSwipe > 1400L) {
            lastSwipe = now; origin = hand.points[9]; originAt = now; gate.reset()
            return HandsFreeUpdate(configuration, if (dx > 0) GestureTrigger.SWIPE_RIGHT else GestureTrigger.SWIPE_LEFT)
        }
        val trigger = gate.update(configuration, now)?.let {
            when (it) {
                HandConfiguration.OPEN -> GestureTrigger.OPEN
                HandConfiguration.FIST -> GestureTrigger.FIST
                HandConfiguration.TWO_FINGERS -> GestureTrigger.TWO_FINGERS
                HandConfiguration.PINCH -> GestureTrigger.PINCH
                else -> null
            }
        }
        return HandsFreeUpdate(configuration, trigger)
    }
    private fun resetPose(): HandsFreeUpdate {
        pinch.reset(); gate.reset(); origin = null
        return HandsFreeUpdate()
    }
    fun reset() { resetPose(); selector.reset(); lastSwipe = Long.MIN_VALUE / 2 }
}
