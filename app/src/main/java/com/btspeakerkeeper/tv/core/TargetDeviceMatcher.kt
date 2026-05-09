package com.btspeakerkeeper.tv.core

object TargetDeviceMatcher {
    private val bluetoothAddressPattern = Regex("(?i)([0-9A-F]{2}:){5}[0-9A-F]{2}")

    fun matchesText(
        text: CharSequence?,
        targetName: CharSequence?,
        targetAddress: CharSequence?,
    ): Boolean {
        return SpeakerNameMatcher.containsConfiguredName(text, targetName) ||
            containsConfiguredAddress(text, targetAddress)
    }

    fun containsConfiguredAddress(text: CharSequence?, targetAddress: CharSequence?): Boolean {
        val normalizedText = normalizeAddressSearchText(text)
        val normalizedAddress = normalizeAddress(targetAddress)
        return normalizedAddress.isNotEmpty() && normalizedText.contains(normalizedAddress)
    }

    fun addressesEqual(candidate: CharSequence?, configured: CharSequence?): Boolean {
        val normalizedCandidate = normalizeAddress(candidate)
        val normalizedConfigured = normalizeAddress(configured)
        return normalizedConfigured.isNotEmpty() && normalizedCandidate == normalizedConfigured
    }

    fun normalizeAddress(value: CharSequence?): String {
        return value
            ?.toString()
            ?.trim()
            ?.uppercase()
            .orEmpty()
    }

    fun firstAddressIn(value: CharSequence?): String {
        return bluetoothAddressPattern.find(value?.toString().orEmpty())
            ?.value
            ?.let { normalizeAddress(it) }
            .orEmpty()
    }

    private fun normalizeAddressSearchText(value: CharSequence?): String {
        return value
            ?.toString()
            ?.uppercase()
            .orEmpty()
    }
}
