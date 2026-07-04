package com.btspeakerkeeper.tv.core

object LiveMonitorForegroundGuard {
    fun shouldPauseForUserOwnedSettings(
        activePackageName: CharSequence?,
        hasActiveAutomationSession: Boolean,
    ): Boolean {
        if (hasActiveAutomationSession) {
            return false
        }

        return activePackageName?.toString()?.trim().orEmpty() in userOwnedSettingsPackages
    }

    const val USER_OWNED_SETTINGS_REASON = "Settings foreground; live monitor paused for manual control"

    private val userOwnedSettingsPackages = setOf(
        "com.android.settings",
        "com.android.tv.settings",
        "com.google.android.tv.settings",
    )
}
