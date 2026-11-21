package com.harsha.reborn.core.engine.feed

import android.view.accessibility.AccessibilityNodeInfo

data class FeedNodeSnapshot(
    val viewIdResourceName: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val isSelected: Boolean = false,
    val isVisibleToUser: Boolean = true,
)

data class FeedWindowSnapshot(
    val packageName: String,
    val nodes: List<FeedNodeSnapshot> = emptyList(),
)

data class FeedBlockMatch(
    val profileId: String,
    val displayName: String,
    val matchedBy: String,
    val isTabSelection: Boolean = false,
)

/**
 * Isolated Accessibility detector for short-form video feeds (Shorts, Reels, Spotlight).
 * Strictly fails open (returns null) if the node tree is unavailable, config is invalid,
 * or only an unselected bottom-bar tab is visible on the app's main screen.
 */
object InAppFeedDetector {

    const val DEFAULT_MAX_NODES = 140
    const val DEFAULT_MAX_DEPTH = 12

    fun evaluateWindow(
        snapshot: FeedWindowSnapshot,
        catalog: FeedSelectorCatalog,
        enabledProfileIds: Set<String>,
        allowReelsFromDms: Boolean = false,
        recentlyFromDm: Boolean = false,
    ): FeedBlockMatch? = runCatching {
        if (snapshot.packageName.isBlank() || snapshot.nodes.isEmpty() || catalog.profiles.isEmpty()) {
            return@runCatching null
        }

        val matchingProfiles = catalog.profiles.filter { profile ->
            profile.id in enabledProfileIds && snapshot.packageName in profile.packageNames
        }
        if (matchingProfiles.isEmpty()) return@runCatching null

        val visibleNodes = snapshot.nodes.filter { it.isVisibleToUser }
        if (visibleNodes.isEmpty()) return@runCatching null

        for (profile in matchingProfiles) {
            val dmContextVisible = isDmContextForProfile(visibleNodes, profile)
            if (allowReelsFromDms && dmContextVisible) {
                continue
            }

            // 1. Selected tab check (explicit navigation into Shorts/Reels/Spotlight tab).
            for (node in visibleNodes) {
                if (!node.isSelected) continue
                val viewId = node.viewIdResourceName?.lowercase().orEmpty()
                val label = (node.text ?: node.contentDescription)?.trim()?.lowercase().orEmpty()

                val matchedTabId = profile.selectedTabViewIds.firstOrNull { pattern ->
                    pattern.isNotBlank() && viewId.isNotEmpty() && viewIdMatches(viewId, pattern)
                }
                if (matchedTabId != null) {
                    return@runCatching FeedBlockMatch(
                        profileId = profile.id,
                        displayName = profile.displayName,
                        matchedBy = "selectedTabId:$matchedTabId",
                        isTabSelection = true,
                    )
                }

                val matchedTabLabel = profile.selectedTabLabels.firstOrNull { expected ->
                    expected.isNotBlank() && label == expected.trim().lowercase()
                }
                if (matchedTabLabel != null) {
                    return@runCatching FeedBlockMatch(
                        profileId = profile.id,
                        displayName = profile.displayName,
                        matchedBy = "selectedTabLabel:$matchedTabLabel",
                        isTabSelection = true,
                    )
                }
            }

            // If opened directly from a DM thread (and not via the Reels/Shorts tab), honor DM allowance.
            if (allowReelsFromDms && recentlyFromDm) {
                continue
            }

            // 2. Active full-screen Reels/Shorts/Spotlight viewer container view ID.
            for (node in visibleNodes) {
                val viewId = node.viewIdResourceName?.lowercase().orEmpty()
                if (viewId.isNotEmpty()) {
                    val matchedId = profile.activeContainerViewIds.firstOrNull { pattern ->
                        pattern.isNotBlank() && viewIdMatches(viewId, pattern)
                    }
                    if (matchedId != null) {
                        return@runCatching FeedBlockMatch(
                            profileId = profile.id,
                            displayName = profile.displayName,
                            matchedBy = "viewId:$matchedId",
                            isTabSelection = false,
                        )
                    }
                }
            }

            // 3. Fallback multiple marker clues on screen.
            if (profile.markerTextClues.isNotEmpty() && profile.minMarkerCluesRequired > 0) {
                val allTexts = visibleNodes.flatMap { node ->
                    listOfNotNull(
                        node.text?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                        node.contentDescription?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                    )
                }
                val hits = profile.markerTextClues.count { clue ->
                    val normalized = clue.trim().lowercase()
                    normalized.isNotEmpty() && allTexts.any { it.contains(normalized) }
                }
                if (hits >= profile.minMarkerCluesRequired) {
                    return@runCatching FeedBlockMatch(
                        profileId = profile.id,
                        displayName = profile.displayName,
                        matchedBy = "markerClues:$hits",
                        isTabSelection = false,
                    )
                }
            }
        }

        null
    }.getOrNull()

