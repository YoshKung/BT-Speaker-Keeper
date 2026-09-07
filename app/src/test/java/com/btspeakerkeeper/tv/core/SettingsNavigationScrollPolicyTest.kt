package com.btspeakerkeeper.tv.core

import org.junit.Assert.*
import org.junit.Test

class SettingsNavigationScrollPolicyTest {
    private val screen = UiBounds(0, 0, 1920, 1080)
    private val outer = SettingsScrollCandidate("android.widget.HorizontalScrollView", UiBounds(0, 0, 1920, 1080), true, true)
    private val navigation = SettingsScrollCandidate("android.widget.GridView", UiBounds(220, 278, 900, 1080), true, true)
    private val preview = SettingsScrollCandidate("android.widget.GridView", UiBounds(1228, 278, 1908, 1080), true, true)

    @Test
    fun expandedGoogleTvTreeScrollsNavigationInsteadOfHorizontalPaneWrapper() {
        assertEquals(1, SettingsNavigationScrollPolicy.chooseIndex(listOf(outer, navigation, preview), screen))
    }

    @Test
    fun doesNotFallThroughToThePreviewWhenNavigationCannotScroll() {
        assertNull(SettingsNavigationScrollPolicy.chooseIndex(listOf(outer, navigation.copy(scrollable = false), preview), screen))
    }

    @Test
    fun rejectsHorizontalOrPreviewOnlyTreesAndHiddenLists() {
        assertNull(SettingsNavigationScrollPolicy.chooseIndex(listOf(outer), screen))
        assertNull(SettingsNavigationScrollPolicy.chooseIndex(listOf(preview), screen))
        assertNull(SettingsNavigationScrollPolicy.chooseIndex(listOf(navigation.copy(visible = false)), screen))
    }
}
