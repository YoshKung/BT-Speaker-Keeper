package com.btspeakerkeeper.tv.core

enum class ConnectRecoveryForegroundAction {
    WAIT,
    RELAUNCH_SETTINGS,
    FINISH,
}

object ConnectRecoveryForegroundPolicy {
    fun decide(
        isAutomationWindowPackage: Boolean,
        retryCount: Int,
        maxRetries: Int,
        targetClicked: Boolean,
        backRecoveryCount: Int,
        freshRelaunchCount: Int,
    ): ConnectRecoveryForegroundAction {
        if (isAutomationWindowPackage || targetClicked) {
            return ConnectRecoveryForegroundAction.WAIT
        }

        if (backRecoveryCount <= 0 || freshRelaunchCount > 0) {
            return ConnectRecoveryForegroundAction.WAIT
        }

        return if (retryCount + 1 >= maxRetries.coerceAtLeast(1)) {
            ConnectRecoveryForegroundAction.FINISH
        } else {
            ConnectRecoveryForegroundAction.RELAUNCH_SETTINGS
        }
    }
}
