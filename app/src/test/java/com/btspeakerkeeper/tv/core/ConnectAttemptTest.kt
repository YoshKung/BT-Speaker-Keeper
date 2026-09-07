package com.btspeakerkeeper.tv.core

import org.junit.Assert.*
import org.junit.Test

class ConnectAttemptTest {
    @Test
    fun confirmationIsOncePerActivationAndDoesNotExtendTheWait() {
        val attempt = ConnectAttempt(0L)
        assertFalse(attempt.claimConfirmation(0L))
        attempt.recordClick(0L)
        assertTrue(attempt.claimConfirmation(2_000L))
        assertFalse(attempt.claimConfirmation(4_000L))
        assertEquals(12_000L, attempt.waitElapsed(12_000L))
        attempt.beginRecovery()
        assertFalse(attempt.claimConfirmation(13_000L))
        attempt.recordClick(14_000L)
        assertTrue(attempt.claimConfirmation(15_000L))
    }
    @Test
    fun completedFallbackCanConfirmItsNewConnectRequestOnce() {
        val attempt = ConnectAttempt(0L)
        attempt.recordClick(0L)
        attempt.claimConfirmation(2_000L)
        val token = attempt.beginGesture(12_000L)!!
        attempt.finishGesture(token, true, 12_100L)
        assertTrue(attempt.claimConfirmation(14_000L))
        assertFalse(attempt.claimConfirmation(15_000L))
        assertEquals(2_900L, attempt.waitElapsed(15_000L))
    }

    @Test
    fun persistentConnectButtonAndRepeatedEventsNeverRedispatchOrRestartTheWait() {
        val attempt = ConnectAttempt(startedAtMillis = 0L)
        assertEquals(ConnectStep.ACTIVATE, attempt.nextStep(true, 0L))
        assertTrue(attempt.recordClick(0L))
        for (time in listOf(700L, 2_000L, 8_000L, 12_000L)) {
            assertEquals(ConnectStep.CHECK_A2DP, attempt.nextStep(true, time))
            assertEquals(ConnectStep.CHECK_A2DP, attempt.nextStep(false, time))
            assertFalse(attempt.recordClick(time))
        }
        assertEquals(12_000L, attempt.waitElapsed(12_000L))
        assertTrue(attempt.canTryGesture(SpeakerConnectionState.DISCONNECTED, 12_000L))
    }

    @Test
    fun fallbackOnlyRunsAfterDisconnectedTimeoutAndOnlyOnceEvenAfterRecovery() {
        val attempt = ConnectAttempt(0L)
        attempt.recordClick(0L)
        assertFalse(attempt.canTryGesture(SpeakerConnectionState.DISCONNECTED, 11_999L))
        assertFalse(attempt.canTryGesture(SpeakerConnectionState.CONNECTING, 12_000L))
        assertFalse(attempt.canTryGesture(SpeakerConnectionState.PROFILE_UNAVAILABLE, 12_000L))
        val token = attempt.beginGesture(12_000L)!!
        assertEquals(ConnectStep.WAIT_FOR_GESTURE, attempt.nextStep(true, 12_100L))
        assertTrue(attempt.finishGesture(token, completed = true, nowMillis = 12_100L))
        assertEquals(ConnectStep.CHECK_A2DP, attempt.nextStep(true, 14_000L))
        assertEquals(1_900L, attempt.waitElapsed(14_000L))
        assertFalse(attempt.canTryGesture(SpeakerConnectionState.DISCONNECTED, 30_000L))
        attempt.beginRecovery()
        assertEquals(ConnectStep.ACTIVATE, attempt.nextStep(true, 31_000L))
        assertTrue(attempt.recordClick(31_000L))
        assertFalse(attempt.canTryGesture(SpeakerConnectionState.DISCONNECTED, 43_000L))
        assertNull(attempt.beginGesture(43_000L))
    }

    @Test
    fun cancelledGestureDoesNotStartAnotherFullWaitAndLateCompletionIsIgnored() {
        val attempt = ConnectAttempt(0L)
        attempt.recordClick(0L)
        val token = attempt.beginGesture(12_000L)!!
        assertTrue(attempt.finishGesture(token, completed = false, nowMillis = 12_100L))
        assertEquals(12_100L, attempt.waitElapsed(12_100L))
        assertFalse(attempt.finishGesture(token, completed = true, nowMillis = 15_000L))
        assertEquals(15_000L, attempt.waitElapsed(15_000L))
    }

    @Test
    fun recoveryInvalidatesAnOutstandingGesture() {
        val attempt = ConnectAttempt(0L)
        attempt.recordClick(0L)
        val token = attempt.beginGesture(12_000L)!!
        attempt.beginRecovery()
        assertFalse(attempt.finishGesture(token, true, 13_000L))
        assertEquals(ConnectStep.SELECT_TARGET, attempt.nextStep(false, 13_000L))
    }

    @Test
    fun sessionBudgetCannotBeExtendedByClicksRecoveriesOrEvents() {
        val attempt = ConnectAttempt(0L)
        attempt.recordClick(10_000L)
        attempt.beginRecovery()
        attempt.recordClick(110_000L)
        assertEquals(ConnectStep.CHECK_A2DP, attempt.nextStep(true, 119_999L))
        assertEquals(ConnectStep.EXPIRED, attempt.nextStep(true, 120_000L))
        assertNull(attempt.beginGesture(120_000L))
        assertFalse(attempt.recordClick(120_000L))
    }
}
