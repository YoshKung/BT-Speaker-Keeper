package com.btspeakerkeeper.tv.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.btspeakerkeeper.tv.PairingAssistActivity
import com.btspeakerkeeper.tv.bluetooth.BluetoothStateRepository
import com.btspeakerkeeper.tv.core.AutomationMode
import com.btspeakerkeeper.tv.core.AutomationSafetyPolicy
import com.btspeakerkeeper.tv.core.AutomationSessionGuards
import com.btspeakerkeeper.tv.core.AutomationTextMatcher
import com.btspeakerkeeper.tv.core.AutomationWindowKind
import com.btspeakerkeeper.tv.core.ConnectRecoveryForegroundAction
import com.btspeakerkeeper.tv.core.ConnectRecoveryForegroundPolicy
import com.btspeakerkeeper.tv.core.ConnectCandidateSelectionPolicy
import com.btspeakerkeeper.tv.core.ConnectCandidateSignal
import com.btspeakerkeeper.tv.core.ConnectAttempt
import com.btspeakerkeeper.tv.core.ConnectPhase
import com.btspeakerkeeper.tv.core.ConnectStep
import com.btspeakerkeeper.tv.core.ConnectProgressRecoveryAction
import com.btspeakerkeeper.tv.core.ConnectProgressRecoveryPolicy
import com.btspeakerkeeper.tv.core.VisibleConnectAction
import com.btspeakerkeeper.tv.core.LivePairPromptGuard
import com.btspeakerkeeper.tv.core.LiveMonitorForegroundGuard
import com.btspeakerkeeper.tv.core.SpeakerConnectionState
import com.btspeakerkeeper.tv.core.SpeakerNameMatcher
import com.btspeakerkeeper.tv.core.SingleVisibleRepairFallbackPolicy
import com.btspeakerkeeper.tv.core.TargetRowActivationAction
import com.btspeakerkeeper.tv.core.TargetRowActivationPolicy
import com.btspeakerkeeper.tv.core.TargetDeviceMatcher
import com.btspeakerkeeper.tv.core.TriggerSource
import com.btspeakerkeeper.tv.core.UiBounds
import com.btspeakerkeeper.tv.core.SettingsNavigationScrollPolicy
import com.btspeakerkeeper.tv.core.SettingsScrollCandidate
import com.btspeakerkeeper.tv.core.SettingsWindowTextPolicy
import com.btspeakerkeeper.tv.core.ConnectConfirmationPolicy
import com.btspeakerkeeper.tv.core.SavedDeviceDetailsAttempt
import com.btspeakerkeeper.tv.core.SavedDeviceRow
import com.btspeakerkeeper.tv.core.LiveMonitorAction
import com.btspeakerkeeper.tv.core.LiveMonitorStatePolicy
import com.btspeakerkeeper.tv.control.PlaybackDetector
import com.btspeakerkeeper.tv.control.ReconnectCoordinator
import com.btspeakerkeeper.tv.control.SettingsLauncher
import com.btspeakerkeeper.tv.data.AppPrefs
import com.btspeakerkeeper.tv.data.AutomationSession

class BtKeeperAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: AppPrefs
    private var currentSession: RuntimeSession? = null
    private var processScheduled = false
    private var monitorScheduled = false
    private var monitorCheckInProgress = false
    private var monitorCheckToken = 0
    private var connectedConfirmationSessionId: Long? = null
    private var confirmationToken = 0L
    private var sessionDeadline: Runnable? = null
    private var deadlineSessionId: Long? = null
    private var lastMonitorReconnectAtMillis = 0L
    private var lastLivePairAcceptedAtMillis: Long? = null
    private var lastUserOwnedSettingsSkipLogAtMillis = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = AppPrefs(this)
        logDebug(null, "service connected; accessibility flags=${serviceInfo.flags}")
        scheduleProcess(500L)
        scheduleConnectionMonitor(2_000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        if (
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            scheduleProcess(700L)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        clearRuntimeWork()
        monitorCheckToken++
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun scheduleProcess(delayMillis: Long) {
        if (processScheduled || !::prefs.isInitialized) {
            return
        }
        processScheduled = true
        handler.postDelayed(
            {
                processScheduled = false
                processActiveWindow()
            },
            delayMillis,
        )
    }

    private fun scheduleConnectionMonitor(delayMillis: Long = CONNECTION_MONITOR_INTERVAL_MILLIS) {
        if (monitorScheduled || !::prefs.isInitialized) {
            return
        }
        monitorScheduled = true
        handler.postDelayed(
            {
                monitorScheduled = false
                runConnectionMonitor()
            },
            delayMillis,
        )
    }

    private fun runConnectionMonitor() {
        val settings = prefs.getSettings()
        val status = prefs.getStatus()
        if (status.automationActive || currentSession != null) {
            // A Settings launch can produce no useful event. The monitor must
            // still adopt the session, arm its deadline, and make progress.
            scheduleProcess(0L)
            scheduleConnectionMonitor()
            return
        }
        if (
            !settings.autoConnectEnabled ||
            SpeakerNameMatcher.normalizeName(settings.targetDeviceName).isEmpty() ||
            monitorCheckInProgress ||
            isPairingAssistVisible() ||
            (settings.skipWhilePlaybackActive && PlaybackDetector.isPlaybackActive(this))
        ) {
            scheduleConnectionMonitor()
            return
        }

        monitorCheckInProgress = true
        val checkToken = ++monitorCheckToken
        handler.postDelayed(
            {
                if (monitorCheckInProgress && checkToken == monitorCheckToken) {
                    monitorCheckInProgress = false
                    scheduleConnectionMonitor()
                }
            },
            MONITOR_CHECK_TIMEOUT_MILLIS,
        )
        BluetoothStateRepository(this).checkTargetState(
            targetName = settings.targetDeviceName,
            targetAddress = settings.targetDeviceAddress,
        ) { result ->
            handler.post {
                if (checkToken != monitorCheckToken) {
                    return@post
                }
                monitorCheckInProgress = false
                val latestSettings = prefs.getSettings()
                if (!latestSettings.autoConnectEnabled || latestSettings.targetDeviceName != settings.targetDeviceName ||
                    latestSettings.targetDeviceAddress != settings.targetDeviceAddress
                ) {
                    scheduleConnectionMonitor()
                    return@post
                }
                val latestStatus = prefs.getStatus()
                val userOwnsSettings = isUserOwnedSettingsWindowVisible()
                val action = LiveMonitorStatePolicy.decide(
                    state = result.state,
                    automationActive = currentSession != null || latestStatus.automationActive,
                    userOwnsSettings = userOwnsSettings,
                    needsConnectedRefresh = latestStatus.lastConnectionState != SpeakerConnectionState.CONNECTED.displayName ||
                        latestStatus.lastError.isNotEmpty() || prefs.getLiveMonitorBackoffUntilMillis() != null,
                )
                when (action) {
                    LiveMonitorAction.UPDATE_CONNECTED -> {
                        prefs.recordSuccess(System.currentTimeMillis())
                        logDebug(null, "monitor confirmed target A2DP Connected; status reconciled without navigation")
                    }
                    LiveMonitorAction.RECONNECT -> triggerLiveMonitorReconnect()
                    LiveMonitorAction.IGNORE -> if (userOwnsSettings) {
                        logLiveMonitorSkipIfNeeded(LiveMonitorForegroundGuard.USER_OWNED_SETTINGS_REASON)
                    }
                }
                scheduleConnectionMonitor()
            }
        }
    }

    private fun triggerLiveMonitorReconnect() {
        val now = System.currentTimeMillis()
        if (now - lastMonitorReconnectAtMillis < MIN_MONITOR_RECONNECT_INTERVAL_MILLIS) {
            return
        }
        lastMonitorReconnectAtMillis = now
        ReconnectCoordinator.requestReconnect(this, TriggerSource.LIVE_MONITOR)
    }

    private fun isPairingAssistVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        val packageName = root.packageName?.toString()?.trim().orEmpty()
        if (packageName != this.packageName) {
            return false
        }
        return windowText(root).contains(PAIRING_ASSIST_TITLE)
    }

    private fun isUserOwnedSettingsWindowVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        return LiveMonitorForegroundGuard.shouldPauseForUserOwnedSettings(
            activePackageName = root.packageName,
            hasActiveAutomationSession = currentSession != null || prefs.getAutomationSession() != null,
        )
    }

    private fun processActiveWindow() {
        val session = currentSession ?: prefs.getAutomationSession()?.toRuntimeSession()
        if (session != null) {
            currentSession = session
            if (session.connectAttempt.isExpired(SystemClock.elapsedRealtime())) {
                finishFailure("Automation session timed out after 2 minutes")
                return
            }
            armSessionDeadline(session)
        }
        val root = rootInActiveWindow
        if (session != null && session.mode == AutomationMode.CONNECT) {
            // A system progress overlay can replace Settings after Connect. Keep checking
            // A2DP without spending navigation retries or requiring the old button tree.
            val unsafeReason = root?.let { AutomationSafetyPolicy.unsafeAutomationWindowReason(windowText(it)) }
            if (unsafeReason != null) {
                finishFailure(unsafeReason)
                return
            }
            when (session.connectAttempt.nextStep(false, SystemClock.elapsedRealtime())) {
                ConnectStep.CHECK_A2DP -> {
                    if (root != null && acceptTargetConnectConfirmation(root, session)) return
                    confirmTargetConnectedWithA2dp(session, TARGET_ROW_CLICK_A2DP_FAILURE_PREFIX)
                    return
                }
                ConnectStep.WAIT_FOR_GESTURE -> return
                else -> Unit
            }
        }
        if (root == null) {
            if (session != null) {
                handleMissingAutomationWindow(session, "No active Settings window")
            }
            return
        }

        if (acceptLivePairPromptIfSafe(root)) {
            return
        }

        if (session == null) return

        if (!isAutomationWindowPackage(root.packageName)) {
            handleMissingAutomationWindow(session, "Waiting for Google TV Settings window", root.packageName)
            return
        }

        val visibleWindowText = windowText(root)
        val windowClassification = AutomationSafetyPolicy.classifyAutomationWindow(
            windowText = visibleWindowText,
            mode = session.mode,
            targetName = session.targetName,
            targetAddress = session.targetAddress,
        )
        logDebug(
            session,
            "window package=${root.packageName} kind=${windowClassification.kind}",
            focusedNodeText(root),
        )
        when (windowClassification.kind) {
            AutomationWindowKind.UNSAFE -> {
                val reason = windowClassification.reason ?: "Unsafe automation window"
                logDebug(session, reason)
                finishFailure(reason)
                return
            }

            AutomationWindowKind.WRONG_DESTINATION -> {
                handleWrongDestination(session, windowClassification.reason ?: "Wrong Settings destination")
                return
            }

            AutomationWindowKind.TARGET_CONTEXT,
            AutomationWindowKind.ALLOWED_NAVIGATION,
            AutomationWindowKind.SETTINGS_HOME,
            AutomationWindowKind.NEUTRAL -> Unit
        }

        if (isTargetConnectedVisible(root, session.targetName, session.targetAddress, session.targetClicked)) {
            confirmTargetConnectedWithA2dp(session)
            return
        }

        if (session.mode == AutomationMode.PAIR_REPAIR) {
            processPairRepair(root, session)
            return
        }

        processConnect(root, session)
    }

    private fun processConnect(root: AccessibilityNodeInfo, session: RuntimeSession) {
        val targetWindow = AutomationSafetyPolicy.hasTargetContext(windowText(root), session.targetName, session.targetAddress)
        val connectNode = if (targetWindow) findConnectNodeInsideTargetContext(root, session) else null
        when (session.connectAttempt.nextStep(connectNode != null, SystemClock.elapsedRealtime())) {
            ConnectStep.EXPIRED -> {
                finishFailure("Automation session timed out after 2 minutes")
                return
            }
            ConnectStep.WAIT_FOR_GESTURE -> return
            ConnectStep.CHECK_A2DP -> {
                confirmTargetConnectedWithA2dp(session, TARGET_ROW_CLICK_A2DP_FAILURE_PREFIX)
                return
            }
            ConnectStep.ACTIVATE -> {
                if (connectNode != null && clickConnectAction(connectNode, session)) {
                    recordConnectClick(session)
                    return
                }
                if (tapVerifiedConnectAction(session)) return
            }
            ConnectStep.SELECT_TARGET -> Unit
        }

        if (session.targetFocused) {
            if (openSavedTargetDetails(root, session)) return
            retryOrFinish(session.copy(targetFocused = false), "Connect skipped: target detail Connect not visible")
            return
        }

        if (targetWindow && !session.targetClicked && !session.targetFocused) {
            val targetNode = findTargetTextNode(root, session)
            if (targetNode != null) {
                val targetRowNode = clickableNodeOrAncestor(targetNode) ?: focusableNodeOrAncestor(targetNode) ?: targetNode
                val targetRowText = subtreeText(targetRowNode).ifBlank { nodeText(targetNode) }
                val targetRowFocused = nodeOrSubtreeIsFocused(targetRowNode) || nodeOrSubtreeIsFocused(targetNode)
                when (
                    TargetRowActivationPolicy.decide(
                        isTargetRowFocused = targetRowFocused,
                        targetRowText = targetRowText,
                        targetName = session.targetName,
                        targetAddress = session.targetAddress,
                    )
                ) {
                    TargetRowActivationAction.FOCUS -> {
                        if (safeFocusNode(targetNode, session, "target speaker row")) {
                            currentSession = session.copy(targetFocused = true)
                            scheduleProcess(POST_TARGET_FOCUS_CHECK_DELAY_MILLIS)
                            return
                        }
                    }

                    TargetRowActivationAction.WAIT_FOR_DETAIL_CONNECT -> {
                        currentSession = session.copy(targetFocused = true)
                        logDebug(session, "target row focused; waiting for detail Connect", targetRowText)
                        scheduleProcess(POST_TARGET_FOCUS_CHECK_DELAY_MILLIS)
                        return
                    }

                    TargetRowActivationAction.CLICK_DIRECT_CONNECT -> {
                        if (clickConnectAction(targetNode, session)) {
                            recordConnectClick(session)
                            return
                        }
                    }

                    TargetRowActivationAction.IGNORE -> Unit
                }
                logDebug(session, "target text node skipped: not clickable; searching Connect action", nodeText(targetNode))
            }
        }

        if (!session.navigationClicked) {
            val navigationNode = findFirstNode(root) { node ->
                AutomationTextMatcher.isDeviceListNavigationTarget(nodeText(node))
            }
            if (navigationNode != null) {
                if (session.navigationScrollCount >= MAX_NAVIGATION_SCROLL_ATTEMPTS) {
                    retryOrFinish(session, "Navigation skipped: device list route not focused")
                    return
                }
                when (safeFocusOrClickNavigationNode(navigationNode, session, "device list navigation")) {
                    NavigationActionResult.CLICKED -> {
                        currentSession = session.copy(
                            navigationClicked = true,
                            navigationScrollCount = 0,
                        )
                        scheduleProcess(1_500L)
                        return
                    }

                    NavigationActionResult.FOCUSED -> {
                        currentSession = session.copy(navigationScrollCount = session.navigationScrollCount + 1)
                        scheduleProcess(700L)
                        return
                    }

                    NavigationActionResult.FAILED -> Unit
                }
            }
        }

        if (canScrollWindow(root, session)) {
            if (session.navigationScrollCount >= MAX_NAVIGATION_SCROLL_ATTEMPTS) {
                retryOrFinish(session, "Navigation skipped: device list route not found")
                return
            }
            if (scrollForward(root)) {
                val nextScrollCount = session.navigationScrollCount + 1
                logDebug(
                    session,
                    "navigation scroll $nextScrollCount/$MAX_NAVIGATION_SCROLL_ATTEMPTS",
                    focusedNodeText(root),
                )
                currentSession = session.copy(navigationScrollCount = nextScrollCount)
                scheduleProcess(1_000L)
                return
            }
        }

        retryOrFinish(session, "Target speaker or Connect button not found")
    }

    private fun openSavedTargetDetails(root: AccessibilityNodeInfo, session: RuntimeSession): Boolean {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        traverse(root) { node -> if (node.isClickable && node.isVisibleToUser) nodes.add(node) }
        val rows = nodes.map { node ->
            val labels = mutableListOf<String>()
            traverse(node) { child ->
                if (child.isVisibleToUser) {
                    child.text?.toString()?.takeIf(String::isNotBlank)?.let(labels::add)
                    child.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(labels::add)
                }
            }
            SavedDeviceRow(labels, uiBounds(node), nodeOrSubtreeIsFocused(node), node.isVisibleToUser, node.isEnabled, node.isClickable)
        }
        val index = session.detailsAttempt.takeRow(rows, session.targetName, session.targetAddress, displayBounds()) ?: return false
        safeClickClickableNode(nodes[index], session, "saved target details navigation")
        // Opening details is navigation, not a Connect activation or proof of success.
        // Keep this attempt object across retries so repeated events cannot click it again.
        scheduleProcess(POST_TARGET_FOCUS_CHECK_DELAY_MILLIS)
        return true
    }

    private fun recordConnectClick(session: RuntimeSession) {
        if (!session.connectAttempt.recordClick(SystemClock.elapsedRealtime())) return
        currentSession = session.copy(targetClicked = true, targetFocused = false)
        scheduleProcess(POST_CONNECT_CLICK_CHECK_DELAY_MILLIS)
    }

    private fun armSessionDeadline(session: RuntimeSession) {
        if (deadlineSessionId == session.id) return
        sessionDeadline?.let(handler::removeCallbacks)
        deadlineSessionId = session.id
        val deadline = Runnable {
            if (currentSession?.id == session.id) finishFailure("Automation session timed out after 2 minutes")
        }
        sessionDeadline = deadline
        handler.postDelayed(deadline, session.connectAttempt.remainingMillis(SystemClock.elapsedRealtime()))
    }

    private fun invalidatePendingConfirmation() {
        confirmationToken++
        connectedConfirmationSessionId = null
    }

    private fun clearRuntimeWork() {
        invalidatePendingConfirmation()
        sessionDeadline?.let(handler::removeCallbacks)
        sessionDeadline = null
        deadlineSessionId = null
        currentSession = null
    }

    private fun processPairRepair(root: AccessibilityNodeInfo, session: RuntimeSession) {
        if (session.targetClicked) {
            if (subtreeContainsPairingNotReadyState(root)) {
                finishPairRepairNeedsUser("Repair pairing canceled. Put the speaker into Bluetooth pairing mode.")
                return
            }
            retryOrFinish(session, "Repair pairing: waiting for pair prompt or connection")
            return
        }

        val targetNode = findFirstNode(root) { node ->
            TargetDeviceMatcher.matchesText(nodeText(node), session.targetName, session.targetAddress)
        }
        val clickableTargetNode = targetNode?.let { clickableNodeOrAncestor(it) }
        if (clickableTargetNode != null && safeClickClickableNode(clickableTargetNode, session, "repair target device")) {
            val selectedText = subtreeText(clickableTargetNode)
            val discoveredAddress = rememberDiscoveredAddress(selectedText, session.targetAddress)
            currentSession = session.copy(
                targetClicked = true,
                targetAddress = discoveredAddress.ifEmpty { session.targetAddress },
                repairDeviceName = repairDeviceNameFromNodeText(selectedText, discoveredAddress.ifEmpty { session.targetAddress }),
            )
            prefs.recordState(SpeakerConnectionState.AUTOMATION_STARTED, "Repair pairing: selected ${session.targetName}")
            scheduleProcess(2_000L)
            return
        }

        if (
            SingleVisibleRepairFallbackPolicy.canUse(
                requestedByManualRepair = session.allowSingleVisibleDeviceRepair,
                targetAddress = session.targetAddress,
            )
        ) {
            val visibleDeviceNode = findSingleVisibleRepairDevice(root)
            if (visibleDeviceNode != null && safeClickClickableNode(visibleDeviceNode.node, session, "single visible repair device")) {
                val discoveredAddress = rememberDiscoveredAddress(visibleDeviceNode.text, session.targetAddress)
                currentSession = session.copy(
                    targetClicked = true,
                    targetAddress = discoveredAddress.ifEmpty { session.targetAddress },
                    repairDeviceName = repairDeviceNameFromNodeText(
                        visibleDeviceNode.text,
                        discoveredAddress.ifEmpty { session.targetAddress },
                    ),
                )
                prefs.recordState(
                    SpeakerConnectionState.AUTOMATION_STARTED,
                    "Repair pairing: selected only visible device ${
                        AutomationSafetyPolicy.shortDiagnosticText(visibleDeviceNode.displayName)
                    }",
                )
                scheduleProcess(2_000L)
                return
            }
        } else if (session.allowSingleVisibleDeviceRepair) {
            logDebug(session, "single visible repair skipped: saved target address requires exact match")
        }

        if (!session.navigationClicked) {
            val pairNavigationNode = findFirstNode(root) { node ->
                AutomationTextMatcher.isRepairPairNavigationTarget(nodeText(node))
            }
            if (pairNavigationNode != null) {
                if (session.navigationScrollCount >= MAX_NAVIGATION_SCROLL_ATTEMPTS) {
                    retryOrFinish(session, "Repair pairing: navigation route not focused")
                    return
                }
                when (safeFocusOrClickNavigationNode(pairNavigationNode, session, "repair pair navigation")) {
                    NavigationActionResult.CLICKED -> {
                        currentSession = session.copy(
                            navigationClicked = true,
                            navigationScrollCount = 0,
                        )
                        prefs.recordState(SpeakerConnectionState.AUTOMATION_STARTED, "Repair pairing: opened pair flow")
                        scheduleProcess(2_000L)
                        return
                    }

                    NavigationActionResult.FOCUSED -> {
                        currentSession = session.copy(navigationScrollCount = session.navigationScrollCount + 1)
                        scheduleProcess(700L)
                        return
                    }

                    NavigationActionResult.FAILED -> Unit
                }
            }
        }

        if (canScrollWindow(root, session)) {
            if (session.navigationScrollCount >= MAX_NAVIGATION_SCROLL_ATTEMPTS) {
                retryOrFinish(session, "Repair pairing: navigation route not found")
                return
            }
            if (scrollForward(root)) {
                val nextScrollCount = session.navigationScrollCount + 1
                logDebug(
                    session,
                    "repair navigation scroll $nextScrollCount/$MAX_NAVIGATION_SCROLL_ATTEMPTS",
                    focusedNodeText(root),
                )
                currentSession = session.copy(navigationScrollCount = nextScrollCount)
                scheduleProcess(1_000L)
                return
            }
        }

        retryOrFinish(session, "Repair pairing: target not visible")
    }

    private fun acceptLivePairPromptIfSafe(root: AccessibilityNodeInfo): Boolean {
        val pairNode = findFirstNode(root) { node ->
            AutomationTextMatcher.isPairAction(nodeText(node))
        } ?: return false

        val settings = prefs.getSettings()
        val session = currentSession ?: prefs.getAutomationSession()?.toRuntimeSession()
        val now = System.currentTimeMillis()
        val windowText = windowText(root)
        val autoConnectOrExplicitRepair = settings.autoConnectEnabled || session?.mode == AutomationMode.PAIR_REPAIR
        if (
            !LivePairPromptGuard.shouldAccept(
                windowText = windowText,
                targetName = settings.targetDeviceName,
                autoConnectEnabled = autoConnectOrExplicitRepair,
                nowMillis = now,
                lastAcceptedAtMillis = lastLivePairAcceptedAtMillis,
                packageName = root.packageName,
                targetAddress = settings.targetDeviceAddress,
                alternateTargetName = session?.repairDeviceName,
            )
        ) {
            return false
        }

        if (!safeClickClickableNode(pairNode, session, "Bluetooth pair prompt")) {
            return false
        }

        lastLivePairAcceptedAtMillis = now
        prefs.recordState(
            SpeakerConnectionState.AUTOMATION_STARTED,
            "Pair prompt accepted for ${settings.targetDeviceName}",
        )
        prefs.clearAutomationSession()
        clearRuntimeWork()
        schedulePostPairReconnectCheck()
        scheduleProcess(2_000L)
        return true
    }

    private fun schedulePostPairReconnectCheck() {
        handler.postDelayed(
            {
                ReconnectCoordinator.requestReconnect(this, TriggerSource.PAIR_ACCEPTED)
            },
            POST_PAIR_RECONNECT_DELAY_MILLIS,
        )
        handler.postDelayed(
            {
                val status = prefs.getStatus()
                if (
                    !status.automationActive &&
                    status.lastConnectionState == SpeakerConnectionState.CONNECTED.displayName
                ) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            },
            POST_PAIR_HOME_DELAY_MILLIS,
        )
    }


    private fun isTargetConnectedVisible(
        root: AccessibilityNodeInfo,
        targetName: String,
        targetAddress: String,
        @Suppress("UNUSED_PARAMETER") targetClicked: Boolean,
    ): Boolean {
        val connectedNode = findFirstNode(root) { node ->
            AutomationTextMatcher.containsConnectedState(nodeText(node))
        }
        return connectedNode != null &&
            hasTargetConnectedContextInAncestors(connectedNode, root, targetName, targetAddress)
    }

    private fun confirmTargetConnectedWithA2dp(
        session: RuntimeSession,
        failurePrefix: String = "Connected UI not confirmed by A2DP",
    ) {
        if (connectedConfirmationSessionId == session.id) {
            return
        }
        val checkToken = ++confirmationToken
        connectedConfirmationSessionId = session.id
        logDebug(session, "$failurePrefix; confirming A2DP state")
        BluetoothStateRepository(this).checkTargetState(session.targetName, session.targetAddress) { result ->
            handler.post {
                if (checkToken != confirmationToken) return@post
                connectedConfirmationSessionId = null
                val activeSession = currentSession
                if (activeSession == null || activeSession.id != session.id) {
                    return@post
                }
                if (activeSession.connectAttempt.isExpired(SystemClock.elapsedRealtime())) {
                    finishFailure("Automation session timed out after 2 minutes")
                    return@post
                }
                if (result.state == SpeakerConnectionState.CONNECTED) {
                    finishSuccess()
                } else if (activeSession.mode == AutomationMode.CONNECT &&
                    activeSession.connectAttempt.phase == ConnectPhase.WAITING_A2DP
                ) {
                    handleConnectProgressResult(
                        session = activeSession,
                        state = result.state,
                        nowMillis = SystemClock.elapsedRealtime(),
                    )
                } else {
                    retryOrFinish(
                        activeSession,
                        "$failurePrefix: ${result.state.displayName}",
                    )
                }
            }
        }
    }

    private fun handleConnectProgressResult(
        session: RuntimeSession,
        state: SpeakerConnectionState,
        nowMillis: Long,
    ) {
        val elapsedMillis = session.connectAttempt.waitElapsed(nowMillis)
        if (session.connectAttempt.canTryGesture(state, nowMillis) && tapVerifiedConnectAction(session)) return
        val action = ConnectProgressRecoveryPolicy.decide(
            retryCount = session.retryCount,
            maxRetries = session.maxRetries,
            elapsedMillis = elapsedMillis,
            state = state,
            backRecoveryCount = session.connectBackRecoveryCount,
            freshRelaunchCount = session.connectFreshRelaunchCount,
        )
        val diagnostic = "state=${state.displayName} elapsed=${elapsedMillis}ms " +
            "backRecovery=${session.connectBackRecoveryCount} " +
            "freshRelaunch=${session.connectFreshRelaunchCount}"
        when (action) {
            ConnectProgressRecoveryAction.WAIT -> {
                logDebug(session, "Connect progress still waiting for A2DP: $diagnostic")
                scheduleProcess(TARGET_ROW_A2DP_RECHECK_INTERVAL_MILLIS)
            }

            ConnectProgressRecoveryAction.BACK_AND_RETRY -> recoverConnectProgress(
                session = session,
                state = state,
                elapsedMillis = elapsedMillis,
                action = action,
                message = "Connect progress timed out; backing out and retrying",
            )

            ConnectProgressRecoveryAction.RELAUNCH_SETTINGS_AND_RETRY -> recoverConnectProgress(
                session = session,
                state = state,
                elapsedMillis = elapsedMillis,
                action = action,
                message = "Connect progress timed out after Back recovery; reopening Settings and retrying",
            )

            ConnectProgressRecoveryAction.FINISH -> finishFailure(
                "Connect progress timed out after bounded recovery: ${state.displayName}",
            )
        }
    }

    private fun recoverConnectProgress(
        session: RuntimeSession,
        state: SpeakerConnectionState,
        elapsedMillis: Long,
        action: ConnectProgressRecoveryAction,
        message: String,
    ) {
        val safeMessage = AutomationSafetyPolicy.redactSensitiveText(message)
        val nextRetryCount = session.retryCount + 1
        if (nextRetryCount >= session.maxRetries) {
            finishFailure("Connect progress timed out: ${state.displayName}")
            return
        }

        invalidatePendingConfirmation()
        session.connectAttempt.beginRecovery()
        val nextSession = session.copy(
            retryCount = nextRetryCount,
            targetClicked = false,
            targetFocused = false,
            navigationClicked = false,
            navigationScrollCount = 0,
            connectBackRecoveryCount = if (action == ConnectProgressRecoveryAction.BACK_AND_RETRY) {
                session.connectBackRecoveryCount + 1
            } else {
                session.connectBackRecoveryCount
            },
            connectFreshRelaunchCount = if (action == ConnectProgressRecoveryAction.RELAUNCH_SETTINGS_AND_RETRY) {
                session.connectFreshRelaunchCount + 1
            } else {
                session.connectFreshRelaunchCount
            },
        )
        prefs.recordState(SpeakerConnectionState.AUTOMATION_STARTED, safeMessage)
        currentSession = nextSession
        logDebug(
            nextSession,
            "$safeMessage state=${state.displayName} elapsed=${elapsedMillis}ms " +
                "backRecovery=${nextSession.connectBackRecoveryCount} " +
                "freshRelaunch=${nextSession.connectFreshRelaunchCount}",
        )

        if (action.reopenFreshSettings) {
            SettingsLauncher.openFreshSettingsHome(this)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        scheduleProcess(1_500L)
    }

    private fun handleMissingAutomationWindow(
        session: RuntimeSession,
        message: String,
        activePackageName: CharSequence? = null,
    ) {
        activePackageName?.let { packageName ->
            logDebug(session, "waiting for automation package=$packageName")
        }
        when (
            ConnectRecoveryForegroundPolicy.decide(
                isAutomationWindowPackage = false,
                retryCount = session.retryCount,
                maxRetries = session.maxRetries,
                targetClicked = session.targetClicked,
                backRecoveryCount = session.connectBackRecoveryCount,
                freshRelaunchCount = session.connectFreshRelaunchCount,
            )
        ) {
            ConnectRecoveryForegroundAction.WAIT -> retryOrFinish(session, message)
            ConnectRecoveryForegroundAction.FINISH -> finishFailure(message)
            ConnectRecoveryForegroundAction.RELAUNCH_SETTINGS -> {
                val safeMessage = AutomationSafetyPolicy.redactSensitiveText(
                    "$message; reopening Settings after Back recovery",
                )
                val nextRetryCount = session.retryCount + 1
                if (nextRetryCount >= session.maxRetries) {
                    finishFailure(safeMessage)
                    return
                }
                invalidatePendingConfirmation()
                session.connectAttempt.beginRecovery()
                val nextSession = session.copy(
                    retryCount = nextRetryCount,
                    targetClicked = false,
                    targetFocused = false,
                    navigationClicked = false,
                    navigationScrollCount = 0,
                    connectFreshRelaunchCount = session.connectFreshRelaunchCount + 1,
                )
                prefs.recordState(SpeakerConnectionState.AUTOMATION_STARTED, safeMessage)
                currentSession = nextSession
                logDebug(
                    nextSession,
                    "$safeMessage backRecovery=${nextSession.connectBackRecoveryCount} " +
                        "freshRelaunch=${nextSession.connectFreshRelaunchCount}",
                )
                SettingsLauncher.openFreshSettingsHome(this)
                scheduleProcess(1_500L)
            }
        }
    }

    private fun retryOrFinish(session: RuntimeSession, message: String) {
        val safeMessage = AutomationSafetyPolicy.redactSensitiveText(message)
        val nextRetryCount = session.retryCount + 1
        logDebug(session, "retry $nextRetryCount/${session.maxRetries}: $safeMessage")
        if (nextRetryCount >= session.maxRetries) {
            if (session.mode == AutomationMode.PAIR_REPAIR) {
                finishPairRepairNeedsUser(safeMessage)
                return
            }
            finishFailure(safeMessage)
            return
        }
        prefs.recordState(SpeakerConnectionState.AUTOMATION_STARTED, safeMessage)
        currentSession = session.copy(retryCount = nextRetryCount)
        scheduleProcess(1_500L)
    }

    private fun handleWrongDestination(session: RuntimeSession, message: String) {
        val safeMessage = AutomationSafetyPolicy.redactSensitiveText(message)
        if (session.wrongDestinationRecoveryAttempted) {
            logDebug(session, "wrong destination recovery failed: $safeMessage")
            finishFailure(safeMessage)
            return
        }

        val recoveryMessage = "$safeMessage; reopening fresh Settings home"
        invalidatePendingConfirmation()
        session.connectAttempt.beginRecovery()
        logDebug(session, recoveryMessage)
        prefs.recordState(SpeakerConnectionState.AUTOMATION_STARTED, recoveryMessage)
        currentSession = session.copy(
            wrongDestinationRecoveryAttempted = true,
            navigationClicked = false,
            targetClicked = false,
            targetFocused = false,
            navigationScrollCount = 0,
        )
        SettingsLauncher.openFreshSettingsHome(this)
        scheduleProcess(1_500L)
    }

    private fun subtreeContainsPairingNotReadyState(root: AccessibilityNodeInfo): Boolean {
        var notReadyVisible = false
        traverse(root) { node ->
            if (AutomationTextMatcher.containsPairingNotReadyState(nodeText(node))) {
                notReadyVisible = true
            }
        }
        return notReadyVisible
    }

    private fun finishSuccess() {
        logDebug(currentSession, "Target A2DP connected; returning Home")
        prefs.recordSuccess(System.currentTimeMillis())
        prefs.clearAutomationSession()
        clearRuntimeWork()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun finishFailure(message: String) {
        val session = currentSession
        val safeMessage = AutomationSafetyPolicy.redactSensitiveText(message)
        if (session?.trigger == TriggerSource.LIVE_MONITOR) {
            val backoffUntil = prefs.recordLiveMonitorBackoff(
                nowMillis = System.currentTimeMillis(),
                cooldownMinutes = prefs.getSettings().cooldownMinutes,
                reason = safeMessage,
            )
            logDebug(
                session,
                "live monitor backoff until=${backoffUntil.backoffUntilMillis} " +
                    "nextProbeAfter=${backoffUntil.nextProbeAfterMillis} " +
                    "failureCount=${backoffUntil.failureCount}: $safeMessage",
            )
        } else {
            prefs.recordFailure(SpeakerConnectionState.ERROR, safeMessage)
        }
        prefs.clearAutomationSession()
        clearRuntimeWork()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun finishPairRepairNeedsUser(message: String) {
        val assistMessage = "$message Press and hold the speaker Bluetooth/Pair button, then wait for retry."
        prefs.recordFailure(SpeakerConnectionState.ERROR, assistMessage)
        prefs.clearAutomationSession()
        clearRuntimeWork()
        val intent = Intent(this, PairingAssistActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(PairingAssistActivity.EXTRA_REASON, assistMessage)
        try {
            startActivity(intent)
        } catch (exception: RuntimeException) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun findFirstNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(root)) {
            return root
        }
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val match = findFirstNode(child, predicate)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun traverse(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        visit(root)
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            traverse(child, visit)
        }
    }

    private fun nodeText(node: AccessibilityNodeInfo): String {
        return listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
        ).joinToString(separator = " ")
    }

    private fun windowText(root: AccessibilityNodeInfo): String {
        val builder = StringBuilder()
        val navigation = StringBuilder()
        val screen = displayBounds()
        traverse(root) { node ->
            if (!node.isVisibleToUser) return@traverse
            val text = nodeText(node)
            if (text.isNotBlank()) {
                if (builder.isNotEmpty()) {
                    builder.append(' ')
                }
                builder.append(text)
                val bounds = uiBounds(node)
                if (bounds.width > 0 && bounds.left >= screen.left && bounds.right <= screen.left + screen.width / 2) {
                    if (navigation.isNotEmpty()) navigation.append(' ')
                    navigation.append(text)
                }
            }
        }
        return SettingsWindowTextPolicy.choose(builder.toString(), navigation.toString())
    }

    private fun findTargetTextNode(root: AccessibilityNodeInfo, session: RuntimeSession): AccessibilityNodeInfo? {
        return findFirstNode(root) { node ->
            node.isVisibleToUser && node.isEnabled && AutomationSafetyPolicy.hasTargetContext(
                contextText = nodeText(node),
                targetName = session.targetName,
                targetAddress = session.targetAddress,
            )
        }
    }

    private fun findConnectNodeInsideTargetContext(
        root: AccessibilityNodeInfo,
        session: RuntimeSession,
    ): AccessibilityNodeInfo? {
        val found = mutableListOf<AccessibilityNodeInfo>()
        traverse(root) { node ->
            if (node.isVisibleToUser && node.isEnabled && connectLabel(node) != null) {
                found.add(node)
            }
        }
        val leafLabels = found.filter { node ->
            (0 until node.childCount).none { index ->
                node.getChild(index)?.let { child ->
                    findFirstNode(child) { it.isVisibleToUser && connectLabel(it) != null } != null
                } == true
            }
        }
        val connectNodes = leafLabels.distinctBy { node ->
            val action = safeConnectContainer(node) ?: node
            "${node.windowId}:${uiBounds(action)}"
        }
        if (connectNodes.isEmpty()) {
            return null
        }

        val signals = connectNodes.map { node ->
            ConnectCandidateSignal(
                hasTargetAncestor = hasTargetContextInAncestors(node, root, session),
                hasClickableAction = safeConnectContainer(node) != null,
            )
        }
        val selectedIndex = ConnectCandidateSelectionPolicy.chooseIndex(
            candidates = signals,
            hasTargetWindowContext = AutomationSafetyPolicy.isTargetConnectContext(
                contextText = windowText(root),
                targetName = session.targetName,
                targetAddress = session.targetAddress,
            ),
            targetActivated = session.targetClicked || session.targetFocused,
        ) ?: return null

        logDebug(
            session,
            "selected Connect candidate ${selectedIndex + 1}/${connectNodes.size}",
            nodeText(connectNodes[selectedIndex]),
        )
        return connectNodes[selectedIndex]
    }

    private fun hasTargetContextInAncestors(
        startNode: AccessibilityNodeInfo,
        root: AccessibilityNodeInfo,
        session: RuntimeSession,
    ): Boolean {
        var node: AccessibilityNodeInfo? = startNode
        repeat(MAX_PARENT_SEARCH_DEPTH) {
            val current = node ?: return false
            if (current == root) {
                return false
            }
            if (
                AutomationSafetyPolicy.hasTargetContext(
                    contextText = subtreeText(current),
                    targetName = session.targetName,
                    targetAddress = session.targetAddress,
                )
            ) {
                return true
            }
            node = current.parent
        }
        return false
    }

    private fun hasTargetConnectedContextInAncestors(
        startNode: AccessibilityNodeInfo,
        root: AccessibilityNodeInfo,
        targetName: String,
        targetAddress: String,
    ): Boolean {
        var node: AccessibilityNodeInfo? = startNode
        repeat(MAX_PARENT_SEARCH_DEPTH) {
            val current = node ?: return false
            if (current == root) {
                return false
            }
            if (
                AutomationSafetyPolicy.isTargetConnectedContext(
                    contextText = subtreeText(current),
                    targetName = targetName,
                    targetAddress = targetAddress,
                )
            ) {
                return true
            }
            node = current.parent
        }
        return false
    }

    private fun safeClickClickableNode(
        startNode: AccessibilityNodeInfo,
        session: RuntimeSession?,
        actionName: String,
    ): Boolean {
        val clickableNode = clickableNodeOrAncestor(startNode)
        if (clickableNode == null) {
            logDebug(session, "click rejected: no clickable node for $actionName", nodeText(startNode))
            return false
        }

        val clicked = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val clickedText = subtreeText(clickableNode).ifBlank { nodeText(clickableNode) }
        if (clicked) {
            logDebug(session, "clicked $actionName", clickedText)
        } else {
            logDebug(session, "click failed: $actionName", clickedText)
        }
        return clicked
    }

    private fun clickConnectAction(node: AccessibilityNodeInfo, session: RuntimeSession): Boolean {
        val clickable = safeConnectContainer(node) ?: return false
        return safeClickClickableNode(clickable, session, "Connect in saved-device target context")
    }

    private fun connectLabel(node: AccessibilityNodeInfo): CharSequence? =
        listOf(node.text, node.contentDescription).firstOrNull(AutomationTextMatcher::isConnectAction)

    private fun acceptTargetConnectConfirmation(root: AccessibilityNodeInfo, session: RuntimeSession): Boolean {
        val labels = mutableListOf<String>()
        val affirmative = mutableListOf<AccessibilityNodeInfo>()
        traverse(root) { node ->
            if (node.isVisibleToUser) {
                node.text?.toString()?.takeIf(String::isNotBlank)?.let(labels::add)
                if (node.isEnabled && ConnectConfirmationPolicy.isAffirmative(node.text)) affirmative.add(node)
            }
        }
        if (!ConnectConfirmationPolicy.allows(root.packageName.toString(), labels, session.targetName,
                session.connectAttempt.phase == ConnectPhase.WAITING_A2DP)) return false
        val label = affirmative.singleOrNull() ?: return false
        val action = clickableNodeOrAncestor(label) ?: return false
        if (!action.isVisibleToUser) return false
        val actionLabels = mutableListOf<String>()
        traverse(action) { node ->
            if (node.isVisibleToUser) node.text?.toString()?.takeIf(String::isNotBlank)?.let(actionLabels::add)
        }
        if (actionLabels.isEmpty() || actionLabels.any { !ConnectConfirmationPolicy.isAffirmative(it) }) return false
        if (!session.connectAttempt.claimConfirmation(SystemClock.elapsedRealtime())) return false
        val clicked = action.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        logDebug(session, "target Connect confirmation click=$clicked; checking A2DP")
        scheduleProcess(POST_CONNECT_CLICK_CHECK_DELAY_MILLIS)
        return true
    }

    private fun safeConnectContainer(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser || !node.isEnabled) return null
        val clickable = clickableNodeOrAncestor(node) ?: return null
        if (!clickable.isVisibleToUser) return null
        val labels = mutableListOf<String>()
        traverse(clickable) { child ->
            if (child.isVisibleToUser) {
                child.text?.toString()?.let(labels::add)
                child.contentDescription?.toString()?.let(labels::add)
            }
        }
        return clickable.takeIf { VisibleConnectAction.isSafeContainer(labels) }
    }

    private fun tapVerifiedConnectAction(session: RuntimeSession): Boolean {
        // Re-read the current window: the old node may belong to a different detail pane now.
        if (currentSession?.id != session.id) return false
        val root = rootInActiveWindow ?: return false
        if (!isAutomationWindowPackage(root.packageName)) return false
        val classification = AutomationSafetyPolicy.classifyAutomationWindow(
            windowText(root), session.mode, session.targetName, session.targetAddress,
        )
        if (classification.kind != AutomationWindowKind.TARGET_CONTEXT) return false
        val node = findConnectNodeInsideTargetContext(root, session) ?: return false
        val point = VisibleConnectAction.tapPoint(
            label = connectLabel(node),
            visible = node.isVisibleToUser,
            enabled = node.isEnabled,
            hasTargetContext = true,
            safeWindow = true,
            actionBounds = uiBounds(node),
            screenBounds = displayBounds(),
        ) ?: return false
        val gestureToken = session.connectAttempt.beginGesture(SystemClock.elapsedRealtime()) ?: return false
        invalidatePendingConfirmation()
        currentSession = session.copy(targetClicked = true, targetFocused = false)
        val path = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, COORDINATE_TAP_DURATION_MILLIS))
            .build()
        lateinit var callbackTimeout: Runnable
        fun complete(completed: Boolean) {
            if (currentSession?.id != session.id) return
            if (!session.connectAttempt.finishGesture(gestureToken, completed, SystemClock.elapsedRealtime())) return
            handler.removeCallbacks(callbackTimeout)
            logDebug(session, if (completed) "Connect gesture completed; checking A2DP" else "Connect gesture cancelled or rejected")
            scheduleProcess(if (completed) POST_CONNECT_CLICK_CHECK_DELAY_MILLIS else 0L)
        }
        callbackTimeout = Runnable { complete(false) }
        handler.postDelayed(callbackTimeout, MONITOR_CHECK_TIMEOUT_MILLIS)
        val dispatched = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) = complete(true)
                override fun onCancelled(gestureDescription: GestureDescription) = complete(false)
            }, handler)
        } catch (exception: RuntimeException) {
            false
        }
        if (!dispatched) complete(false)
        logDebug(session, "Connect gesture dispatch=$dispatched; using verified action bounds")
        return true
    }

    @Suppress("DEPRECATION")
    private fun displayBounds(): UiBounds {
        val windowManager = getSystemService(WindowManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            return UiBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
        val metrics = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return UiBounds(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun safeFocusNode(
        startNode: AccessibilityNodeInfo,
        session: RuntimeSession,
        actionName: String,
    ): Boolean {
        val focusableNode = focusableNodeOrAncestor(startNode) ?: clickableNodeOrAncestor(startNode)
        if (focusableNode == null) {
            logDebug(session, "focus rejected: no focusable node for $actionName", nodeText(startNode))
            return false
        }

        val focusedText = subtreeText(focusableNode).ifBlank { nodeText(focusableNode) }
        if (focusableNode.isFocused || subtreeHasFocusedNode(focusableNode)) {
            logDebug(session, "already focused $actionName", focusedText)
            return true
        }

        val focused = focusableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return if (focused) {
            logDebug(session, "focused $actionName", focusedText)
            true
        } else {
            logDebug(session, "focus failed: $actionName", focusedText)
            false
        }
    }

    private fun safeFocusOrClickNavigationNode(
        startNode: AccessibilityNodeInfo,
        session: RuntimeSession,
        actionName: String,
    ): NavigationActionResult {
        val clickableNode = clickableNodeOrAncestor(startNode)
        if (clickableNode == null) {
            logDebug(session, "navigation rejected: no clickable node for $actionName", nodeText(startNode))
            return NavigationActionResult.FAILED
        }

        val clickedText = subtreeText(clickableNode).ifBlank { nodeText(clickableNode) }
        if (clickableNode.isFocused || startNode.isFocused || subtreeHasFocusedNode(clickableNode)) {
            val clicked = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                logDebug(session, "clicked $actionName", clickedText)
                return NavigationActionResult.CLICKED
            }
            logDebug(session, "navigation click failed: $actionName", clickedText)
            return NavigationActionResult.FAILED
        }

        val focused = startNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            clickableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return if (focused) {
            logDebug(session, "focused $actionName", clickedText)
            NavigationActionResult.FOCUSED
        } else {
            logDebug(session, "navigation focus rejected: $actionName", clickedText)
            NavigationActionResult.FAILED
        }
    }

    private fun subtreeHasFocusedNode(root: AccessibilityNodeInfo): Boolean {
        var focused = false
        traverse(root) { node ->
            if (node.isFocused) {
                focused = true
            }
        }
        return focused
    }

    private fun clickableNodeOrAncestor(startNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = startNode
        repeat(MAX_PARENT_SEARCH_DEPTH) {
            val current = node ?: return null
            if (current.isEnabled && current.isClickable) {
                return current
            }
            node = current.parent
        }
        return null
    }

    private fun findFocusedTargetRowNode(
        root: AccessibilityNodeInfo,
        session: RuntimeSession,
    ): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        traverse(root) { node ->
            if (
                AutomationSafetyPolicy.hasTargetContext(
                    contextText = nodeText(node),
                    targetName = session.targetName,
                    targetAddress = session.targetAddress,
                )
            ) {
                val candidate = focusableNodeOrAncestor(node) ?: clickableNodeOrAncestor(node) ?: node
                val candidateText = subtreeText(candidate).ifBlank { nodeText(candidate) }
                if (
                    nodeOrSubtreeIsFocused(candidate) &&
                    AutomationSafetyPolicy.hasTargetContext(
                        contextText = candidateText,
                        targetName = session.targetName,
                        targetAddress = session.targetAddress,
                    )
                ) {
                    candidates.add(candidate)
                }
            }
        }
        return candidates.distinctBy { node ->
            val bounds = uiBounds(node)
            "${bounds.left}:${bounds.top}:${bounds.right}:${bounds.bottom}:${subtreeText(node)}"
        }.minByOrNull { node ->
            val bounds = uiBounds(node)
            bounds.width * (bounds.bottom - bounds.top).coerceAtLeast(1)
        }
    }

    private fun focusableNodeOrAncestor(startNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = startNode
        repeat(MAX_PARENT_SEARCH_DEPTH) {
            val current = node ?: return null
            if (current.isEnabled && current.isFocusable) {
                return current
            }
            node = current.parent
        }
        return null
    }

    private fun nodeOrSubtreeIsFocused(node: AccessibilityNodeInfo): Boolean {
        return node.isFocused || subtreeHasFocusedNode(node)
    }

    private fun uiBounds(node: AccessibilityNodeInfo): UiBounds {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return UiBounds(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
        )
    }

    private fun scrollForward(root: AccessibilityNodeInfo): Boolean {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        traverse(root) { node -> nodes.add(node) }
        val candidates = nodes.map { node ->
            SettingsScrollCandidate(node.className.toString(), uiBounds(node), node.isVisibleToUser, node.isScrollable)
        }
        val index = SettingsNavigationScrollPolicy.chooseIndex(candidates, displayBounds()) ?: return false
        return nodes[index].performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    private fun focusedNodeText(root: AccessibilityNodeInfo): String {
        val focusedNode = findFirstNode(root) { node -> node.isFocused }
        return focusedNode
            ?.let { subtreeText(it).ifBlank { nodeText(it) } }
            .orEmpty()
    }

    private fun canScrollWindow(root: AccessibilityNodeInfo, session: RuntimeSession): Boolean {
        return AutomationSafetyPolicy.canScrollAutomationWindow(
            windowText = windowText(root),
            mode = session.mode,
            targetName = session.targetName,
            targetAddress = session.targetAddress,
        )
    }

    private fun repairDeviceNameFromNodeText(text: String, targetAddress: String): String {
        val normalizedText = SpeakerNameMatcher.normalizeName(text)
        val normalizedAddress = TargetDeviceMatcher.normalizeAddress(targetAddress)
        if (normalizedAddress.isEmpty()) {
            return normalizedText
        }
        return SpeakerNameMatcher.normalizeName(normalizedText.substringBefore(normalizedAddress))
    }

    private fun rememberDiscoveredAddress(selectedText: String, existingAddress: String): String {
        if (existingAddress.isNotBlank()) {
            return existingAddress
        }
        val discoveredAddress = TargetDeviceMatcher.firstAddressIn(selectedText)
        if (discoveredAddress.isNotBlank()) {
            prefs.updateSettings(prefs.getSettings().copy(targetDeviceAddress = discoveredAddress))
        }
        return discoveredAddress
    }

    private fun findSingleVisibleRepairDevice(root: AccessibilityNodeInfo): RepairDeviceCandidate? {
        val candidates = mutableListOf<RepairDeviceCandidate>()
        traverse(root) { node ->
            if (!node.isEnabled || !node.isClickable) {
                return@traverse
            }
            val text = subtreeText(node)
            if (AutomationSafetyPolicy.isUnsafeSingleVisibleRepairCandidate(text)) {
                logDebug(null, "single visible repair candidate rejected", text)
                return@traverse
            }
            candidates.add(
                RepairDeviceCandidate(
                    node = node,
                    text = text,
                    displayName = SpeakerNameMatcher.normalizeName(text).ifBlank { "unknown" },
                ),
            )
        }
        return candidates.firstOrNull { candidate -> candidate.node.isFocused } ?: candidates.singleOrNull()
    }

    private fun subtreeText(root: AccessibilityNodeInfo): String {
        val builder = StringBuilder()
        traverse(root) { node ->
            val text = nodeText(node)
            if (text.isNotBlank()) {
                if (builder.isNotEmpty()) {
                    builder.append(' ')
                }
                builder.append(text)
            }
        }
        return builder.toString()
    }

    private fun isAutomationWindowPackage(packageName: CharSequence?): Boolean {
        val normalizedPackage = packageName?.toString()?.trim().orEmpty()
        return normalizedPackage in automationWindowPackages
    }

    private fun logDebug(
        session: RuntimeSession?,
        message: String,
        nodeText: String? = null,
    ) {
        if (!isDebuggableBuild()) {
            return
        }

        val safeMessage = AutomationSafetyPolicy.redactSensitiveText(message)
        val sessionText = if (session == null) {
            "session=none"
        } else {
            "session=${session.id} trigger=${session.trigger} mode=${session.mode} retry=${session.retryCount}/${session.maxRetries}"
        }
        val nodeSuffix = nodeText
            ?.let { AutomationSafetyPolicy.shortDiagnosticText(it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { " node=\"$it\"" }
            .orEmpty()
        Log.d(TAG, "$sessionText $safeMessage$nodeSuffix")
    }

    private fun logLiveMonitorSkipIfNeeded(message: String) {
        if (!isDebuggableBuild()) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastUserOwnedSettingsSkipLogAtMillis < USER_OWNED_SETTINGS_SKIP_LOG_INTERVAL_MILLIS) {
            return
        }

        lastUserOwnedSettingsSkipLogAtMillis = now
        Log.d(TAG, "session=none trigger=LIVE_MONITOR $message")
    }

    private fun isDebuggableBuild(): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun AutomationSession.toRuntimeSession(): RuntimeSession {
        return RuntimeSession(
            id = id,
            targetName = targetName,
            targetAddress = targetAddress,
            maxRetries = maxRetries,
            trigger = trigger,
            mode = mode,
            allowSingleVisibleDeviceRepair = allowSingleVisibleDeviceRepair,
            connectAttempt = ConnectAttempt(
                SystemClock.elapsedRealtime() - (System.currentTimeMillis() - id).coerceAtLeast(0L),
            ),
        )
    }

    private data class RepairDeviceCandidate(
        val node: AccessibilityNodeInfo,
        val text: String,
        val displayName: String,
    )

    private enum class NavigationActionResult {
        CLICKED,
        FOCUSED,
        FAILED,
    }

    private data class RuntimeSession(
        val id: Long,
        val targetName: String,
        val targetAddress: String,
        val maxRetries: Int,
        val trigger: TriggerSource,
        val mode: AutomationMode,
        val allowSingleVisibleDeviceRepair: Boolean,
        val connectAttempt: ConnectAttempt,
        val detailsAttempt: SavedDeviceDetailsAttempt = SavedDeviceDetailsAttempt(),
        val retryCount: Int = 0,
        val targetClicked: Boolean = false,
        val targetFocused: Boolean = false,
        val navigationClicked: Boolean = false,
        val navigationScrollCount: Int = 0,
        val wrongDestinationRecoveryAttempted: Boolean = false,
        val repairDeviceName: String = "",
        val connectBackRecoveryCount: Int = 0,
        val connectFreshRelaunchCount: Int = 0,
    )

    companion object {
        private const val TAG = "BtKeeperAutomation"
        private const val MAX_PARENT_SEARCH_DEPTH = 8
        private const val CONNECTION_MONITOR_INTERVAL_MILLIS = 5_000L
        private const val MONITOR_CHECK_TIMEOUT_MILLIS = 4_000L
        private const val MIN_MONITOR_RECONNECT_INTERVAL_MILLIS = 10_000L
        private const val MAX_NAVIGATION_SCROLL_ATTEMPTS = 8
        private const val POST_TARGET_FOCUS_CHECK_DELAY_MILLIS = 1_000L
        private const val POST_CONNECT_CLICK_CHECK_DELAY_MILLIS = 2_000L
        private const val COORDINATE_TAP_DURATION_MILLIS = 80L
        private const val TARGET_ROW_A2DP_RECHECK_INTERVAL_MILLIS = 2_000L
        private const val TARGET_ROW_CLICK_A2DP_FAILURE_PREFIX = "Target row click not confirmed by A2DP"
        private const val POST_PAIR_RECONNECT_DELAY_MILLIS = 5_000L
        private const val POST_PAIR_HOME_DELAY_MILLIS = 7_000L
        private const val USER_OWNED_SETTINGS_SKIP_LOG_INTERVAL_MILLIS = 30_000L
        private const val PAIRING_ASSIST_TITLE = "Speaker pairing needed"
        private val automationWindowPackages = setOf(
            "android",
            "com.android.bluetooth",
            "com.android.settings",
            "com.android.systemui",
            "com.android.tv.settings",
            "com.google.android.tv.settings",
        )
    }
}
