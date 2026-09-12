package com.btspeakerkeeper.tv.core

enum class LiveMonitorAction { IGNORE, RECONNECT, UPDATE_CONNECTED }

object LiveMonitorStatePolicy {
    fun decide(
        state: SpeakerConnectionState,
        automationActive: Boolean,
        userOwnsSettings: Boolean,
        needsConnectedRefresh: Boolean,
    ): LiveMonitorAction = when {
        automationActive -> LiveMonitorAction.IGNORE
        state == SpeakerConnectionState.CONNECTED && needsConnectedRefresh -> LiveMonitorAction.UPDATE_CONNECTED
        userOwnsSettings -> LiveMonitorAction.IGNORE
        state == SpeakerConnectionState.DISCONNECTED || state == SpeakerConnectionState.TARGET_NOT_PAIRED -> LiveMonitorAction.RECONNECT
        else -> LiveMonitorAction.IGNORE
    }
}
