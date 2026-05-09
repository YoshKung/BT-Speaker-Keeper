package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConnectDeviceToggleTest {
    private val baseSettings = ReconnectSettings(
        targetDeviceName = "",
        autoConnectEnabled = false,
        skipWhilePlaybackActive = true,
        maxRetryCount = 3,
        cooldownMinutes = 10,
        periodicRetryEnabled = false,
        periodicRetryMinutes = 60,
    )

    @Test
    fun enablesAutoConnectForSelectedDeviceWhenNoDeviceWasSelected() {
        val updated = AutoConnectDeviceToggle.toggle(baseSettings, "Demo Speaker")

        assertEquals("Demo Speaker", updated.targetDeviceName)
        assertTrue(updated.autoConnectEnabled)
    }

    @Test
    fun disablesAutoConnectWhenSelectedDeviceIsAlreadyActive() {
        val updated = AutoConnectDeviceToggle.toggle(
            baseSettings.copy(
                targetDeviceName = "Demo Speaker",
                autoConnectEnabled = true,
            ),
            "Demo Speaker",
        )

        assertEquals("Demo Speaker", updated.targetDeviceName)
        assertFalse(updated.autoConnectEnabled)
    }

    @Test
    fun switchesTargetAndEnablesAutoConnectWhenDifferentDeviceIsPressed() {
        val updated = AutoConnectDeviceToggle.toggle(
            baseSettings.copy(
                targetDeviceName = "Old Speaker",
                autoConnectEnabled = true,
            ),
            "Demo Speaker",
        )

        assertEquals("Demo Speaker", updated.targetDeviceName)
        assertTrue(updated.autoConnectEnabled)
    }

    @Test
    fun storesSelectedDeviceAddressForRepairMatching() {
        val updated = AutoConnectDeviceToggle.toggle(
            baseSettings,
            selectedDeviceName = "Demo Speaker",
            selectedDeviceAddress = "aa:bb:cc:dd:ee:ff",
        )

        assertEquals("AA:BB:CC:DD:EE:FF", updated.targetDeviceAddress)
        assertTrue(updated.autoConnectEnabled)
    }
}
