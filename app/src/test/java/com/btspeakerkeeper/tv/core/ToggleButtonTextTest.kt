package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ToggleButtonTextTest {
    @Test
    fun formatsSkipPlaybackStateForRemoteButton() {
        assertEquals("Skip while playback: ON", ToggleButtonText.skipPlayback(true))
        assertEquals("Skip while playback: OFF", ToggleButtonText.skipPlayback(false))
    }

    @Test
    fun formatsPeriodicRetryStateForRemoteButton() {
        assertEquals("Periodic retry: ON", ToggleButtonText.periodicRetry(true))
        assertEquals("Periodic retry: OFF", ToggleButtonText.periodicRetry(false))
    }
}
