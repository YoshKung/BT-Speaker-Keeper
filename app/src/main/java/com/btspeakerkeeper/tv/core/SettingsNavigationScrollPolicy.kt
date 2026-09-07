package com.btspeakerkeeper.tv.core

data class SettingsScrollCandidate(val className: String, val bounds: UiBounds, val visible: Boolean, val scrollable: Boolean)

object SettingsNavigationScrollPolicy {
    fun chooseIndex(candidates: List<SettingsScrollCandidate>, screen: UiBounds): Int? {
        if (screen.width <= 0 || screen.bottom <= screen.top) return null
        // Expanded two-pane trees also expose the horizontal page wrapper and preview list.
        val navigation = candidates.indices.filter { index ->
            val candidate = candidates[index]
            val bounds = candidate.bounds
            candidate.visible && candidate.className.substringAfterLast('.') in verticalLists &&
                bounds.width > 0 && bounds.bottom > bounds.top &&
                bounds.left >= screen.left && bounds.left < screen.left + screen.width / 2 &&
                bounds.right <= screen.right && bounds.top < screen.bottom && bounds.bottom > screen.top
        }.minByOrNull { candidates[it].bounds.left }
        return navigation?.takeIf { candidates[it].scrollable }
    }

    private val verticalLists = setOf("GridView", "ListView", "RecyclerView", "VerticalGridView")
}
