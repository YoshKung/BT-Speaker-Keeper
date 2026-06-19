package com.btspeakerkeeper.tv.core

object AutomationSafetyPolicy {
    private val bluetoothAddressPattern = Regex("(?i)([0-9A-F]{2}:){5}[0-9A-F]{2}")
    private val pairingCodePattern = Regex(
        "(?i)(Pairing code|Wi-Fi pairing code|Wi\u2011Fi pairing code|รหัสการจับคู่ Wi-Fi|รหัสการจับคู่ Wi\u2011Fi)\\s*[:：]?\\s*\\d{4,8}",
    )
    private val endpointPattern = Regex("\\b\\d{1,3}(?:\\.\\d{1,3}){3}:\\d+\\b")

    fun unsafeAutomationWindowReason(windowText: CharSequence?): String? {
        return if (AutomationTextMatcher.containsPairingPromptExclusion(windowText)) {
            "Unsafe window: Wireless debugging or ADB pairing screen"
        } else {
            null
        }
    }

    fun hasTargetContext(
        contextText: CharSequence?,
        targetName: CharSequence?,
        targetAddress: CharSequence?,
    ): Boolean {
        return TargetDeviceMatcher.matchesText(contextText, targetName, targetAddress)
    }

    fun isTargetConnectedContext(
        contextText: CharSequence?,
        targetName: CharSequence?,
        targetAddress: CharSequence?,
    ): Boolean {
        return hasTargetContext(contextText, targetName, targetAddress) &&
            AutomationTextMatcher.containsConnectedState(contextText)
    }

    fun isUnsafeSingleVisibleRepairCandidate(text: CharSequence?): Boolean {
        val normalized = AutomationTextMatcher.normalize(text)
        return normalized.isBlank() ||
            AutomationTextMatcher.containsPairingPromptExclusion(normalized) ||
            AutomationTextMatcher.isRepairPairNavigation(normalized) ||
            AutomationTextMatcher.isPairAction(normalized) ||
            AutomationTextMatcher.isDeviceListNavigation(normalized) ||
            AutomationTextMatcher.isGenericSettingsRow(normalized)
    }

    fun shortDiagnosticText(value: CharSequence?, maxLength: Int = DEFAULT_DIAGNOSTIC_TEXT_LENGTH): String {
        val redacted = redactSensitiveText(value)
        if (redacted.length <= maxLength) {
            return redacted
        }
        val prefixLength = (maxLength - ELLIPSIS.length).coerceAtLeast(0)
        return redacted.take(prefixLength).trimEnd() + ELLIPSIS
    }

    fun redactSensitiveText(value: CharSequence?): String {
        val text = value?.toString().orEmpty()
        return endpointPattern.replace(
            pairingCodePattern.replace(
                bluetoothAddressPattern.replace(text) { match ->
                    redactBluetoothAddress(match.value)
                },
            ) { match ->
                val label = match.groupValues.getOrNull(1).orEmpty()
                "$label <REDACTED_CODE>"
            },
            "<REDACTED_ENDPOINT>",
        )
    }

    private fun redactBluetoothAddress(address: String): String {
        val parts = address.uppercase().split(':')
        if (parts.size != BLUETOOTH_ADDRESS_PART_COUNT) {
            return "<REDACTED_ADDRESS>"
        }
        return listOf("**", "**", "**", "**", parts[4], parts[5]).joinToString(":")
    }

    private const val BLUETOOTH_ADDRESS_PART_COUNT = 6
    private const val DEFAULT_DIAGNOSTIC_TEXT_LENGTH = 120
    private const val ELLIPSIS = " ..."
}
