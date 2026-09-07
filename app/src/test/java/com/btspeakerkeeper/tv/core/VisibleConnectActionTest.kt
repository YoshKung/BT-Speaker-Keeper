package com.btspeakerkeeper.tv.core

import org.junit.Assert.*
import org.junit.Test

class VisibleConnectActionTest {
    private val screen = UiBounds(0, 0, 1920, 1080)
    private val action = UiBounds(1200, 250, 1500, 350)

    @Test
    fun tapsTheActualThaiConnectLabelInsideTheDisplay() {
        assertEquals(TapPoint(1350, 300), point("เชื่อมต่อ"))
        assertEquals(TapPoint(1350, 300), point("Connect"))
    }

    @Test
    fun rejectsUnrelatedOrDestructiveActions() {
        for (label in listOf("Disconnect", "เลิกเชื่อมต่อ", "เลิกเชื่อมต่อแล้ว", "เปลี่ยนชื่อ", "ไม่จำ", "Forget", "Pair")) {
            assertNull(point(label))
        }
        assertFalse(VisibleConnectAction.isSafeContainer(listOf("เชื่อมต่อ", "ไม่จำ")))
        assertFalse(VisibleConnectAction.isSafeContainer(listOf("Connect", "Rename")))
        assertTrue(VisibleConnectAction.isSafeContainer(listOf("Example speaker", "เชื่อมต่อ")))
    }

    @Test
    fun neverTapsHiddenDisabledWrongTargetOrUnsafeWindows() {
        assertNull(point("Connect", visible = false))
        assertNull(point("Connect", enabled = false))
        assertNull(point("Connect", target = false))
        assertNull(point("Connect", safeWindow = false))
    }

    @Test
    fun rejectsOffscreenAndInvalidBoundsWithoutInflatingDisplaySize() {
        assertNull(point("Connect", bounds = UiBounds(1900, 250, 2100, 350)))
        assertNull(point("Connect", bounds = UiBounds(-50, 250, 100, 350)))
        assertNull(point("Connect", bounds = UiBounds(1200, 250, 1200, 350)))
        assertNull(point("Connect", bounds = UiBounds(1200, 1000, 1500, 1200)))
    }

    private fun point(
        label: String,
        visible: Boolean = true,
        enabled: Boolean = true,
        target: Boolean = true,
        safeWindow: Boolean = true,
        bounds: UiBounds = action,
    ) = VisibleConnectAction.tapPoint(label, visible, enabled, target, safeWindow, bounds, screen)
}
