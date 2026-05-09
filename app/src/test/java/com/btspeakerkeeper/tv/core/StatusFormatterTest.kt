package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class StatusFormatterTest {
    @Test
    fun formatsMissingTimeAsNever() {
        assertEquals("Never", StatusFormatter.formatTime(null, ZoneId.of("UTC")))
    }

    @Test
    fun formatsEpochMillisWithStableZone() {
        assertEquals(
            "2026-05-08 10:15:30",
            StatusFormatter.formatTime(1_778_235_330_000L, ZoneId.of("UTC")),
        )
    }
}
