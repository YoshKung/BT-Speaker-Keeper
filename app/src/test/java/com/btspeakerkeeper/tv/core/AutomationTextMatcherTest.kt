package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTextMatcherTest {
    @Test
    fun recognizesEnglishAndThaiConnectActions() {
        assertTrue(AutomationTextMatcher.isConnectAction("Connect"))
        assertTrue(AutomationTextMatcher.isConnectAction(" เชื่อมต่อ "))
    }

    @Test
    fun doesNotTreatConnectedStateAsConnectAction() {
        assertFalse(AutomationTextMatcher.isConnectAction("Connected"))
        assertFalse(AutomationTextMatcher.isConnectAction("เชื่อมต่อแล้ว"))
    }

    @Test
    fun recognizesEnglishAndThaiConnectedStates() {
        assertTrue(AutomationTextMatcher.isConnectedState("Connected"))
        assertTrue(AutomationTextMatcher.isConnectedState("เชื่อมต่อแล้ว"))
    }

    @Test
    fun recognizesDeviceListNavigationLabels() {
        assertTrue(AutomationTextMatcher.isDeviceListNavigation("Previously connected devices"))
        assertTrue(AutomationTextMatcher.isDeviceListNavigation("Remotes & Accessories"))
        assertTrue(AutomationTextMatcher.isDeviceListNavigation("อุปกรณ์ที่บันทึกไว้"))
        assertTrue(AutomationTextMatcher.isDeviceListNavigation("รีโมตและอุปกรณ์เสริม"))
    }

    @Test
    fun doesNotTreatConnectActionAsDeviceListNavigation() {
        assertFalse(AutomationTextMatcher.isDeviceListNavigation("Connect"))
        assertFalse(AutomationTextMatcher.isDeviceListNavigation("เชื่อมต่อ"))
    }

    @Test
    fun recognizesEnglishAndThaiPairActions() {
        assertTrue(AutomationTextMatcher.isPairAction("Pair"))
        assertTrue(AutomationTextMatcher.isPairAction("Pair device"))
        assertTrue(AutomationTextMatcher.isPairAction(" จับคู่ "))
        assertTrue(AutomationTextMatcher.isPairAction("จับคู่อุปกรณ์"))
    }

    @Test
    fun doesNotTreatPairingTitlesAsPairActions() {
        assertFalse(AutomationTextMatcher.isPairAction("Pair with device"))
        assertFalse(AutomationTextMatcher.isPairAction("จับคู่กับอุปกรณ์"))
        assertFalse(AutomationTextMatcher.isPairAction("รหัสการจับคู่ Wi-Fi"))
    }

    @Test
    fun recognizesWifiAndAdbPairingExclusions() {
        assertTrue(AutomationTextMatcher.containsPairingPromptExclusion("รหัสการจับคู่ Wi-Fi 123456"))
        assertTrue(AutomationTextMatcher.containsPairingPromptExclusion("รหัสการจับคู่ Wi‑Fi 123456"))
        assertTrue(AutomationTextMatcher.containsPairingPromptExclusion("IP address and port 192.0.2.10:46353"))
        assertTrue(AutomationTextMatcher.containsPairingPromptExclusion("Wireless debugging"))
    }

    @Test
    fun recognizesRepairPairNavigationLabels() {
        assertTrue(AutomationTextMatcher.isRepairPairNavigation("Pair remote or accessory"))
        assertTrue(AutomationTextMatcher.isRepairPairNavigation("Pair accessory"))
        assertTrue(AutomationTextMatcher.isRepairPairNavigation("จับคู่อุปกรณ์"))
        assertTrue(AutomationTextMatcher.isRepairPairNavigation("จับคู่รีโมตหรืออุปกรณ์เสริม"))
    }

    @Test
    fun recognizesPairingNotReadyStates() {
        assertTrue(AutomationTextMatcher.containsPairingNotReadyState("Pairing canceled"))
        assertTrue(AutomationTextMatcher.containsPairingNotReadyState("Couldn't pair with Demo Speaker"))
        assertTrue(AutomationTextMatcher.containsPairingNotReadyState("Demo Speaker ถูกยกเลิก"))
        assertTrue(AutomationTextMatcher.containsPairingNotReadyState("จับคู่ไม่สำเร็จ"))
    }

    @Test
    fun doesNotTreatCancelButtonAsPairingNotReadyState() {
        assertFalse(
            AutomationTextMatcher.containsPairingNotReadyState(
                "คำขอจับคู่อุปกรณ์ผ่านบลูทูธ จาก: Demo Speaker จับคู่อุปกรณ์ ยกเลิก",
            ),
        )
        assertFalse(AutomationTextMatcher.containsPairingNotReadyState("Cancel"))
        assertFalse(AutomationTextMatcher.containsPairingNotReadyState("ยกเลิก"))
    }
}
