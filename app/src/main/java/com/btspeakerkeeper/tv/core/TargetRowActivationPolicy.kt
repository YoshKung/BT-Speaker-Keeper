package com.btspeakerkeeper.tv.core

enum class TargetRowActivationAction {
    FOCUS,
    WAIT_FOR_DETAIL_CONNECT,
    CLICK_DIRECT_CONNECT,
    IGNORE,
}

object TargetRowActivationPolicy {
    fun decide(
        isTargetRowFocused: Boolean,
        targetRowText: CharSequence?,
        targetName: CharSequence?,
        targetAddress: CharSequence?,
    ): TargetRowActivationAction {
        if (!AutomationSafetyPolicy.hasTargetContext(targetRowText, targetName, targetAddress)) {
            return TargetRowActivationAction.IGNORE
        }

        if (AutomationSafetyPolicy.isTargetConnectContext(targetRowText, targetName, targetAddress)) {
            return TargetRowActivationAction.CLICK_DIRECT_CONNECT
        }

        return if (isTargetRowFocused) {
            TargetRowActivationAction.WAIT_FOR_DETAIL_CONNECT
        } else {
            TargetRowActivationAction.FOCUS
        }
    }
}
