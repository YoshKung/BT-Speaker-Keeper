package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetRowActivationPolicyTest {
    @Test
    fun focusesSavedDeviceRowBeforeClickingWhenRowHasNoConnectAction() {
        assertEquals(
            TargetRowActivationAction.FOCUS,
            TargetRowActivationPolicy.decide(
                isTargetRowFocused = false,
                targetRowText = "PHANTOM PHANTOM เลิกเชื่อมต่อแล้ว",
                targetName = "PHANTOM",
                targetAddress = "",
            ),
        )
    }

    @Test
    fun waitsForDetailConnectWhenFocusedSavedDeviceRowHasNoConnectAction() {
        assertEquals(
            TargetRowActivationAction.WAIT_FOR_DETAIL_CONNECT,
            TargetRowActivationPolicy.decide(
                isTargetRowFocused = true,
                targetRowText = "PHANTOM PHANTOM เลิกเชื่อมต่อแล้ว",
                targetName = "PHANTOM",
                targetAddress = "",
            ),
        )
    }

    @Test
    fun clicksOnlyWhenTargetRowItselfContainsConnectAction() {
        assertEquals(
            TargetRowActivationAction.CLICK_DIRECT_CONNECT,
            TargetRowActivationPolicy.decide(
                isTargetRowFocused = true,
                targetRowText = "PHANTOM เชื่อมต่อ",
                targetName = "PHANTOM",
                targetAddress = "",
            ),
        )
    }

    @Test
    fun rejectsRowsThatDoNotMatchConfiguredTarget() {
        assertEquals(
            TargetRowActivationAction.IGNORE,
            TargetRowActivationPolicy.decide(
                isTargetRowFocused = true,
                targetRowText = "Kitchen speaker เชื่อมต่อ",
                targetName = "PHANTOM",
                targetAddress = "",
            ),
        )
    }
}
