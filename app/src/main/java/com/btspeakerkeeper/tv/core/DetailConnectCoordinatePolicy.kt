package com.btspeakerkeeper.tv.core

data class UiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int
        get() = right - left
    val centerY: Int
        get() = top + ((bottom - top) / 2)
}

data class TapPoint(
    val x: Int,
    val y: Int,
)

object DetailConnectCoordinatePolicy {
    fun computeTapPoint(
        targetRowFocused: Boolean,
        hasTargetWindowContext: Boolean,
        targetRowBounds: UiBounds,
        firstActionRowBounds: UiBounds,
        screenWidth: Int,
        screenHeight: Int,
    ): TapPoint? {
        if (!targetRowFocused || !hasTargetWindowContext) {
            return null
        }

        val targetWidth = targetRowBounds.width
        val anchorWidth = firstActionRowBounds.width
        if (targetWidth <= 0 || anchorWidth <= 0 || screenWidth <= 0 || screenHeight <= 0) {
            return null
        }

        val x = firstActionRowBounds.right + anchorWidth
        val y = firstActionRowBounds.centerY
        return TapPoint(x, y).takeIf { point ->
            point.x in 1 until screenWidth && point.y in 1 until screenHeight
        }
    }
}
