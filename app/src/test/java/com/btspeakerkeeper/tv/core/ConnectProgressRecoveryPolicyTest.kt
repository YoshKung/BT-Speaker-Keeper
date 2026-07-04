package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectProgressRecoveryPolicyTest {
    @Test
    fun waitsWhileDisconnectedProgressIsInsideNormalTimeout() {
        assertEquals(
            ConnectProgressRecoveryAction.WAIT,
            ConnectProgressRecoveryPolicy.decide(
                retryCount = 0,
                maxRetries = 3,
                elapsedMillis = AutomationSessionGuards.TARGET_ROW_CONNECT_WAIT_MILLIS - 1L,
                state = SpeakerConnectionState.DISCONNECTED,
                backRecoveryCount = 0,
                freshRelaunchCount = 0,
            ),
        )
    }

    @Test
    fun backsOutAfterStuckDisconnectedProgress() {
        val action = ConnectProgressRecoveryPolicy.decide(
            retryCount = 0,
            maxRetries = 3,
            elapsedMillis = AutomationSessionGuards.TARGET_ROW_CONNECT_WAIT_MILLIS,
            state = SpeakerConnectionState.DISCONNECTED,
            backRecoveryCount = 0,
            freshRelaunchCount = 0,
        )

        assertEquals(ConnectProgressRecoveryAction.BACK_AND_RETRY, action)
        assertTrue(action.consumesRetry)
        assertTrue(action.resetTargetClickState)
        assertFalse(action.reopenFreshSettings)
    }

    @Test
    fun extendsWaitOnlyWhileA2dpReportsConnecting() {
        assertEquals(
            ConnectProgressRecoveryAction.WAIT,
            ConnectProgressRecoveryPolicy.decide(
                retryCount = 0,
                maxRetries = 3,
                elapsedMillis = AutomationSessionGuards.TARGET_ROW_CONNECT_WAIT_MILLIS,
                state = SpeakerConnectionState.CONNECTING,
                backRecoveryCount = 0,
                freshRelaunchCount = 0,
            ),
        )
        assertEquals(
            ConnectProgressRecoveryAction.BACK_AND_RETRY,
            ConnectProgressRecoveryPolicy.decide(
                retryCount = 0,
                maxRetries = 3,
                elapsedMillis = AutomationSessionGuards.TARGET_ROW_CONNECTING_WAIT_MILLIS,
                state = SpeakerConnectionState.CONNECTING,
                backRecoveryCount = 0,
                freshRelaunchCount = 0,
            ),
        )
    }

    @Test
    fun relaunchesSettingsAfterOneBackRecovery() {
        val action = ConnectProgressRecoveryPolicy.decide(
            retryCount = 1,
            maxRetries = 3,
            elapsedMillis = AutomationSessionGuards.TARGET_ROW_CONNECT_WAIT_MILLIS,
            state = SpeakerConnectionState.DISCONNECTED,
            backRecoveryCount = 1,
            freshRelaunchCount = 0,
        )

        assertEquals(ConnectProgressRecoveryAction.RELAUNCH_SETTINGS_AND_RETRY, action)
        assertTrue(action.consumesRetry)
        assertTrue(action.resetTargetClickState)
        assertTrue(action.reopenFreshSettings)
    }

    @Test
    fun finishesAfterBoundedRecoveries() {
        assertEquals(
            ConnectProgressRecoveryAction.FINISH,
            ConnectProgressRecoveryPolicy.decide(
                retryCount = 2,
                maxRetries = 3,
                elapsedMillis = AutomationSessionGuards.TARGET_ROW_CONNECT_WAIT_MILLIS,
                state = SpeakerConnectionState.DISCONNECTED,
                backRecoveryCount = 1,
                freshRelaunchCount = 1,
            ),
        )
    }
}
