package com.btspeakerkeeper.tv.core

import org.junit.Assert.*
import org.junit.Test

class ConnectConfirmationPolicyTest {
    private val systemPackage = "com.google.android.chromecast.chromecastservice"
    private fun allows(labels: List<String>, waiting: Boolean = true, pkg: String = systemPackage) =
        ConnectConfirmationPolicy.allows(pkg, labels, "Demo Speaker", waiting)

    @Test fun acceptsExactTargetConnectConfirmationOnlyAfterActivation() {
        assertTrue(allows(listOf("เชื่อมต่อกับ Demo Speaker", "ใช่", "ไม่")))
        assertTrue(allows(listOf("Connect to Demo Speaker", "Yes", "No")))
        assertFalse(allows(listOf("เชื่อมต่อกับ Demo Speaker", "ใช่", "ไม่"), waiting = false))
    }

    @Test fun refusesOtherDevicesDestructivePromptsAndOrdinaryYesDialogs() {
        for (title in listOf("Connect to Other Speaker", "Connect to Demo Speaker 2", "Forget Demo Speaker", "Disconnect Demo Speaker", "Rename Demo Speaker", "Demo Speaker")) {
            assertFalse(allows(listOf(title, "Yes", "No")))
        }
        assertFalse(allows(listOf("Connect to Demo Speaker", "Yes", "No"), pkg = "com.example.app"))
    }

    @Test fun wifiAdbPairingAndMissingChoicesCannotBeAccepted() {
        assertFalse(allows(listOf("Connect to Demo Speaker", "Yes", "No", "Wireless debugging")))
        assertFalse(allows(listOf("Connect to Demo Speaker", "Yes")))
        assertFalse(allows(listOf("Pair with Demo Speaker", "Pair", "Cancel")))
    }
}
