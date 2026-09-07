package com.btspeakerkeeper.tv.core

object VisibleConnectAction {
    fun tapPoint(
        label: CharSequence?,
        visible: Boolean,
        enabled: Boolean,
        hasTargetContext: Boolean,
        safeWindow: Boolean,
        actionBounds: UiBounds,
        screenBounds: UiBounds,
    ): TapPoint? {
        if (!AutomationTextMatcher.isConnectAction(label) || !visible || !enabled || !hasTargetContext || !safeWindow) {
            return null
        }
        if (actionBounds.width <= 0 || actionBounds.bottom <= actionBounds.top ||
            screenBounds.width <= 0 || screenBounds.bottom <= screenBounds.top ||
            actionBounds.left < screenBounds.left || actionBounds.top < screenBounds.top ||
            actionBounds.right > screenBounds.right || actionBounds.bottom > screenBounds.bottom
        ) return null
        return TapPoint(actionBounds.left + actionBounds.width / 2, actionBounds.centerY)
    }

    fun isSafeContainer(labels: List<String>): Boolean {
        val normalized = labels.map(AutomationTextMatcher::normalize)
        return normalized.any(AutomationTextMatcher::isConnectAction) &&
            normalized.none { it in forbiddenActions }
    }

    private val forbiddenActions = setOf(
        "Disconnect", "Unpair", "Forget", "Forget device", "Rename", "Change name",
        "เลิกเชื่อมต่อ", "ยกเลิกการเชื่อมต่อ", "ยกเลิกการจับคู่", "ลืม", "ลืมอุปกรณ์", "ไม่จำ", "เปลี่ยนชื่อ",
    )
}
