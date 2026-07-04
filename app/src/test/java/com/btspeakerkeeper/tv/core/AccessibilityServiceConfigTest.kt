package com.btspeakerkeeper.tv.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceConfigTest {
    @Test
    fun declaresGestureCapabilityForCoordinateTapFallback() {
        val config = listOf(
            File("src/main/res/xml/bt_keeper_accessibility_service.xml"),
            File("app/src/main/res/xml/bt_keeper_accessibility_service.xml"),
        ).first(File::isFile)

        assertTrue(
            "Coordinate tap fallback requires canPerformGestures=true",
            config.readText().contains("""android:canPerformGestures="true""""),
        )
    }
}
