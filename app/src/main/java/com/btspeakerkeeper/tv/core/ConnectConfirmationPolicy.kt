package com.btspeakerkeeper.tv.core

object ConnectConfirmationPolicy {
    fun allows(packageName: String, labels: List<String>, targetName: String, waitingForA2dp: Boolean): Boolean {
        if (!waitingForA2dp || packageName != "com.google.android.chromecast.chromecastservice") return false
        val name = SpeakerNameMatcher.normalizeName(targetName)
        if (name.isBlank()) return false
        val text = labels.map(AutomationTextMatcher::normalize).filter(String::isNotBlank)
        if (AutomationSafetyPolicy.unsafeAutomationWindowReason(text.joinToString(" ")) != null) return false
        val titles = setOf("Connect to $name", "เชื่อมต่อกับ $name")
        return text.any { it in titles } && text.any(::isAffirmative) &&
            text.any { it == "No" || it == "ไม่" } &&
            text.all { it in titles || isAffirmative(it) || it == "No" || it == "ไม่" }
    }

    fun isAffirmative(label: CharSequence?): Boolean = AutomationTextMatcher.normalize(label) in setOf("Yes", "ใช่")
}
