package com.btspeakerkeeper.tv.core

enum class TriggerSource(
    val displayName: String,
    val isManual: Boolean,
    val requiresAutoConnect: Boolean = !isManual,
    val bypassCooldown: Boolean = isManual,
    val respectsPlaybackSafety: Boolean = !isManual,
) {
    APP_OPEN("App opened", false),
    MANUAL("Connect Now", true),
    REPAIR_PAIR("Repair Pair Now", true),
    PAIR_ACCEPTED("Pair accepted", true),
    LIVE_MONITOR(
        displayName = "Live monitor",
        isManual = false,
        requiresAutoConnect = true,
        bypassCooldown = true,
        respectsPlaybackSafety = true,
    ),
    BOOT("Boot completed", false),
    SCREEN_ON("Screen on", false),
    PERIODIC("Periodic retry", false),
}

enum class AutomationMode {
    CONNECT,
    PAIR_REPAIR,
}

data class ReconnectSettings(
    val targetDeviceName: String,
    val targetDeviceAddress: String = "",
    val autoConnectEnabled: Boolean,
    val skipWhilePlaybackActive: Boolean,
    val maxRetryCount: Int,
    val cooldownMinutes: Int,
    val periodicRetryEnabled: Boolean,
    val periodicRetryMinutes: Int,
)

data class ReconnectRuntimeState(
    val inProgress: Boolean,
    val lastAttemptAtMillis: Long?,
)

sealed class ReconnectDecision {
    data object Proceed : ReconnectDecision()
    data class Skip(val reason: String) : ReconnectDecision()
}

enum class SpeakerConnectionState(val displayName: String) {
    UNKNOWN("Unknown"),
    MISSING_PERMISSION("Missing Bluetooth permission"),
    BLUETOOTH_UNAVAILABLE("Bluetooth unavailable"),
    PROFILE_UNAVAILABLE("A2DP profile unavailable"),
    TARGET_NOT_CONFIGURED("Target not configured"),
    TARGET_NOT_PAIRED("Target not paired"),
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    CONNECTED("Connected"),
    DISCONNECTING("Disconnecting"),
    ERROR("Error"),
    SKIPPED("Skipped"),
    AUTOMATION_STARTED("Automation started"),
}
