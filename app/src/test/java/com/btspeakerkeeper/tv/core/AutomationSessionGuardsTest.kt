package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSessionGuardsTest {
    @Test
    fun clearsLegacyOrExpiredAutomationSessions() {
        val now = 200_000L

        assertTrue(AutomationSessionGuards.isStaleAutomationSession(0L, now))
        assertTrue(
            AutomationSessionGuards.isStaleAutomationSession(
                now - AutomationSessionGuards.STALE_AUTOMATION_SESSION_MILLIS - 1L,
                now,
            ),
        )
        assertFalse(
            AutomationSessionGuards.isStaleAutomationSession(
                now - AutomationSessionGuards.STALE_AUTOMATION_SESSION_MILLIS,
                now,
            ),
        )
    }

    @Test
    fun liveMonitorBackoffUsesCooldownWithTwoMinuteSafetyFloor() {
        assertEquals(220_000L, AutomationSessionGuards.liveMonitorBackoffUntil(100_000L, 0))
        assertEquals(220_000L, AutomationSessionGuards.liveMonitorBackoffUntil(100_000L, 1))
        assertEquals(400_000L, AutomationSessionGuards.liveMonitorBackoffUntil(100_000L, 5))
    }

    @Test
    fun liveMonitorBackoffKeepsExistingActiveCooldownWindow() {
        assertEquals(
            500_000L,
            AutomationSessionGuards.liveMonitorBackoffUntil(
                nowMillis = 200_000L,
                cooldownMinutes = 5,
                currentBackoffUntilMillis = 500_000L,
            ),
        )
        assertEquals(
            500_000L,
            AutomationSessionGuards.liveMonitorBackoffUntil(
                nowMillis = 200_000L,
                cooldownMinutes = 5,
                currentBackoffUntilMillis = 150_000L,
            ),
        )
    }

    @Test
    fun schedulesLiveMonitorProbeThirtySecondsLater() {
        assertEquals(130_000L, AutomationSessionGuards.nextLiveMonitorProbeAfter(100_000L))
    }

    @Test
    fun targetRowConnectWaitTimesOutAfterBoundedWindow() {
        assertFalse(AutomationSessionGuards.isTargetRowConnectWaitTimedOut(100_000L, 111_999L))
        assertTrue(AutomationSessionGuards.isTargetRowConnectWaitTimedOut(100_000L, 112_000L))
    }
}
