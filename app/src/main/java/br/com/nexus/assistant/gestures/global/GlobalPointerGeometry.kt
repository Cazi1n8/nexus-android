package br.com.nexus.assistant.gestures.global

import kotlin.math.roundToInt

/** Physical display coordinates shared by the cursor's center and dispatchGesture. */
internal object GlobalPointerGeometry {
    fun pixel(normalized: Float, extent: Int): Float {
        require(extent > 0)
        val coordinate = if (normalized.isFinite()) normalized else .5f
        // Android gesture paths must remain inside the display, including x/y=0 edges.
        return coordinate.coerceIn(.001f, .999f) * extent
    }

    fun overlayOrigin(normalized: Float, extent: Int, pointerExtent: Int): Int {
        require(pointerExtent > 0)
        // Clamping the whole window moves its center away from the dispatched touch
        // near display edges. Let the decorative halo extend outside the display.
        return (pixel(normalized, extent) - pointerExtent / 2f).roundToInt()
    }
}
