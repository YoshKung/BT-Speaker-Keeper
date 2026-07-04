package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectRecoveryForegroundPolicyTest {
    @Test
    fun relaunchesSettingsWhenBackRecoveryLeavesAutomationWindow() {
        assertEquals(
            ConnectRecoveryForegroundAction.RELAUNCH_SETTINGS,
            ConnectRecoveryForegroundPolicy.decide(
                isAutomationWindowPackage = false,
                retryCount = 1,
                maxRetries = 5,
                targetClicked = false,
                backRecoveryCount = 1,
                freshRelaunchCount = 0,
            ),
        )
    }

    @Test
    fun waitsNormallyBeforeAnyBackRecovery() {
        assertEquals(
            ConnectRecoveryForegroundAction.WAIT,
            ConnectRecoveryForegroundPolicy.decide(
                isAutomationWindowPackage = false,
                retryCount = 0,
                maxRetries = 5,
                targetClicked = false,
                backRecoveryCount = 0,
                freshRelaunchCount = 0,
            ),
        )
    }

    @Test
    fun doesNotRelaunchMoreThanOnceAfterBackRecovery() {
        assertEquals(
            ConnectRecoveryForegroundAction.WAIT,
            ConnectRecoveryForegroundPolicy.decide(
                isAutomationWindowPackage = false,
                retryCount = 2,
                maxRetries = 5,
                targetClicked = false,
                backRecoveryCount = 1,
                freshRelaunchCount = 1,
            ),
        )
    }

    @Test
    fun finishesWhenRelaunchWouldExhaustRetries() {
        assertEquals(
            ConnectRecoveryForegroundAction.FINISH,
            ConnectRecoveryForegroundPolicy.decide(
                isAutomationWindowPackage = false,
                retryCount = 4,
                maxRetries = 5,
                targetClicked = false,
                backRecoveryCount = 1,
                freshRelaunchCount = 0,
            ),
        )
    }
}
