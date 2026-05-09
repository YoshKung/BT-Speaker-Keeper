package com.btspeakerkeeper.tv.core

object AutoConnectDeviceToggle {
    fun toggle(
        settings: ReconnectSettings,
        selectedDeviceName: String,
        selectedDeviceAddress: String = "",
    ): ReconnectSettings {
        val normalizedSelected = SpeakerNameMatcher.normalizeName(selectedDeviceName)
        val normalizedAddress = TargetDeviceMatcher.normalizeAddress(selectedDeviceAddress)
        val isCurrentTarget = SpeakerNameMatcher.matchesExactConfiguredName(
            candidate = settings.targetDeviceName,
            configured = normalizedSelected,
        ) || TargetDeviceMatcher.addressesEqual(settings.targetDeviceAddress, normalizedAddress)

        return settings.copy(
            targetDeviceName = normalizedSelected,
            targetDeviceAddress = normalizedAddress,
            autoConnectEnabled = !(isCurrentTarget && settings.autoConnectEnabled),
        )
    }
}
