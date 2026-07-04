package com.btspeakerkeeper.tv.core

object AutomationSessionGuards {
    const val STALE_AUTOMATION_SESSION_MILLIS = 2 * 60_000L
    const val MIN_LIVE_MONITOR_BACKOFF_MILLIS = 2 * 60_000L
    const val LIVE_MONITOR_PROBE_INTERVAL_MILLIS = 30_000L
    const val TARGET_ROW_CONNECT_WAIT_MILLIS = 12_000L
    const val TARGET_ROW_CONNECTING_WAIT_MILLIS = 30_000L

    fun isStaleAutomationSession(sessionId: Long, nowMillis: Long): Boolean {
        return sessionId <= 0L || nowMillis - sessionId > STALE_AUTOMATION_SESSION_MILLIS
    }

    fun liveMonitorBackoffUntil(
        nowMillis: Long,
        cooldownMinutes: Int,
        currentBackoffUntilMillis: Long? = null,
    ): Long {
        if (currentBackoffUntilMillis != null && nowMillis < currentBackoffUntilMillis) {
            return currentBackoffUntilMillis
        }
        val cooldownMillis = cooldownMinutes.coerceAtLeast(0) * 60_000L
        return nowMillis + cooldownMillis.coerceAtLeast(MIN_LIVE_MONITOR_BACKOFF_MILLIS)
    }

    fun nextLiveMonitorProbeAfter(nowMillis: Long): Long {
        return nowMillis + LIVE_MONITOR_PROBE_INTERVAL_MILLIS
    }

    fun isTargetRowConnectWaitTimedOut(startedAtMillis: Long, nowMillis: Long): Boolean {
        return nowMillis - startedAtMillis >= TARGET_ROW_CONNECT_WAIT_MILLIS
    }
}
