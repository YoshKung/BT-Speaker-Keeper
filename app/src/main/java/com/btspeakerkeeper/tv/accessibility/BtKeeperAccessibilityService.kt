package com.btspeakerkeeper.tv.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.btspeakerkeeper.tv.PairingAssistActivity
import com.btspeakerkeeper.tv.bluetooth.BluetoothStateRepository
import com.btspeakerkeeper.tv.core.AutomationTextMatcher
import com.btspeakerkeeper.tv.core.AutomationMode
import com.btspeakerkeeper.tv.core.LivePairPromptGuard
import com.btspeakerkeeper.tv.core.SpeakerConnectionState
import com.btspeakerkeeper.tv.core.SpeakerNameMatcher
import com.btspeakerkeeper.tv.core.TargetDeviceMatcher
import com.btspeakerkeeper.tv.core.TriggerSource
import com.btspeakerkeeper.tv.control.PlaybackDetector
import com.btspeakerkeeper.tv.control.ReconnectCoordinator
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
    private var lastMonitorReconnectAtMillis = 0L
    private var lastLivePairAcceptedAtMillis: Long? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = AppPrefs(this)
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
        if (
            !settings.autoConnectEnabled ||
            SpeakerNameMatcher.normalizeName(settings.targetDeviceName).isEmpty() ||
            status.automationActive ||
            currentSession != null ||
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
                if (shouldReconnectFromMonitor(result.state)) {
                    triggerLiveMonitorReconnect()
                }
                scheduleConnectionMonitor()
            }
        }
    }

    private fun shouldReconnectFromMonitor(state: SpeakerConnectionState): Boolean {
        return state == SpeakerConnectionState.DISCONNECTED ||
            state == SpeakerConnectionState.TARGET_NOT_PAIRED
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

    private fun processActiveWindow() {
        val root = rootInActiveWindow
        if (root == null) {
            val session = currentSession ?: prefs.getAutomationSession()?.toRuntimeSession()
            if (session != null) {
                retryOrFinish(session, "No active Settings window")
            }
            return
        }

        if (acceptLivePairPromptIfSafe(root)) {
            return
        }

        val session = currentSession ?: prefs.getAutomationSession()?.toRuntimeSession() ?: return
        currentSession = session

        if (!isAutomationWindowPackage(root.packageName)) {
            retryOrFinish(session, "Waiting for Google TV Settings window")
            return
        }

        if (isTargetConnectedVisible(root, session.targetName, session.targetAddress, session.targetClicked)) {
            finishSuccess()
            return
        }

        if (session.mode == AutomationMode.PAIR_REPAIR) {
            processPairRepair(root, session)
            return
        }

        processConnect(root, session)
    }

    private fun processConnect(root: AccessibilityNodeInfo, session: RuntimeSession) {
        if (!session.targetClicked) {
            val targetNode = findFirstNode(root) { node ->
                TargetDeviceMatcher.matchesText(nodeText(node), session.targetName, session.targetAddress)
            }
            if (targetNode != null && clickNodeOrAncestor(targetNode)) {
                currentSession = session.copy(targetClicked = true)
                scheduleProcess(1_500L)
                return
            }
        }

        val connectNode = findFirstNode(root) { node ->
            AutomationTextMatcher.isConnectAction(nodeText(node))
        }
        if (connectNode != null && clickNodeOrAncestor(connectNode)) {
            currentSession = session.copy(targetClicked = true)
            scheduleProcess(2_000L)
            return
        }

        if (!session.navigationClicked) {
            val navigationNode = findFirstNode(root) { node ->
                AutomationTextMatcher.isDeviceListNavigation(nodeText(node))
            }
            if (navigationNode != null && clickNodeOrAncestor(navigationNode)) {
                currentSession = session.copy(navigationClicked = true)
                scheduleProcess(1_500L)
                return
            }
        }

        if (scrollForward(root)) {
            retryOrFinish(session, "Scrolled while searching for target speaker or Connect button")
            return
        }

        retryOrFinish(session, "Target speaker or Connect button not found")
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
        if (clickableTargetNode != null && clickNode(clickableTargetNode)) {
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

        if (session.allowSingleVisibleDeviceRepair) {
            val visibleDeviceNode = findSingleVisibleRepairDevice(root)
            if (visibleDeviceNode != null && clickNode(visibleDeviceNode.node)) {
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
                    "Repair pairing: selected only visible device ${visibleDeviceNode.displayName}",
                )
                scheduleProcess(2_000L)
                return
            }
        }

        if (!session.navigationClicked) {
            val pairNavigationNode = findFirstNode(root) { node ->
                AutomationTextMatcher.isRepairPairNavigation(nodeText(node))
            }
            if (pairNavigationNode != null && clickNodeOrAncestor(pairNavigationNode)) {
                currentSession = session.copy(navigationClicked = true)
                prefs.recordState(SpeakerConnectionState.AUTOMATION_STARTED, "Repair pairing: opened pair flow")
                scheduleProcess(2_000L)
                return
            }
        }

        if (scrollForward(root)) {
            retryOrFinish(session, "Repair pairing: scrolled while searching for ${session.targetName}")
            return
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

        if (!clickNodeOrAncestor(pairNode)) {
            return false
        }

        lastLivePairAcceptedAtMillis = now
        prefs.recordState(
            SpeakerConnectionState.AUTOMATION_STARTED,
            "Pair prompt accepted for ${settings.targetDeviceName}",
        )
        prefs.clearAutomationSession()
        currentSession = null
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
        targetClicked: Boolean,
    ): Boolean {
        val targetNode = findFirstNode(root) { node ->
            TargetDeviceMatcher.matchesText(nodeText(node), targetName, targetAddress)
        }
        if (targetNode != null) {
            var node: AccessibilityNodeInfo? = targetNode
            repeat(MAX_PARENT_SEARCH_DEPTH) {
                val current = node ?: return@repeat
                if (subtreeContainsConnectedState(current)) {
                    return true
                }
                node = current.parent
            }
        }
        return targetClicked && subtreeContainsConnectedState(root)
    }

    private fun retryOrFinish(session: RuntimeSession, message: String) {
        val nextRetryCount = session.retryCount + 1
        if (nextRetryCount >= session.maxRetries) {
            if (session.mode == AutomationMode.PAIR_REPAIR) {
                finishPairRepairNeedsUser(message)
                return
            }
            finishFailure(message)
            return
        }
        currentSession = session.copy(retryCount = nextRetryCount)
        scheduleProcess(1_500L)
    }

    private fun subtreeContainsConnectedState(root: AccessibilityNodeInfo): Boolean {
        var connectedVisible = false
        traverse(root) { node ->
            if (AutomationTextMatcher.containsConnectedState(nodeText(node))) {
                connectedVisible = true
            }
        }
        return connectedVisible
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
        prefs.recordSuccess(System.currentTimeMillis())
        prefs.clearAutomationSession()
        currentSession = null
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun finishFailure(message: String) {
        prefs.recordFailure(SpeakerConnectionState.ERROR, message)
        prefs.clearAutomationSession()
        currentSession = null
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun finishPairRepairNeedsUser(message: String) {
        val assistMessage = "$message Press and hold the speaker Bluetooth/Pair button, then wait for retry."
        prefs.recordFailure(SpeakerConnectionState.ERROR, assistMessage)
        prefs.clearAutomationSession()
        currentSession = null
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

    private fun clickNodeOrAncestor(startNode: AccessibilityNodeInfo): Boolean {
        val clickableNode = clickableNodeOrAncestor(startNode) ?: startNode
        return clickNode(clickableNode)
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK) || clickNodeCenter(node)
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

    private fun clickNodeCenter(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            return false
        }
        val path = Path().apply {
            moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun scrollForward(root: AccessibilityNodeInfo): Boolean {
        val scrollable = findFirstNode(root) { node -> node.isScrollable }
        return scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
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
            if (text.isBlank() || AutomationTextMatcher.containsPairingPromptExclusion(text)) {
                return@traverse
            }
            if (AutomationTextMatcher.isRepairPairNavigation(text) || AutomationTextMatcher.isPairAction(text)) {
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

    private fun AutomationSession.toRuntimeSession(): RuntimeSession {
        return RuntimeSession(
            id = id,
            targetName = targetName,
            targetAddress = targetAddress,
            maxRetries = maxRetries,
            mode = mode,
            allowSingleVisibleDeviceRepair = allowSingleVisibleDeviceRepair,
        )
    }

    private data class RepairDeviceCandidate(
        val node: AccessibilityNodeInfo,
        val text: String,
        val displayName: String,
    )

    private data class RuntimeSession(
        val id: Long,
        val targetName: String,
        val targetAddress: String,
        val maxRetries: Int,
        val mode: AutomationMode,
        val allowSingleVisibleDeviceRepair: Boolean,
        val retryCount: Int = 0,
        val targetClicked: Boolean = false,
        val navigationClicked: Boolean = false,
        val repairDeviceName: String = "",
    )

    companion object {
        private const val MAX_PARENT_SEARCH_DEPTH = 8
        private const val CONNECTION_MONITOR_INTERVAL_MILLIS = 5_000L
        private const val MONITOR_CHECK_TIMEOUT_MILLIS = 4_000L
        private const val MIN_MONITOR_RECONNECT_INTERVAL_MILLIS = 10_000L
        private const val POST_PAIR_RECONNECT_DELAY_MILLIS = 5_000L
        private const val POST_PAIR_HOME_DELAY_MILLIS = 7_000L
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
