package com.btspeakerkeeper.tv.core

data class ConnectCandidateSignal(
    val hasTargetAncestor: Boolean,
    val hasClickableAction: Boolean,
)

object ConnectCandidateSelectionPolicy {
    fun chooseIndex(
        candidates: List<ConnectCandidateSignal>,
        hasTargetWindowContext: Boolean,
        targetActivated: Boolean,
    ): Int? {
        val ancestorClickableIndex = candidates.indexOfFirst { candidate ->
            candidate.hasTargetAncestor && candidate.hasClickableAction
        }
        if (ancestorClickableIndex >= 0) {
            return ancestorClickableIndex
        }

        val ancestorIndex = candidates.indexOfFirst { candidate -> candidate.hasTargetAncestor }
        if (ancestorIndex >= 0) {
            return ancestorIndex
        }

        if (!targetActivated || !hasTargetWindowContext) {
            return null
        }

        val clickableIndex = candidates.indexOfFirst { candidate -> candidate.hasClickableAction }
        return clickableIndex.takeIf { it >= 0 } ?: candidates.indices.firstOrNull()
    }
}
