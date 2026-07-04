package com.btspeakerkeeper.tv.core

enum class ConnectProgressRecoveryAction(
    val consumesRetry: Boolean,
    val resetTargetClickState: Boolean,
    val reopenFreshSettings: Boolean,
) {
    WAIT(
        consumesRetry = false,
        resetTargetClickState = false,
        reopenFreshSettings = false,
    ),
    BACK_AND_RETRY(
        consumesRetry = true,
        resetTargetClickState = true,
        reopenFreshSettings = false,
    ),
    RELAUNCH_SETTINGS_AND_RETRY(
        consumesRetry = true,
        resetTargetClickState = true,
        reopenFreshSettings = true,
    ),
    FINISH(
        consumesRetry = false,
        resetTargetClickState = false,
        reopenFreshSettings = false,
    ),
}

object ConnectProgressRecoveryPolicy {
    private const val MAX_BACK_RECOVERIES = 1
    private const val MAX_FRESH_RELAUNCH_RECOVERIES = 1

    fun decide(
        retryCount: Int,
        maxRetries: Int,
        elapsedMillis: Long,
        state: SpeakerConnectionState,
        backRecoveryCount: Int,
        freshRelaunchCount: Int,
    ): ConnectProgressRecoveryAction {
        if (!hasTimedOut(elapsedMillis, state)) {
            return ConnectProgressRecoveryAction.WAIT
        }

        if (retryCount + 1 >= maxRetries.coerceAtLeast(1)) {
            return ConnectProgressRecoveryAction.FINISH
        }

        return when {
            backRecoveryCount < MAX_BACK_RECOVERIES -> ConnectProgressRecoveryAction.BACK_AND_RETRY
            freshRelaunchCount < MAX_FRESH_RELAUNCH_RECOVERIES ->
                ConnectProgressRecoveryAction.RELAUNCH_SETTINGS_AND_RETRY
            else -> ConnectProgressRecoveryAction.FINISH
        }
    }

    private fun hasTimedOut(elapsedMillis: Long, state: SpeakerConnectionState): Boolean {
        val waitMillis = if (state == SpeakerConnectionState.CONNECTING) {
            AutomationSessionGuards.TARGET_ROW_CONNECTING_WAIT_MILLIS
        } else {
            AutomationSessionGuards.TARGET_ROW_CONNECT_WAIT_MILLIS
        }
        return elapsedMillis >= waitMillis
    }
}
