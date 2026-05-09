package com.btspeakerkeeper.tv.core

object LivePairPromptGuard {
    private const val ACCEPT_COOLDOWN_MILLIS = 60_000L
    private val allowedPromptPackages = setOf(
        "android",
        "com.android.bluetooth",
        "com.android.settings",
        "com.android.systemui",
        "com.android.tv.settings",
        "com.google.android.tv.settings",
    )

    fun shouldAccept(
        windowText: CharSequence?,
        targetName: CharSequence?,
        autoConnectEnabled: Boolean,
        nowMillis: Long,
        lastAcceptedAtMillis: Long?,
        packageName: CharSequence? = null,
        targetAddress: CharSequence? = null,
        alternateTargetName: CharSequence? = null,
    ): Boolean {
        if (!autoConnectEnabled) {
            return false
        }
        if (!isAllowedPromptPackage(packageName)) {
            return false
        }
        if (!matchesTarget(windowText, targetName, targetAddress, alternateTargetName)) {
            return false
        }
        if (AutomationTextMatcher.containsPairingPromptExclusion(windowText)) {
            return false
        }
        val lastAcceptedAt = lastAcceptedAtMillis ?: return true
        return nowMillis - lastAcceptedAt >= ACCEPT_COOLDOWN_MILLIS
    }

    private fun isAllowedPromptPackage(packageName: CharSequence?): Boolean {
        val normalizedPackage = packageName?.toString()?.trim()
        if (normalizedPackage.isNullOrEmpty()) {
            return true
        }
        return normalizedPackage in allowedPromptPackages
    }

    private fun matchesTarget(
        windowText: CharSequence?,
        targetName: CharSequence?,
        targetAddress: CharSequence?,
        alternateTargetName: CharSequence?,
    ): Boolean {
        return SpeakerNameMatcher.containsConfiguredName(windowText, targetName) ||
            SpeakerNameMatcher.containsConfiguredName(windowText, alternateTargetName) ||
            TargetDeviceMatcher.containsConfiguredAddress(windowText, targetAddress)
    }
}
