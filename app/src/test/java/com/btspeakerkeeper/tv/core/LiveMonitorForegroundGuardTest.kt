package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMonitorForegroundGuardTest {
    @Test
    fun pausesLiveMonitorWhenSettingsIsForegroundWithoutAutomationSession() {
        assertTrue(
            LiveMonitorForegroundGuard.shouldPauseForUserOwnedSettings(
                activePackageName = "com.android.tv.settings",
                hasActiveAutomationSession = false,
            ),
        )
    }

    @Test
    fun allowsLiveMonitorWhenAutomationSessionOwnsSettings() {
        assertFalse(
            LiveMonitorForegroundGuard.shouldPauseForUserOwnedSettings(
                activePackageName = "com.android.tv.settings",
                hasActiveAutomationSession = true,
            ),
        )
    }

    @Test
    fun pausesForGoogleTvSettingsPackagesOnlyWhenUserOwned() {
        val settingsPackages = listOf(
            "com.android.settings",
            "com.android.tv.settings",
            "com.google.android.tv.settings",
            "com.google.android.chromecast.chromecastservice",
        )

        settingsPackages.forEach { packageName ->
            assertTrue(
                packageName,
                LiveMonitorForegroundGuard.shouldPauseForUserOwnedSettings(
                    activePackageName = packageName,
                    hasActiveAutomationSession = false,
                ),
            )
            assertFalse(
                packageName,
                LiveMonitorForegroundGuard.shouldPauseForUserOwnedSettings(
                    activePackageName = packageName,
                    hasActiveAutomationSession = true,
                ),
            )
        }
    }

    @Test
    fun allowsLiveMonitorOutsideSettings() {
        assertFalse(
            LiveMonitorForegroundGuard.shouldPauseForUserOwnedSettings(
                activePackageName = "com.google.android.apps.tv.launcherx",
                hasActiveAutomationSession = false,
            ),
        )
    }
}
