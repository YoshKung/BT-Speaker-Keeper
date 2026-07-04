package com.btspeakerkeeper.tv.control

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.btspeakerkeeper.tv.bluetooth.BluetoothStateRepository
import com.btspeakerkeeper.tv.core.AutomationMode
import com.btspeakerkeeper.tv.core.ReconnectAutomationPlanner
import com.btspeakerkeeper.tv.core.ReconnectDecision
import com.btspeakerkeeper.tv.core.ReconnectPolicy
import com.btspeakerkeeper.tv.core.ReconnectRuntimeState
import com.btspeakerkeeper.tv.core.SpeakerConnectionState
import com.btspeakerkeeper.tv.core.SingleVisibleRepairFallbackPolicy
import com.btspeakerkeeper.tv.core.TriggerSource
import com.btspeakerkeeper.tv.data.AppPrefs

object ReconnectCoordinator {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val policy = ReconnectPolicy()

    @Volatile
    private var checkingInProgress = false

    fun requestReconnect(context: Context, trigger: TriggerSource) {
        val appContext = context.applicationContext
        val prefs = AppPrefs(appContext)
        val settings = prefs.getSettings()
        val status = prefs.getStatus()
        val bluetooth = BluetoothStateRepository(appContext)
        val now = System.currentTimeMillis()
        val liveMonitorBackoffUntil = prefs.getLiveMonitorBackoffUntilMillis()
        val liveMonitorNextProbeAfter = prefs.getLiveMonitorNextProbeAfterMillis()
        val liveMonitorFailureCount = prefs.getLiveMonitorFailureCount()
        val decision = policy.decide(
            settings = settings,
            runtime = ReconnectRuntimeState(
                inProgress = checkingInProgress || status.automationActive,
                lastAttemptAtMillis = status.lastAttemptAtMillis,
                liveMonitorBackoffUntilMillis = liveMonitorBackoffUntil,
                liveMonitorNextProbeAfterMillis = liveMonitorNextProbeAfter,
                liveMonitorFailureCount = liveMonitorFailureCount,
            ),
            trigger = trigger,
            nowMillis = now,
            isPlaybackActive = PlaybackDetector.isPlaybackActive(appContext),
            isAccessibilityEnabled = AccessibilityUtils.isKeeperServiceEnabled(appContext),
            hasBluetoothPermission = bluetooth.hasBluetoothConnectPermission(),
        )

        when (decision) {
            ReconnectDecision.Proceed -> {
                if (
                    trigger == TriggerSource.LIVE_MONITOR &&
                    liveMonitorBackoffUntil != null &&
                    now < liveMonitorBackoffUntil
                ) {
                    logDebug(
                        appContext,
                        "Live monitor probe allowed during backoff " +
                            "backoffUntil=$liveMonitorBackoffUntil " +
                            "nextProbeAfter=$liveMonitorNextProbeAfter " +
                            "failureCount=$liveMonitorFailureCount",
                    )
                }
                beginBluetoothCheck(appContext, prefs, bluetooth, trigger)
            }

            is ReconnectDecision.Skip -> {
                if (trigger == TriggerSource.LIVE_MONITOR && decision.reason == "Live monitor backoff active") {
                    logDebug(
                        appContext,
                        "Probe skipped until $liveMonitorNextProbeAfter " +
                            "backoffUntil=$liveMonitorBackoffUntil " +
                            "failureCount=$liveMonitorFailureCount",
                    )
                }
                prefs.recordSkipped(trigger, decision.reason)
            }
        }
    }

    private fun beginBluetoothCheck(
        appContext: Context,
        prefs: AppPrefs,
        bluetooth: BluetoothStateRepository,
        trigger: TriggerSource,
    ) {
        checkingInProgress = true
        val settings = prefs.getSettings()
        prefs.recordAttemptStarted(trigger, System.currentTimeMillis())
        bluetooth.checkTargetState(settings.targetDeviceName, settings.targetDeviceAddress) { result ->
            mainHandler.post {
                checkingInProgress = false
                when (result.state) {
                    SpeakerConnectionState.CONNECTED -> {
                        prefs.recordSuccess(nowMillis = System.currentTimeMillis())
                    }

                    SpeakerConnectionState.CONNECTING,
                    SpeakerConnectionState.DISCONNECTING -> {
                        prefs.recordState(result.state)
                    }

                    else -> {
                        when (
                            val automationMode = ReconnectAutomationPlanner.modeFor(
                                state = result.state,
                                trigger = trigger,
                                autoConnectEnabled = settings.autoConnectEnabled,
                            )
                        ) {
                            AutomationMode.CONNECT,
                            AutomationMode.PAIR_REPAIR -> {
                                prefs.requestAutomation(
                                    targetName = settings.targetDeviceName,
                                    targetAddress = settings.targetDeviceAddress,
                                    maxRetries = settings.maxRetryCount,
                                    trigger = trigger,
                                    mode = automationMode,
                                    allowSingleVisibleDeviceRepair = SingleVisibleRepairFallbackPolicy.canUse(
                                        requestedByManualRepair = trigger == TriggerSource.REPAIR_PAIR,
                                        targetAddress = settings.targetDeviceAddress,
                                    ),
                                )
                                if (automationMode == AutomationMode.PAIR_REPAIR) {
                                    SettingsLauncher.openPairAccessorySettings(appContext)
                                } else {
                                    SettingsLauncher.openConnectSettings(appContext)
                                }
                            }

                            null -> {
                                val message = if (
                                    trigger == TriggerSource.LIVE_MONITOR &&
                                    result.state == SpeakerConnectionState.TARGET_NOT_PAIRED
                                ) {
                                    recordLiveMonitorRepairSkipped(appContext, prefs)
                                } else {
                                    result.message ?: result.state.displayName
                                }
                                prefs.recordFailure(state = result.state, message = message)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun recordLiveMonitorRepairSkipped(appContext: Context, prefs: AppPrefs): String {
        val reason = "Live monitor repair skipped: target not paired; use Repair Pair Now"
        val backoff = prefs.recordLiveMonitorBackoff(
            nowMillis = System.currentTimeMillis(),
            cooldownMinutes = prefs.getSettings().cooldownMinutes,
            reason = reason,
        )
        logDebug(
            appContext,
            "live monitor repair skipped backoffUntil=${backoff.backoffUntilMillis} " +
                "nextProbeAfter=${backoff.nextProbeAfterMillis} " +
                "failureCount=${backoff.failureCount}: $reason",
        )
        return reason
    }

    private fun logDebug(context: Context, message: String) {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return
        }
        Log.d(TAG, message)
    }

    private const val TAG = "BtKeeperReconnect"
}
