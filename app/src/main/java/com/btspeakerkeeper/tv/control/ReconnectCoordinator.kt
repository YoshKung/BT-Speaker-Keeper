package com.btspeakerkeeper.tv.control

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.btspeakerkeeper.tv.bluetooth.BluetoothStateRepository
import com.btspeakerkeeper.tv.core.AutomationMode
import com.btspeakerkeeper.tv.core.ReconnectAutomationPlanner
import com.btspeakerkeeper.tv.core.ReconnectDecision
import com.btspeakerkeeper.tv.core.ReconnectPolicy
import com.btspeakerkeeper.tv.core.ReconnectRuntimeState
import com.btspeakerkeeper.tv.core.SpeakerConnectionState
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
        val decision = policy.decide(
            settings = settings,
            runtime = ReconnectRuntimeState(
                inProgress = checkingInProgress || status.automationActive,
                lastAttemptAtMillis = status.lastAttemptAtMillis,
            ),
            trigger = trigger,
            nowMillis = now,
            isPlaybackActive = PlaybackDetector.isPlaybackActive(appContext),
            isAccessibilityEnabled = AccessibilityUtils.isKeeperServiceEnabled(appContext),
            hasBluetoothPermission = bluetooth.hasBluetoothConnectPermission(),
        )

        when (decision) {
            ReconnectDecision.Proceed -> beginBluetoothCheck(appContext, prefs, bluetooth, trigger)
            is ReconnectDecision.Skip -> prefs.recordSkipped(trigger, decision.reason)
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
                                    mode = automationMode,
                                    allowSingleVisibleDeviceRepair = trigger == TriggerSource.REPAIR_PAIR,
                                )
                                if (automationMode == AutomationMode.PAIR_REPAIR) {
                                    SettingsLauncher.openPairAccessorySettings(appContext)
                                } else {
                                    SettingsLauncher.openConnectSettings(appContext)
                                }
                            }

                            null -> {
                                prefs.recordFailure(
                                    state = result.state,
                                    message = result.message ?: result.state.displayName,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
