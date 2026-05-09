package com.btspeakerkeeper.tv.core

object ReconnectAutomationPlanner {
    fun modeFor(
        state: SpeakerConnectionState,
        trigger: TriggerSource,
        autoConnectEnabled: Boolean,
    ): AutomationMode? {
        return when (state) {
            SpeakerConnectionState.DISCONNECTED -> AutomationMode.CONNECT
            SpeakerConnectionState.TARGET_NOT_PAIRED -> {
                if (autoConnectEnabled || trigger == TriggerSource.REPAIR_PAIR) {
                    AutomationMode.PAIR_REPAIR
                } else {
                    null
                }
            }

            else -> null
        }
    }
}