    fun isDmWindowForPackage(
        snapshot: FeedWindowSnapshot,
        catalog: FeedSelectorCatalog,
    ): Boolean = runCatching {
        if (snapshot.packageName.isBlank() || snapshot.nodes.isEmpty()) return@runCatching false
        val visibleNodes = snapshot.nodes.filter { it.isVisibleToUser }
        if (visibleNodes.isEmpty()) return@runCatching false
        catalog.profiles
            .filter { snapshot.packageName in it.packageNames }
            .any { isDmContextForProfile(visibleNodes, it) }
    }.getOrDefault(false)

    private fun isDmContextForProfile(
        visibleNodes: List<FeedNodeSnapshot>,
        profile: FeedSelectorProfile,
    ): Boolean {
        if (profile.dmContextViewIds.isEmpty() && profile.dmContextTextClues.isEmpty()) return false
        for (node in visibleNodes) {
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()
            if (viewId.isNotEmpty() && profile.dmContextViewIds.any { it.isNotBlank() && viewIdMatches(viewId, it) }) {
                return true
            }
            val text = node.text?.trim()?.lowercase().orEmpty()
            val desc = node.contentDescription?.trim()?.lowercase().orEmpty()
            if (profile.dmContextTextClues.any { clue ->
                    val target = clue.trim().lowercase()
                    target.isNotEmpty() && (text.contains(target) || desc.contains(target))
                }
            ) {
                return true
            }
        }
        return false
    }

    internal interface NodeSource {
        val viewIdResourceName: String?
        val text: CharSequence?
        val contentDescription: CharSequence?
        val isSelected: Boolean
        val isVisibleToUser: Boolean
        val childCount: Int
        fun getChild(index: Int): NodeSource?
    }

    private class AndroidNodeSource(
        private val node: AccessibilityNodeInfo,
    ) : NodeSource {
        override val viewIdResourceName: String?
            get() = node.viewIdResourceName
        override val text: CharSequence?
            get() = node.text
        override val contentDescription: CharSequence?
            get() = node.contentDescription
        override val isSelected: Boolean
            get() = node.isSelected
        override val isVisibleToUser: Boolean
            get() = runCatching { node.isVisibleToUser }.getOrDefault(true)
        override val childCount: Int
            get() = runCatching { node.childCount }.getOrDefault(0)
        override fun getChild(index: Int): NodeSource? =
            runCatching { node.getChild(index) }.getOrNull()?.let(::AndroidNodeSource)
    }

    fun captureSnapshot(
        root: AccessibilityNodeInfo?,
        packageName: String,
        maxNodes: Int = DEFAULT_MAX_NODES,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
    ): FeedWindowSnapshot = captureNodeSource(
        root = root?.let(::AndroidNodeSource),
        packageName = packageName,
        maxNodes = maxNodes,
        maxDepth = maxDepth,
    )

    internal fun captureNodeSource(
        root: NodeSource?,
        packageName: String,
        maxNodes: Int = DEFAULT_MAX_NODES,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
    ): FeedWindowSnapshot = runCatching {
        if (root == null) return@runCatching FeedWindowSnapshot(packageName = packageName)

        val nodes = mutableListOf<FeedNodeSnapshot>()
        val queue = ArrayDeque<Pair<NodeSource, Int>>()
        queue.add(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < maxNodes) {
            val (node, depth) = queue.removeFirst()
            visited++
            val visible = runCatching { node.isVisibleToUser }.getOrDefault(true)
            nodes.add(
                FeedNodeSnapshot(
                    viewIdResourceName = node.viewIdResourceName,
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    isSelected = node.isSelected,
                    isVisibleToUser = visible,
                )
            )
            if (depth < maxDepth) {
                val childCount = runCatching { node.childCount }.getOrDefault(0)
                for (i in 0 until childCount) {
                    runCatching { node.getChild(i) }.getOrNull()?.let { child ->
                        queue.add(child to (depth + 1))
                    }
                }
            }
        }

        FeedWindowSnapshot(packageName = packageName, nodes = nodes)
    }.getOrDefault(FeedWindowSnapshot(packageName = packageName))

    private fun viewIdMatches(fullViewId: String, pattern: String): Boolean {
        val target = pattern.trim().lowercase()
        return fullViewId == target || fullViewId.endsWith(":id/$target") || fullViewId.contains(target)
    }
}
