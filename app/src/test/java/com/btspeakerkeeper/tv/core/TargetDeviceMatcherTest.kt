package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetDeviceMatcherTest {
    @Test
    fun matchesTextByConfiguredName() {
        assertTrue(
            TargetDeviceMatcher.matchesText(
                text = "Demo Speaker Connected",
                targetName = "Demo Speaker",
                targetAddress = "",
            ),
        )
    }

    @Test
    fun matchesTextByConfiguredAddressWhenNameChanged() {
        assertTrue(
            TargetDeviceMatcher.matchesText(
                text = "Factory Speaker AA:BB:CC:DD:EE:FF",
                targetName = "Demo Speaker",
                targetAddress = "aa:bb:cc:dd:ee:ff",
            ),
        )
    }

    @Test
    fun rejectsTextWithoutNameOrAddress() {
        assertFalse(
            TargetDeviceMatcher.matchesText(
                text = "Factory Speaker 00:11:22:33:44:55",
                targetName = "Demo Speaker",
                targetAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
    }

    @Test
    fun extractsFirstBluetoothAddressFromText() {
        assertTrue(
            TargetDeviceMatcher.firstAddressIn("Factory Speaker aa:bb:cc:dd:ee:ff") == "AA:BB:CC:DD:EE:FF",
        )
    }
}
