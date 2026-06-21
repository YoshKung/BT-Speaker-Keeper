package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSafetyPolicyTest {
    @Test
    fun flagsWirelessDebuggingPairingScreensAsUnsafeAutomationWindows() {
        assertEquals(
            "Unsafe window: Wireless debugging or ADB pairing screen",
            AutomationSafetyPolicy.unsafeAutomationWindowReason(
                "จับคู่กับอุปกรณ์ รหัสการจับคู่ Wi-Fi 123456 IP address and port 203.0.113.10:45678",
            ),
        )
    }

    @Test
    fun classifiesWirelessDebuggingPairingScreensAsUnsafe() {
        val classification = AutomationSafetyPolicy.classifyAutomationWindow(
            windowText = "Wireless debugging Pairing code 123456 IP address and port 203.0.113.10:45678",
            mode = AutomationMode.CONNECT,
            targetName = "Demo Speaker",
            targetAddress = "",
        )

        assertEquals(AutomationWindowKind.UNSAFE, classification.kind)
        assertEquals("Unsafe window: Wireless debugging or ADB pairing screen", classification.reason)
    }

    @Test
    fun classifiesAppsSecurityAsWrongDestination() {
        val classification = AutomationSafetyPolicy.classifyAutomationWindow(
            windowText = "แอป ความปลอดภัย สแกนแอปด้วย Play Protect สิทธิ์ของแอป",
            mode = AutomationMode.CONNECT,
            targetName = "Demo Speaker",
            targetAddress = "",
        )

        assertEquals(AutomationWindowKind.WRONG_DESTINATION, classification.kind)
        assertEquals("Wrong Settings destination: Apps/Security", classification.reason)
    }

    @Test
    fun classifiesDeveloperOptionsAsWrongDestination() {
        val classification = AutomationSafetyPolicy.classifyAutomationWindow(
            windowText = "System Developer options USB debugging Stay awake",
            mode = AutomationMode.CONNECT,
            targetName = "Demo Speaker",
            targetAddress = "",
        )

        assertEquals(AutomationWindowKind.WRONG_DESTINATION, classification.kind)
        assertEquals("Wrong Settings destination: Developer options", classification.reason)
    }

    @Test
    fun allowsSettingsHomeWhenBluetoothNavigationIsVisible() {
        val classification = AutomationSafetyPolicy.classifyAutomationWindow(
            windowText = "Settings Network & Internet Apps Remotes & Accessories",
            mode = AutomationMode.CONNECT,
            targetName = "Demo Speaker",
            targetAddress = "",
        )

        assertEquals(AutomationWindowKind.ALLOWED_NAVIGATION, classification.kind)
    }

    @Test
    fun allowsPairRepairNavigationOnlyForRepairMode() {
        assertEquals(
            AutomationWindowKind.ALLOWED_NAVIGATION,
            AutomationSafetyPolicy.classifyAutomationWindow(
                windowText = "Pair remote or accessory",
                mode = AutomationMode.PAIR_REPAIR,
                targetName = "Demo Speaker",
                targetAddress = "",
            ).kind,
        )
        assertEquals(
            AutomationWindowKind.NEUTRAL,
            AutomationSafetyPolicy.classifyAutomationWindow(
                windowText = "Pair remote or accessory",
                mode = AutomationMode.CONNECT,
                targetName = "Demo Speaker",
                targetAddress = "",
            ).kind,
        )
    }

    @Test
    fun allowsConnectOnlyWhenContextContainsTargetNameOrAddress() {
        assertTrue(
            AutomationSafetyPolicy.hasTargetContext(
                contextText = "Demo Speaker Connect",
                targetName = "Demo Speaker",
                targetAddress = "",
            ),
        )
        assertTrue(
            AutomationSafetyPolicy.hasTargetContext(
                contextText = "Factory Speaker AA:BB:CC:DD:EE:FF Connect",
                targetName = "Demo Speaker",
                targetAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
        assertFalse(
            AutomationSafetyPolicy.hasTargetContext(
                contextText = "Kitchen speaker Connect",
                targetName = "Demo Speaker",
                targetAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
    }

    @Test
    fun treatsConnectedStateAsTargetConnectedOnlyInTargetContext() {
        assertTrue(
            AutomationSafetyPolicy.isTargetConnectedContext(
                contextText = "Demo Speaker Connected",
                targetName = "Demo Speaker",
                targetAddress = "",
            ),
        )
        assertFalse(
            AutomationSafetyPolicy.isTargetConnectedContext(
                contextText = "Kitchen speaker Connected",
                targetName = "Demo Speaker",
                targetAddress = "",
            ),
        )
    }

    @Test
    fun rejectsUnsafeSingleVisibleRepairCandidates() {
        assertTrue(AutomationSafetyPolicy.isUnsafeSingleVisibleRepairCandidate("Wireless debugging"))
        assertTrue(AutomationSafetyPolicy.isUnsafeSingleVisibleRepairCandidate("Play Protect"))
        assertTrue(AutomationSafetyPolicy.isUnsafeSingleVisibleRepairCandidate("Pair accessory"))
        assertTrue(AutomationSafetyPolicy.isUnsafeSingleVisibleRepairCandidate("Remotes & Accessories"))
        assertTrue(AutomationSafetyPolicy.isUnsafeSingleVisibleRepairCandidate("Connected devices"))
        assertFalse(AutomationSafetyPolicy.isUnsafeSingleVisibleRepairCandidate("Demo Speaker AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun scrollsOnlyInTargetOrAllowedNavigationWindows() {
        assertFalse(
            AutomationSafetyPolicy.canScrollAutomationWindow(
                windowText = "แอป ความปลอดภัย สแกนแอปด้วย Play Protect",
                mode = AutomationMode.CONNECT,
                targetName = "Demo Speaker",
                targetAddress = "",
            ),
        )
        assertTrue(
            AutomationSafetyPolicy.canScrollAutomationWindow(
                windowText = "Bluetooth devices Previously connected devices",
                mode = AutomationMode.CONNECT,
                targetName = "Demo Speaker",
                targetAddress = "",
            ),
        )
        assertTrue(
            AutomationSafetyPolicy.canScrollAutomationWindow(
                windowText = "Demo Speaker Connect",
                mode = AutomationMode.CONNECT,
                targetName = "Demo Speaker",
                targetAddress = "",
            ),
        )
    }


    @Test
    fun redactsBluetoothAddressesInDiagnostics() {
        assertEquals(
            "Repair pairing: selected Demo Speaker **:**:**:**:EE:FF",
            AutomationSafetyPolicy.redactSensitiveText("Repair pairing: selected Demo Speaker AA:BB:CC:DD:EE:FF"),
        )
    }

    @Test
    fun redactsPairingCodesAndIpEndpointsInDiagnostics() {
        assertEquals(
            "Wi-Fi pairing code <REDACTED_CODE> at <REDACTED_ENDPOINT>",
            AutomationSafetyPolicy.redactSensitiveText("Wi-Fi pairing code 123456 at 203.0.113.10:45678"),
        )
    }

    @Test
    fun shortensDiagnosticTextAfterRedaction() {
        assertEquals(
            "Demo Speaker **:**:**:**: ...",
            AutomationSafetyPolicy.shortDiagnosticText(
                "Demo Speaker AA:BB:CC:DD:EE:FF is visible in a very long row",
                maxLength = 29,
            ),
        )
    }
}
