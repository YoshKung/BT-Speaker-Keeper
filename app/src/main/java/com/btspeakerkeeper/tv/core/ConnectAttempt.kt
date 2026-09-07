package com.btspeakerkeeper.tv.core

enum class ConnectStep { SELECT_TARGET, ACTIVATE, CHECK_A2DP, WAIT_FOR_GESTURE, EXPIRED }
enum class ConnectPhase { SELECTING_TARGET, WAITING_A2DP, DISPATCHING_GESTURE, RECOVERING }

class ConnectAttempt(private val startedAtMillis: Long) {
    var phase = ConnectPhase.SELECTING_TARGET
        private set
    private var clickedAtMillis: Long? = null
    private var gestureAttempted = false
    private var gestureToken = 0L
    private var confirmationSent = false

    fun nextStep(hasConnectAction: Boolean, nowMillis: Long): ConnectStep = when {
        isExpired(nowMillis) -> ConnectStep.EXPIRED
        phase == ConnectPhase.DISPATCHING_GESTURE -> ConnectStep.WAIT_FOR_GESTURE
        phase == ConnectPhase.WAITING_A2DP -> ConnectStep.CHECK_A2DP
        hasConnectAction -> ConnectStep.ACTIVATE
        else -> ConnectStep.SELECT_TARGET
    }

    fun recordClick(nowMillis: Long): Boolean {
        if (isExpired(nowMillis) || phase == ConnectPhase.WAITING_A2DP || phase == ConnectPhase.DISPATCHING_GESTURE) {
            return false
        }
        clickedAtMillis = nowMillis
        confirmationSent = false
        phase = ConnectPhase.WAITING_A2DP
        return true
    }

    fun waitElapsed(nowMillis: Long): Long = (nowMillis - (clickedAtMillis ?: nowMillis)).coerceAtLeast(0L)

    fun claimConfirmation(nowMillis: Long): Boolean {
        if (isExpired(nowMillis) || phase != ConnectPhase.WAITING_A2DP || confirmationSent) return false
        confirmationSent = true
        return true
    }

    fun isExpired(nowMillis: Long): Boolean = remainingMillis(nowMillis) == 0L

    fun remainingMillis(nowMillis: Long): Long =
        (AutomationSessionGuards.STALE_AUTOMATION_SESSION_MILLIS - (nowMillis - startedAtMillis)).coerceAtLeast(0L)

    fun canTryGesture(state: SpeakerConnectionState, nowMillis: Long): Boolean =
        !isExpired(nowMillis) && !gestureAttempted && phase == ConnectPhase.WAITING_A2DP &&
            state == SpeakerConnectionState.DISCONNECTED &&
            waitElapsed(nowMillis) >= AutomationSessionGuards.TARGET_ROW_CONNECT_WAIT_MILLIS

    fun beginGesture(nowMillis: Long): Long? {
        if (isExpired(nowMillis) || gestureAttempted) return null
        gestureAttempted = true
        phase = ConnectPhase.DISPATCHING_GESTURE
        return ++gestureToken
    }

    fun finishGesture(token: Long, completed: Boolean, nowMillis: Long): Boolean {
        if (isExpired(nowMillis) || phase != ConnectPhase.DISPATCHING_GESTURE || token != gestureToken) return false
        phase = ConnectPhase.WAITING_A2DP
        // A rejected/cancelled gesture must not buy another full connection wait.
        if (completed) {
            clickedAtMillis = nowMillis
            confirmationSent = false
        }
        if (clickedAtMillis == null) clickedAtMillis = nowMillis - AutomationSessionGuards.TARGET_ROW_CONNECT_WAIT_MILLIS
        return true
    }

    fun beginRecovery() {
        phase = ConnectPhase.RECOVERING
        clickedAtMillis = null
        gestureToken++
    }
}
