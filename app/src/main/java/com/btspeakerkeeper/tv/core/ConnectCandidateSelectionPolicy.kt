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
        val ancestorIndices = candidates.indices.filter { candidates[it].hasTargetAncestor }
        if (ancestorIndices.isNotEmpty()) return ancestorIndices.singleOrNull()

        if (!targetActivated || !hasTargetWindowContext) {
            return null
        }

        // Duplicate accessibility nodes for the same action are collapsed by the caller.
        // Separate remaining actions are ambiguous, even if one happens to be clickable.
        return candidates.indices.singleOrNull()
    }
}
