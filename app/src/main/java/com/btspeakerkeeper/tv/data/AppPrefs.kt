package com.btspeakerkeeper.tv.data

import android.content.Context
import android.content.SharedPreferences
import com.btspeakerkeeper.tv.core.AutomationMode
import com.btspeakerkeeper.tv.core.ReconnectSettings
import com.btspeakerkeeper.tv.core.SpeakerConnectionState
import com.btspeakerkeeper.tv.core.TriggerSource

class AppPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun sharedPreferences(): SharedPreferences = prefs

    fun getSettings(): ReconnectSettings {
        return ReconnectSettings(
            targetDeviceName = prefs.getString(KEY_TARGET_NAME, "").orEmpty(),
            targetDeviceAddress = prefs.getString(KEY_TARGET_ADDRESS, "").orEmpty(),
            autoConnectEnabled = prefs.getBoolean(KEY_AUTO_CONNECT, false),
            skipWhilePlaybackActive = prefs.getBoolean(KEY_SKIP_PLAYBACK, true),
            maxRetryCount = prefs.getInt(KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES),
            cooldownMinutes = prefs.getInt(KEY_COOLDOWN_MINUTES, DEFAULT_COOLDOWN_MINUTES),
            periodicRetryEnabled = prefs.getBoolean(KEY_PERIODIC_ENABLED, false),
            periodicRetryMinutes = prefs.getInt(KEY_PERIODIC_MINUTES, DEFAULT_PERIODIC_MINUTES),
        )
    }

    fun updateSettings(settings: ReconnectSettings) {
        prefs.edit()
            .putString(KEY_TARGET_NAME, settings.targetDeviceName)
            .putString(KEY_TARGET_ADDRESS, settings.targetDeviceAddress)
            .putBoolean(KEY_AUTO_CONNECT, settings.autoConnectEnabled)
            .putBoolean(KEY_SKIP_PLAYBACK, settings.skipWhilePlaybackActive)
            .putInt(KEY_MAX_RETRIES, settings.maxRetryCount.coerceIn(1, 10))
            .putInt(KEY_COOLDOWN_MINUTES, settings.cooldownMinutes.coerceIn(0, 240))
            .putBoolean(KEY_PERIODIC_ENABLED, settings.periodicRetryEnabled)
            .putInt(KEY_PERIODIC_MINUTES, settings.periodicRetryMinutes.coerceIn(15, 24 * 60))
            .apply()
    }

    fun getStatus(): AppStatus {
        val successAt = prefs.getLong(KEY_LAST_SUCCESS_AT, 0L).takeIf { it > 0L }
        val attemptAt = prefs.getLong(KEY_LAST_ATTEMPT_AT, 0L).takeIf { it > 0L }
        return AppStatus(
            lastTrigger = prefs.getString(KEY_LAST_TRIGGER, "Never").orEmpty(),
            lastConnectionState = prefs.getString(KEY_LAST_STATE, SpeakerConnectionState.UNKNOWN.displayName).orEmpty(),
            lastError = prefs.getString(KEY_LAST_ERROR, "").orEmpty(),
            lastSuccessfulConnectAtMillis = successAt,
            lastAttemptAtMillis = attemptAt,
            automationActive = prefs.getBoolean(KEY_AUTOMATION_ACTIVE, false),
        )
    }

    fun recordSkipped(trigger: TriggerSource, reason: String) {
        prefs.edit()
            .putString(KEY_LAST_TRIGGER, trigger.displayName)
            .putString(KEY_LAST_STATE, SpeakerConnectionState.SKIPPED.displayName)
            .putString(KEY_LAST_ERROR, reason)
            .apply()
    }

    fun recordAttemptStarted(trigger: TriggerSource, nowMillis: Long) {
        prefs.edit()
            .putString(KEY_LAST_TRIGGER, trigger.displayName)
            .putString(KEY_LAST_STATE, SpeakerConnectionState.UNKNOWN.displayName)
            .putString(KEY_LAST_ERROR, "")
            .putLong(KEY_LAST_ATTEMPT_AT, nowMillis)
            .apply()
    }

    fun recordState(state: SpeakerConnectionState, error: String? = null) {
        prefs.edit()
            .putString(KEY_LAST_STATE, state.displayName)
            .putString(KEY_LAST_ERROR, error.orEmpty())
            .apply()
    }

    fun recordSuccess(nowMillis: Long) {
        prefs.edit()
            .putString(KEY_LAST_STATE, SpeakerConnectionState.CONNECTED.displayName)
            .putString(KEY_LAST_ERROR, "")
            .putLong(KEY_LAST_SUCCESS_AT, nowMillis)
            .putBoolean(KEY_AUTOMATION_ACTIVE, false)
            .apply()
    }

    fun recordFailure(state: SpeakerConnectionState, message: String) {
        prefs.edit()
            .putString(KEY_LAST_STATE, state.displayName)
            .putString(KEY_LAST_ERROR, message)
            .putBoolean(KEY_AUTOMATION_ACTIVE, false)
            .apply()
    }

    fun requestAutomation(
        targetName: String,
        targetAddress: String,
        maxRetries: Int,
        mode: AutomationMode = AutomationMode.CONNECT,
        allowSingleVisibleDeviceRepair: Boolean = false,
    ): AutomationSession {
        val session = AutomationSession(
            id = System.currentTimeMillis(),
            targetName = targetName,
            targetAddress = targetAddress,
            maxRetries = maxRetries.coerceIn(1, 10),
            mode = mode,
            allowSingleVisibleDeviceRepair = allowSingleVisibleDeviceRepair,
        )
        prefs.edit()
            .putBoolean(KEY_AUTOMATION_ACTIVE, true)
            .putLong(KEY_AUTOMATION_ID, session.id)
            .putString(KEY_AUTOMATION_TARGET, session.targetName)
            .putString(KEY_AUTOMATION_TARGET_ADDRESS, session.targetAddress)
            .putInt(KEY_AUTOMATION_MAX_RETRIES, session.maxRetries)
            .putString(KEY_AUTOMATION_MODE, session.mode.name)
            .putBoolean(KEY_AUTOMATION_SINGLE_DEVICE_REPAIR, session.allowSingleVisibleDeviceRepair)
            .putString(KEY_LAST_STATE, SpeakerConnectionState.AUTOMATION_STARTED.displayName)
            .putString(KEY_LAST_ERROR, "")
            .apply()
        return session
    }

    fun getAutomationSession(): AutomationSession? {
        if (!prefs.getBoolean(KEY_AUTOMATION_ACTIVE, false)) {
            return null
        }
        val target = prefs.getString(KEY_AUTOMATION_TARGET, "").orEmpty()
        if (target.isBlank()) {
            return null
        }
        return AutomationSession(
            id = prefs.getLong(KEY_AUTOMATION_ID, 0L),
            targetName = target,
            targetAddress = prefs.getString(KEY_AUTOMATION_TARGET_ADDRESS, "").orEmpty(),
            maxRetries = prefs.getInt(KEY_AUTOMATION_MAX_RETRIES, DEFAULT_MAX_RETRIES).coerceIn(1, 10),
            mode = automationModeFromPrefs(),
            allowSingleVisibleDeviceRepair = prefs.getBoolean(KEY_AUTOMATION_SINGLE_DEVICE_REPAIR, false),
        )
    }

    fun clearAutomationSession() {
        prefs.edit()
            .putBoolean(KEY_AUTOMATION_ACTIVE, false)
            .remove(KEY_AUTOMATION_ID)
            .remove(KEY_AUTOMATION_TARGET)
            .remove(KEY_AUTOMATION_TARGET_ADDRESS)
            .remove(KEY_AUTOMATION_MAX_RETRIES)
            .remove(KEY_AUTOMATION_MODE)
            .remove(KEY_AUTOMATION_SINGLE_DEVICE_REPAIR)
            .apply()
    }

    private fun automationModeFromPrefs(): AutomationMode {
        val modeName = prefs.getString(KEY_AUTOMATION_MODE, AutomationMode.CONNECT.name).orEmpty()
        return runCatching { AutomationMode.valueOf(modeName) }.getOrDefault(AutomationMode.CONNECT)
    }

    companion object {
        private const val PREFS_NAME = "bt_speaker_keeper"

        private const val KEY_TARGET_NAME = "target_name"
        private const val KEY_TARGET_ADDRESS = "target_address"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_SKIP_PLAYBACK = "skip_playback"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val KEY_COOLDOWN_MINUTES = "cooldown_minutes"
        private const val KEY_PERIODIC_ENABLED = "periodic_enabled"
        private const val KEY_PERIODIC_MINUTES = "periodic_minutes"

        private const val KEY_LAST_TRIGGER = "last_trigger"
        private const val KEY_LAST_STATE = "last_state"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_SUCCESS_AT = "last_success_at"
        private const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"

        private const val KEY_AUTOMATION_ACTIVE = "automation_active"
        private const val KEY_AUTOMATION_ID = "automation_id"
        private const val KEY_AUTOMATION_TARGET = "automation_target"
        private const val KEY_AUTOMATION_TARGET_ADDRESS = "automation_target_address"
        private const val KEY_AUTOMATION_MAX_RETRIES = "automation_max_retries"
        private const val KEY_AUTOMATION_MODE = "automation_mode"
        private const val KEY_AUTOMATION_SINGLE_DEVICE_REPAIR = "automation_single_device_repair"

        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_COOLDOWN_MINUTES = 10
        const val DEFAULT_PERIODIC_MINUTES = 60
    }
}

data class AppStatus(
    val lastTrigger: String,
    val lastConnectionState: String,
    val lastError: String,
    val lastSuccessfulConnectAtMillis: Long?,
    val lastAttemptAtMillis: Long?,
    val automationActive: Boolean,
)

data class AutomationSession(
    val id: Long,
    val targetName: String,
    val targetAddress: String,
    val maxRetries: Int,
    val mode: AutomationMode = AutomationMode.CONNECT,
    val allowSingleVisibleDeviceRepair: Boolean = false,
)
