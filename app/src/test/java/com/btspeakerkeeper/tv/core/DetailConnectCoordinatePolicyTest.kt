package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailConnectCoordinatePolicyTest {
    @Test
    fun computesRightPaneFirstActionTapFromTargetAndFirstRowBounds() {
        val point = DetailConnectCoordinatePolicy.computeTapPoint(
            targetRowFocused = true,
            hasTargetWindowContext = true,
            targetRowBounds = UiBounds(left = 220, top = 442, right = 900, bottom = 571),
            firstActionRowBounds = UiBounds(left = 220, top = 242, right = 900, bottom = 362),
            screenWidth = 1920,
            screenHeight = 1080,
        )

        assertEquals(TapPoint(x = 1580, y = 302), point)
    }

    @Test
    fun usesLeftActionAnchorWhenFocusedTargetBoundsAreTooBroad() {
        val point = DetailConnectCoordinatePolicy.computeTapPoint(
            targetRowFocused = true,
            hasTargetWindowContext = true,
            targetRowBounds = UiBounds(left = 0, top = 0, right = 3840, bottom = 1080),
            firstActionRowBounds = UiBounds(left = 220, top = 242, right = 900, bottom = 362),
            screenWidth = 3840,
            screenHeight = 2160,
        )

        assertEquals(TapPoint(x = 1580, y = 302), point)
    }

    @Test
    fun rejectsWhenTargetRowIsNotFocused() {
        val point = DetailConnectCoordinatePolicy.computeTapPoint(
            targetRowFocused = false,
            hasTargetWindowContext = true,
            targetRowBounds = UiBounds(left = 220, top = 442, right = 900, bottom = 571),
            firstActionRowBounds = UiBounds(left = 220, top = 242, right = 900, bottom = 362),
            screenWidth = 1920,
            screenHeight = 1080,
        )

        assertNull(point)
    }

    @Test
    fun rejectsWhenWindowIsNotTargetContext() {
        val point = DetailConnectCoordinatePolicy.computeTapPoint(
            targetRowFocused = true,
            hasTargetWindowContext = false,
            targetRowBounds = UiBounds(left = 220, top = 442, right = 900, bottom = 571),
            firstActionRowBounds = UiBounds(left = 220, top = 242, right = 900, bottom = 362),
            screenWidth = 1920,
            screenHeight = 1080,
        )

        assertNull(point)
    }

    @Test
    fun rejectsWhenCalculatedPointWouldFallOutsideScreen() {
        val point = DetailConnectCoordinatePolicy.computeTapPoint(
            targetRowFocused = true,
            hasTargetWindowContext = true,
            targetRowBounds = UiBounds(left = 220, top = 442, right = 900, bottom = 571),
            firstActionRowBounds = UiBounds(left = 220, top = 242, right = 900, bottom = 362),
            screenWidth = 1200,
            screenHeight = 1080,
        )

        assertNull(point)
    }
}
