package com.btspeakerkeeper.tv.core

object AutomationTextMatcher {
    private val connectActions = setOf("Connect", "เชื่อมต่อ")
    private val pairActions = setOf("Pair", "Pair device", "จับคู่", "จับคู่อุปกรณ์")
    private val connectedStates = setOf("Connected", "เชื่อมต่อแล้ว")
    private val pairingNotReadyStates = setOf(
        "Canceled",
        "Cancelled",
        "Pairing canceled",
        "Pairing cancelled",
        "Couldn't pair",
        "Could not pair",
        "Unable to pair",
        "Pairing failed",
        "Pairing rejected",
        "Connection failed",
        "ถูกยกเลิก",
        "การจับคู่ถูกยกเลิก",
        "จับคู่ไม่สำเร็จ",
        "จับคู่ไม่ได้",
        "ไม่สามารถจับคู่",
        "เชื่อมต่อไม่ได้",
    )
    private val pairingPromptExclusions = setOf(
        "ADB",
        "IP address and port",
        "Pairing code",
        "Wi-Fi pairing code",
        "Wi‑Fi pairing code",
        "Wireless debugging",
        "ที่อยู่ IP และพอร์ต",
        "รหัสการจับคู่ Wi-Fi",
        "รหัสการจับคู่ Wi‑Fi",
        "การแก้ไขข้อบกพร่องแบบไร้สาย",
    )
    private val deviceListNavigationTexts = setOf(
        "Accessories",
        "Bluetooth",
        "Bluetooth devices",
        "Paired devices",
        "Previously connected devices",
        "Remotes & Accessories",
        "Saved devices",
        "Known devices",
        "บลูทูธ",
        "อุปกรณ์บลูทูธ",
        "อุปกรณ์ที่จับคู่",
        "อุปกรณ์ที่เชื่อมต่อก่อนหน้า",
        "อุปกรณ์ที่บันทึกไว้",
        "อุปกรณ์ที่รู้จัก",
        "อุปกรณ์เสริม",
        "รีโมตและอุปกรณ์เสริม",
    )
    private val repairPairNavigationTexts = setOf(
        "Pair remote or accessory",
        "Pair accessory",
        "จับคู่อุปกรณ์",
        "จับคู่อุปกรณ์เสริม",
        "จับคู่รีโมตหรืออุปกรณ์เสริม",
        "จับคู่รีโมทหรืออุปกรณ์เสริม",
    )
    private val genericSettingsRows = setOf(
        "Connected devices",
        "Available devices",
        "Other devices",
        "Searching for accessories",
        "Searching for devices",
        "Device name",
        "Settings",
        "อุปกรณ์ที่เชื่อมต่อ",
        "อุปกรณ์ที่พร้อมใช้งาน",
        "อุปกรณ์อื่น",
        "กำลังค้นหาอุปกรณ์",
        "ชื่ออุปกรณ์",
        "การตั้งค่า",
    )
    private val wrongSettingsDestinationTexts = setOf(
        "Security",
        "Play Protect",
        "Scan apps with Play Protect",
        "Improve harmful app detection",
        "Permissions",
        "App permissions",
        "Special app access",
        "Manage updates",
        "Unused apps",
        "Developer options",
        "USB debugging",
        "Bug report",
        "Stay awake",
        "ความปลอดภัย",
        "สแกนแอปด้วย Play Protect",
        "ปรับปรุงการตรวจหาแอปอันตราย",
        "สิทธิ์",
        "สิทธิ์ของแอป",
        "สิทธิ์เข้าถึงพิเศษของแอป",
        "จัดการการอัปเดต",
        "แอปที่ไม่ได้ใช้",
        "ตัวเลือกสำหรับนักพัฒนา",
        "การแก้ไขข้อบกพร่อง USB",
        "รายงานข้อบกพร่อง",
        "เปิดหน้าจอค้างไว้",
    )

    fun normalize(value: CharSequence?): String {
        return SpeakerNameMatcher.normalizeName(value)
    }

    fun isConnectAction(value: CharSequence?): Boolean {
        return normalize(value) in connectActions
    }

    fun isPairAction(value: CharSequence?): Boolean {
        return normalize(value) in pairActions
    }

    fun isConnectedState(value: CharSequence?): Boolean {
        return normalize(value) in connectedStates
    }

    fun containsConnectedState(value: CharSequence?): Boolean {
        val normalized = normalize(value)
        return connectedStates.any { state -> normalized.contains(state) }
    }

    fun containsPairingNotReadyState(value: CharSequence?): Boolean {
        val normalized = normalize(value)
        return pairingNotReadyStates.any { state -> normalized.contains(state) }
    }

    fun isDeviceListNavigation(value: CharSequence?): Boolean {
        val normalized = normalize(value)
        if (
            pairActions.any { action -> normalized == action } ||
            repairPairNavigationTexts.any { text -> normalized == text || normalized.contains(text) }
        ) {
            return false
        }
        return deviceListNavigationTexts.any { text ->
            normalized == text || normalized.contains(text)
        }
    }

    fun isRepairPairNavigation(value: CharSequence?): Boolean {
        val normalized = normalize(value)
        return repairPairNavigationTexts.any { text ->
            normalized == text || normalized.contains(text)
        }
    }

    fun containsPairingPromptExclusion(value: CharSequence?): Boolean {
        val normalized = normalize(value)
        return pairingPromptExclusions.any { exclusion -> normalized.contains(exclusion) }
    }

    fun isGenericSettingsRow(value: CharSequence?): Boolean {
        val normalized = normalize(value)
        return genericSettingsRows.any { text ->
            normalized == text || normalized.contains(text)
        }
    }

    fun isWrongSettingsDestination(value: CharSequence?): Boolean {
        val normalized = normalize(value)
        return wrongSettingsDestinationTexts.any { text ->
            normalized == text || normalized.contains(text)
        }
    }
}
