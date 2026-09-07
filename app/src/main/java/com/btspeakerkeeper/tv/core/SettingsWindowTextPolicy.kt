package com.btspeakerkeeper.tv.core

object SettingsWindowTextPolicy {
    fun choose(fullText: String, navigationPaneText: String): String =
        if (AutomationSafetyPolicy.unsafeAutomationWindowReason(fullText) == null &&
            AutomationTextMatcher.isSettingsHome(navigationPaneText) &&
            !AutomationTextMatcher.isWrongSettingsDestination(navigationPaneText)
        ) navigationPaneText else fullText
}
