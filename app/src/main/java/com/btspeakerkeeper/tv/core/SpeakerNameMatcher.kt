package com.btspeakerkeeper.tv.core

object SpeakerNameMatcher {
    private val whitespace = Regex("\\s+")

    fun normalizeName(value: CharSequence?): String {
        return value
            ?.toString()
            ?.trim()
            ?.replace(whitespace, " ")
            .orEmpty()
    }

    fun matchesExactConfiguredName(candidate: CharSequence?, configured: CharSequence?): Boolean {
        val normalizedCandidate = normalizeName(candidate)
        val normalizedConfigured = normalizeName(configured)
        return normalizedConfigured.isNotEmpty() && normalizedCandidate == normalizedConfigured
    }

    fun containsConfiguredName(text: CharSequence?, configured: CharSequence?): Boolean {
        val normalizedText = normalizeName(text)
        val normalizedConfigured = normalizeName(configured)
        return normalizedConfigured.isNotEmpty() && normalizedText.contains(normalizedConfigured)
    }
}
