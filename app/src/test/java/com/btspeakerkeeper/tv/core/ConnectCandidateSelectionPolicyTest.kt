package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectCandidateSelectionPolicyTest {
    @Test
    fun selectsClickableCandidateWhenTargetContextIsInAncestors() {
        val selected = ConnectCandidateSelectionPolicy.chooseIndex(
            candidates = listOf(
                ConnectCandidateSignal(hasTargetAncestor = true, hasClickableAction = true),
                ConnectCandidateSignal(hasTargetAncestor = false, hasClickableAction = true),
            ),
            hasTargetWindowContext = true,
            targetActivated = false,
        )

        assertEquals(0, selected)
    }

    @Test
    fun rejectsAmbiguousConnectActionsAfterTargetActivation() {
        val selected = ConnectCandidateSelectionPolicy.chooseIndex(
            candidates = listOf(
                ConnectCandidateSignal(hasTargetAncestor = false, hasClickableAction = true),
                ConnectCandidateSignal(hasTargetAncestor = false, hasClickableAction = true),
            ),
            hasTargetWindowContext = true,
            targetActivated = true,
        )

        assertNull(selected)
    }

    @Test
    fun rejectsGlobalConnectBeforeTargetActivation() {
        val selected = ConnectCandidateSelectionPolicy.chooseIndex(
            candidates = listOf(
                ConnectCandidateSignal(hasTargetAncestor = false, hasClickableAction = true),
                ConnectCandidateSignal(hasTargetAncestor = false, hasClickableAction = true),
            ),
            hasTargetWindowContext = true,
            targetActivated = false,
        )

        assertNull(selected)
    }

    @Test
    fun rejectsConnectWhenWindowIsNotTargetContext() {
        val selected = ConnectCandidateSelectionPolicy.chooseIndex(
            candidates = listOf(
                ConnectCandidateSignal(hasTargetAncestor = false, hasClickableAction = true),
            ),
            hasTargetWindowContext = false,
            targetActivated = true,
        )

        assertNull(selected)
    }
}
