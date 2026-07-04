package com.btspeakerkeeper.tv.core

object SingleVisibleRepairFallbackPolicy {
    fun canUse(
        requestedByManualRepair: Boolean,
        targetAddress: CharSequence?,
    ): Boolean {
        return requestedByManualRepair && TargetDeviceMatcher.normalizeAddress(targetAddress).isEmpty()
    }
}
