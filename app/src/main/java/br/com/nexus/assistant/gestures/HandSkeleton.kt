package br.com.nexus.assistant.gestures

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.min

private val handConnections = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12, 9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 17 to 18, 18 to 19, 19 to 20, 0 to 17)

/** FIT_CENTER mapping matches the camera preview, including its letterboxing. */
@Composable
internal fun HandSkeleton(frame: HandFrame, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scale = min(size.width / frame.imageWidth.coerceAtLeast(1), size.height / frame.imageHeight.coerceAtLeast(1))
        val imageW = frame.imageWidth * scale
        val imageH = frame.imageHeight * scale
        val left = (size.width - imageW) / 2f
        val top = (size.height - imageH) / 2f
        fun point(p: HandPoint) = Offset(left + p.x * imageW, top + p.y * imageH)
        frame.hands.forEach { hand ->
            if (hand.points.size == 21) {
                val color = if (hand.handedness == "Left") Color(0xFFAE9AFF) else Color(0xFF31E7FA)
                handConnections.forEach { (a, b) -> drawLine(color.copy(alpha = .7f), point(hand.points[a]), point(hand.points[b]), 2f) }
                hand.points.forEach { drawCircle(color, 3.5f, point(it)) }
            }
        }
    }
}
