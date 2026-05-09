package com.btspeakerkeeper.tv.core

class ReconnectPolicy {
    fun decide(
        settings: ReconnectSettings,
        runtime: ReconnectRuntimeState,
        trigger: TriggerSource,
        nowMillis: Long,
        isPlaybackActive: Boolean,
        isAccessibilityEnabled: Boolean,
        hasBluetoothPermission: Boolean,
    ): ReconnectDecision {
        if (SpeakerNameMatcher.normalizeName(settings.targetDeviceName).isEmpty()) {
            return ReconnectDecision.Skip("Target speaker name is empty")
        }

        if (runtime.inProgress) {
            return ReconnectDecision.Skip("Reconnect already in progress")
        }

        if (!hasBluetoothPermission) {
            return ReconnectDecision.Skip("Nearby Devices permission missing")
        }

        if (!isAccessibilityEnabled) {
            return ReconnectDecision.Skip("Accessibility Service disabled")
        }

        if (trigger.requiresAutoConnect && !settings.autoConnectEnabled) {
            return ReconnectDecision.Skip("Auto Connect is off")
        }

        if (trigger.respectsPlaybackSafety && settings.skipWhilePlaybackActive && isPlaybackActive) {
            return ReconnectDecision.Skip("Playback active")
        }

        val cooldownMillis = settings.cooldownMinutes.coerceAtLeast(0) * 60_000L
        val lastAttemptAt = runtime.lastAttemptAtMillis
        if (!trigger.bypassCooldown && cooldownMillis > 0 && lastAttemptAt != null) {
            val elapsed = nowMillis - lastAttemptAt
            if (elapsed in 0 until cooldownMillis) {
                return ReconnectDecision.Skip("Cooldown active")
            }
        }

        return ReconnectDecision.Proceed
    }
}
