package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    private val policy = ReconnectPolicy()
    private val settings = ReconnectSettings(
        targetDeviceName = "Demo Speaker Speaker",
        autoConnectEnabled = true,
        skipWhilePlaybackActive = true,
        maxRetryCount = 3,
        cooldownMinutes = 10,
        periodicRetryEnabled = false,
        periodicRetryMinutes = 60,
    )

    @Test
    fun manualTriggerBypassesAutoConnectAndCooldown() {
        val decision = policy.decide(
            settings = settings.copy(autoConnectEnabled = false),
            runtime = ReconnectRuntimeState(
                inProgress = false,
                lastAttemptAtMillis = 1_000L,
            ),
            trigger = TriggerSource.MANUAL,
            nowMillis = 2_000L,
            isPlaybackActive = false,
            isAccessibilityEnabled = true,
            hasBluetoothPermission = true,
        )

        assertTrue(decision is ReconnectDecision.Proceed)
    }

    @Test
    fun automaticTriggerRequiresAutoConnect() {
        val decision = policy.decide(
            settings = settings.copy(autoConnectEnabled = false),
            runtime = ReconnectRuntimeState(inProgress = false, lastAttemptAtMillis = null),
            trigger = TriggerSource.APP_OPEN,
            nowMillis = 2_000L,
            isPlaybackActive = false,
            isAccessibilityEnabled = true,
            hasBluetoothPermission = true,
        )

        assertEquals(ReconnectDecision.Skip("Auto Connect is off"), decision)
    }

    @Test
    fun automaticTriggerRespectsCooldown() {
        val decision = policy.decide(
            settings = settings,
            runtime = ReconnectRuntimeState(inProgress = false, lastAttemptAtMillis = 60_000L),
            trigger = TriggerSource.BOOT,
            nowMillis = 120_000L,
            isPlaybackActive = false,
            isAccessibilityEnabled = true,
            hasBluetoothPermission = true,
        )

        assertEquals(ReconnectDecision.Skip("Cooldown active"), decision)
    }

    @Test
    fun pairAcceptedBypassesCooldown() {
        val decision = policy.decide(
            settings = settings,
            runtime = ReconnectRuntimeState(inProgress = false, lastAttemptAtMillis = 60_000L),
            trigger = TriggerSource.PAIR_ACCEPTED,
            nowMillis = 120_000L,
            isPlaybackActive = false,
            isAccessibilityEnabled = true,
            hasBluetoothPermission = true,
        )

        assertTrue(decision is ReconnectDecision.Proceed)
    }

    @Test
    fun liveMonitorRequiresAutoConnect() {
        val decision = policy.decide(
            settings = settings.copy(autoConnectEnabled = false),
            runtime = ReconnectRuntimeState(inProgress = false, lastAttemptAtMillis = null),
            trigger = TriggerSource.LIVE_MONITOR,
            nowMillis = 120_000L,
            isPlaybackActive = false,
            isAccessibilityEnabled = true,
            hasBluetoothPermission = true,
        )

        assertEquals(ReconnectDecision.Skip("Auto Connect is off"), decision)
    }

    @Test
    fun liveMonitorBypassesCooldown() {
        val decision = policy.decide(
            settings = settings,
            runtime = ReconnectRuntimeState(inProgress = false, lastAttemptAtMillis = 60_000L),
            trigger = TriggerSource.LIVE_MONITOR,
            nowMillis = 120_000L,
            isPlaybackActive = false,
            isAccessibilityEnabled = true,
            hasBluetoothPermission = true,
        )

        assertTrue(decision is ReconnectDecision.Proceed)
    }

    @Test
    fun liveMonitorRespectsPlaybackSafety() {
        val decision = policy.decide(
            settings = settings,
            runtime = ReconnectRuntimeState(inProgress = false, lastAttemptAtMillis = null),
            trigger = TriggerSource.LIVE_MONITOR,
            nowMillis = 120_000L,
            isPlaybackActive = true,
            isAccessibilityEnabled = true,
            hasBluetoothPermission = true,
        )

        assertEquals(ReconnectDecision.Skip("Playback active"), decision)
    }

    @Test
    fun automaticTriggerSkipsPlaybackWhenSafetyEnabled() {
        val decision = policy.decide(
            settings = settings,
            runtime = ReconnectRuntimeState(inProgress = false, lastAttemptAtMillis = null),
            trigger = TriggerSource.PERIODIC,
            nowMillis = 120_000L,
            isPlaybackActive = true,
            isAccessibilityEnabled = true,
            hasBluetoothPermission = true,
        )

        assertEquals(ReconnectDecision.Skip("Playback active"), decision)
    }
}
