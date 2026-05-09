package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerNameMatcherTest {
    @Test
    fun matchesExactConfiguredNameAfterWhitespaceNormalization() {
        assertTrue(
            SpeakerNameMatcher.matchesExactConfiguredName(
                candidate = "  Demo   Speaker ",
                configured = "Demo Speaker",
            ),
        )
    }

    @Test
    fun doesNotMatchDifferentCaseBecauseUserEnteredExactName() {
        assertFalse(
            SpeakerNameMatcher.matchesExactConfiguredName(
                candidate = "demo speaker",
                configured = "Demo Speaker",
            ),
        )
    }

    @Test
    fun findsConfiguredNameInsideSettingsRowText() {
        assertTrue(
            SpeakerNameMatcher.containsConfiguredName(
                text = "Demo Speaker Connected",
                configured = "Demo Speaker",
            ),
        )
    }
}
