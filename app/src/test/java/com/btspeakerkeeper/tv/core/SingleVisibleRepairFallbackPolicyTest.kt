package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleVisibleRepairFallbackPolicyTest {
    @Test
    fun allowsManualSingleVisibleFallbackOnlyWhenNoSavedAddressExists() {
        assertTrue(
            SingleVisibleRepairFallbackPolicy.canUse(
                requestedByManualRepair = true,
                targetAddress = "",
            ),
        )
    }

    @Test
    fun rejectsSingleVisibleFallbackWhenSavedAddressExists() {
        assertFalse(
            SingleVisibleRepairFallbackPolicy.canUse(
                requestedByManualRepair = true,
                targetAddress = "02:00:00:00:00:01",
            ),
        )
    }

    @Test
    fun rejectsSingleVisibleFallbackForNonManualRepairSessions() {
        assertFalse(
            SingleVisibleRepairFallbackPolicy.canUse(
                requestedByManualRepair = false,
                targetAddress = "",
            ),
        )
    }
}
