package com.btspeakerkeeper.tv.control

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.btspeakerkeeper.tv.accessibility.BtKeeperAccessibilityService

object AccessibilityUtils {
    fun isKeeperServiceEnabled(context: Context): Boolean {
        val resolver = context.applicationContext.contentResolver
        val enabled = Settings.Secure.getInt(
            resolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!enabled) {
            return false
        }

        val expected = ComponentName(context, BtKeeperAccessibilityService::class.java)
        val expectedNames = setOf(expected.flattenToString(), expected.flattenToShortString())
        val enabledServices = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val serviceName = splitter.next()
            if (expectedNames.any { expectedName -> serviceName.equals(expectedName, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }
}
