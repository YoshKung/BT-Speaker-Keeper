package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsWindowTextPolicyTest {
    private val home = "การตั้งค่า บัญชีและโปรไฟล์ ความเป็นส่วนตัว แอป ระบบ รีโมตและอุปกรณ์เสริม"

    @Test fun appsPreviewDoesNotReplaceVerifiedSettingsHome() {
        assertEquals(home, SettingsWindowTextPolicy.choose("$home แอป แอปที่ไม่ได้ใช้ สิทธิ์ของแอป", home))
    }

    @Test fun pairingExclusionStillUsesTheEntireWindow() {
        val full = "$home Wireless debugging Wi-Fi pairing code"
        assertEquals(full, SettingsWindowTextPolicy.choose(full, home))
    }

    @Test fun appsPageDoesNotQualifyAsSettingsHome() {
        val full = "แอป แอปที่ไม่ได้ใช้ สิทธิ์ของแอป"
        assertEquals(full, SettingsWindowTextPolicy.choose(full, "แอป แอปที่ไม่ได้ใช้"))
    }

    @Test fun savedSpeakerDetailKeepsTheConnectPreview() {
        val left = "รีโมตและอุปกรณ์เสริม Demo Speaker ไม่ได้เชื่อมต่อ"
        val full = "$left Demo Speaker เชื่อมต่อ เปลี่ยนชื่อ ไม่จำ"
        assertEquals(full, SettingsWindowTextPolicy.choose(full, left))
    }
}
