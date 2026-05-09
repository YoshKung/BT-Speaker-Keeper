package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconnectAutomationPlannerTest {
    @Test
    fun startsPairRepairWhenAutoTargetIsNotPaired() {
        assertEquals(
            AutomationMode.PAIR_REPAIR,
            ReconnectAutomationPlanner.modeFor(
                state = SpeakerConnectionState.TARGET_NOT_PAIRED,
                trigger = TriggerSource.BOOT,
                autoConnectEnabled = true,
            ),
        )
    }

    @Test
    fun doesNotStartPairRepairForAutomaticTriggerWhenAutoConnectIsOff() {
        assertNull(
            ReconnectAutomationPlanner.modeFor(
                state = SpeakerConnectionState.TARGET_NOT_PAIRED,
                trigger = TriggerSource.BOOT,
                autoConnectEnabled = false,
            ),
        )
    }

    @Test
    fun startsPairRepairForManualRepairEvenWhenAutoConnectIsOff() {
        assertEquals(
            AutomationMode.PAIR_REPAIR,
            ReconnectAutomationPlanner.modeFor(
                state = SpeakerConnectionState.TARGET_NOT_PAIRED,
                trigger = TriggerSource.REPAIR_PAIR,
                autoConnectEnabled = false,
            ),
        )
    }

    @Test
    fun startsConnectAutomationWhenTargetIsDisconnected() {
        assertEquals(
            AutomationMode.CONNECT,
            ReconnectAutomationPlanner.modeFor(
                state = SpeakerConnectionState.DISCONNECTED,
                trigger = TriggerSource.BOOT,
                autoConnectEnabled = true,
            ),
        )
    }
}
