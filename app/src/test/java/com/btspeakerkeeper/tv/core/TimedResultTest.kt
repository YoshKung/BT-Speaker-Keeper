package com.btspeakerkeeper.tv.core

import org.junit.Assert.*
import org.junit.Test

class TimedResultTest {
    @Test
    fun missingCallbackCompletesAtFourSecondsAndLateCallbacksCannotOverwriteTimeout() {
        val results = mutableListOf<String>()
        val result = TimedResult(0L, 4_000L, "timeout", results::add)
        assertFalse(result.expire(3_999L))
        assertTrue(results.isEmpty())
        assertTrue(result.expire(4_000L))
        assertFalse(result.complete("connected", 4_001L))
        assertFalse(result.expire(8_000L))
        assertEquals(listOf("timeout"), results)
    }

    @Test
    fun successfulResultCompletesExactlyOnce() {
        val results = mutableListOf<String>()
        val result = TimedResult(0L, 4_000L, "timeout", results::add)
        assertTrue(result.complete("connected", 3_999L))
        assertFalse(result.complete("disconnected", 3_999L))
        assertFalse(result.expire(4_000L))
        assertEquals(listOf("connected"), results)
    }

    @Test
    fun callbackArrivingAtDeadlineCannotBeatADelayedTimeoutRunnable() {
        val results = mutableListOf<String>()
        val result = TimedResult(0L, 4_000L, "timeout", results::add)
        result.complete("connected", 4_000L)
        assertEquals(listOf("timeout"), results)
    }
}
