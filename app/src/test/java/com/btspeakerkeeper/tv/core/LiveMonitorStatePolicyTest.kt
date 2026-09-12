package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveMonitorStatePolicyTest {
    @Test fun reconcilesManualConnectionWithoutTakingOverSettings() {
        assertEquals(LiveMonitorAction.UPDATE_CONNECTED, LiveMonitorStatePolicy.decide(SpeakerConnectionState.CONNECTED, false, true, true))
        assertEquals(LiveMonitorAction.UPDATE_CONNECTED, LiveMonitorStatePolicy.decide(SpeakerConnectionState.CONNECTED, false, false, true))
    }

    @Test fun neverOverwritesAnAutomationSessionStartedWhileTheCheckWasPending() {
        assertEquals(LiveMonitorAction.IGNORE, LiveMonitorStatePolicy.decide(SpeakerConnectionState.CONNECTED, true, false, true))
        assertEquals(LiveMonitorAction.IGNORE, LiveMonitorStatePolicy.decide(SpeakerConnectionState.DISCONNECTED, true, false, true))
    }

    @Test fun stableConnectedChecksDoNotRepeatedlyRecordSuccess() {
        assertEquals(LiveMonitorAction.IGNORE, LiveMonitorStatePolicy.decide(SpeakerConnectionState.CONNECTED, false, false, false))
    }

    @Test fun settingsOwnershipStillPreventsReconnectAndRepair() {
        for (state in listOf(SpeakerConnectionState.DISCONNECTED, SpeakerConnectionState.TARGET_NOT_PAIRED)) {
            assertEquals(LiveMonitorAction.IGNORE, LiveMonitorStatePolicy.decide(state, false, true, true))
            assertEquals(LiveMonitorAction.RECONNECT, LiveMonitorStatePolicy.decide(state, false, false, true))
        }
    }

    @Test fun unknownOrInProgressStatesCannotBeRecordedAsSuccess() {
        for (state in listOf(SpeakerConnectionState.CONNECTING, SpeakerConnectionState.PROFILE_UNAVAILABLE, SpeakerConnectionState.ERROR)) {
            assertEquals(LiveMonitorAction.IGNORE, LiveMonitorStatePolicy.decide(state, false, false, true))
        }
    }
}
