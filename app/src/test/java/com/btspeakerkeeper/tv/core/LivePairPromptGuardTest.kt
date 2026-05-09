package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePairPromptGuardTest {
    @Test
    fun acceptsPairPromptForConfiguredAutoConnectTarget() {
        assertTrue(
            LivePairPromptGuard.shouldAccept(
                windowText = "Bluetooth Demo Speaker Pair",
                targetName = "Demo Speaker",
                autoConnectEnabled = true,
                nowMillis = 120_000L,
                lastAcceptedAtMillis = null,
                packageName = "com.android.tv.settings",
            ),
        )
    }

    @Test
    fun acceptsThaiBluetoothPairPromptFromGoogleTv() {
        assertTrue(
            LivePairPromptGuard.shouldAccept(
                windowText = "คำขอจับคู่อุปกรณ์ผ่านบลูทูธ จาก: Demo Speaker จับคู่อุปกรณ์ ยกเลิก",
                targetName = "Demo Speaker",
                autoConnectEnabled = true,
                nowMillis = 120_000L,
                lastAcceptedAtMillis = null,
                packageName = "com.android.tv.settings",
            ),
        )
    }

    @Test
    fun rejectsPromptWhenAutoConnectIsOff() {
        assertFalse(
            LivePairPromptGuard.shouldAccept(
                windowText = "Bluetooth Demo Speaker Pair",
                targetName = "Demo Speaker",
                autoConnectEnabled = false,
                nowMillis = 120_000L,
                lastAcceptedAtMillis = null,
            ),
        )
    }

    @Test
    fun rejectsPromptWithoutConfiguredTargetName() {
        assertFalse(
            LivePairPromptGuard.shouldAccept(
                windowText = "Bluetooth Kitchen speaker Pair",
                targetName = "Demo Speaker",
                autoConnectEnabled = true,
                nowMillis = 120_000L,
                lastAcceptedAtMillis = null,
            ),
        )
    }

    @Test
    fun rejectsWifiPairingPromptEvenWhenItContainsPairingText() {
        assertFalse(
            LivePairPromptGuard.shouldAccept(
                windowText = "จับคู่กับอุปกรณ์ รหัสการจับคู่ Wi‑Fi 123456 IP address and port Demo Speaker",
                targetName = "Demo Speaker",
                autoConnectEnabled = true,
                nowMillis = 120_000L,
                lastAcceptedAtMillis = null,
                packageName = "com.android.tv.settings",
            ),
        )
    }

    @Test
    fun rejectsPromptOutsideSettingsOrSystemPackage() {
        assertFalse(
            LivePairPromptGuard.shouldAccept(
                windowText = "Bluetooth Demo Speaker Pair",
                targetName = "Demo Speaker",
                autoConnectEnabled = true,
                nowMillis = 120_000L,
                lastAcceptedAtMillis = null,
                packageName = "com.example.random",
            ),
        )
    }

    @Test
    fun acceptsPromptForAlternateRepairName() {
        assertTrue(
            LivePairPromptGuard.shouldAccept(
                windowText = "Bluetooth Factory Speaker Pair device",
                targetName = "Demo Speaker",
                autoConnectEnabled = true,
                nowMillis = 120_000L,
                lastAcceptedAtMillis = null,
                packageName = "com.android.tv.settings",
                targetAddress = "AA:BB:CC:DD:EE:FF",
                alternateTargetName = "Factory Speaker",
            ),
        )
    }

    @Test
    fun rejectsRepeatedAcceptWithinCooldown() {
        assertFalse(
            LivePairPromptGuard.shouldAccept(
                windowText = "Bluetooth Demo Speaker Pair",
                targetName = "Demo Speaker",
                autoConnectEnabled = true,
                nowMillis = 120_000L,
                lastAcceptedAtMillis = 100_000L,
            ),
        )
    }
}
