package com.btspeakerkeeper.tv.core

data class SavedDeviceRow(
    val labels: List<String>,
    val bounds: UiBounds,
    val focused: Boolean,
    val visible: Boolean,
    val enabled: Boolean,
    val clickable: Boolean,
)

class SavedDeviceDetailsAttempt {
    private var opened = false

    fun takeRow(rows: List<SavedDeviceRow>, targetName: String, targetAddress: String, screen: UiBounds): Int? {
        if (opened) return null
        val index = rows.indices.filter { index ->
            val row = rows[index]
            val labels = row.labels.map(AutomationTextMatcher::normalize).filter(String::isNotEmpty)
            val isTarget: (String) -> Boolean = { label ->
                SpeakerNameMatcher.matchesExactConfiguredName(label, targetName) ||
                    TargetDeviceMatcher.addressesEqual(label, targetAddress)
            }
            row.focused && row.visible && row.enabled && row.clickable &&
                row.bounds.width > 0 && row.bounds.bottom > row.bounds.top &&
                row.bounds.left >= screen.left && row.bounds.right <= screen.left + screen.width / 2 &&
                row.bounds.top >= screen.top && row.bounds.bottom <= screen.bottom &&
                labels.any(isTarget) && labels.any { it in disconnectedLabels } &&
                labels.all { isTarget(it) || it in disconnectedLabels }
        }.singleOrNull() ?: return null
        opened = true
        return index
    }

    private val disconnectedLabels = setOf(
        "Disconnected", "Not connected", "เลิกเชื่อมต่อแล้ว", "ไม่ได้เชื่อมต่อ", "ไม่เชื่อมต่อ",
    )
}
