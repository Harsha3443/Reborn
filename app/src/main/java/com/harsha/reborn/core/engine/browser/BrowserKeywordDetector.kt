package com.harsha.reborn.core.engine.browser

import android.view.accessibility.AccessibilityNodeInfo
import com.harsha.reborn.core.data.model.BrowserKeywordRule

data class BrowserUrlProfile(
    val packageName: String,
    val urlBarIds: List<String>,
)

data class BrowserMatch(
    val rule: BrowserKeywordRule,
    val observedUrlText: String,
)

/**
 * Config-driven URL bar reader and website/keyword detector for major Android browsers.
 * Fails open (returns null) if the browser is unsupported or the URL bar cannot be read.
 */
object BrowserKeywordDetector {

    val SUPPORTED_BROWSERS: List<BrowserUrlProfile> = listOf(
        BrowserUrlProfile("com.android.chrome", listOf("url_bar", "search_box_text")),
        BrowserUrlProfile("com.chrome.beta", listOf("url_bar")),
        BrowserUrlProfile("com.chrome.dev", listOf("url_bar")),
        BrowserUrlProfile("com.chrome.canary", listOf("url_bar")),
        BrowserUrlProfile("org.mozilla.firefox", listOf("mozac_browser_toolbar_url_view", "url_bar_title")),
        BrowserUrlProfile("org.mozilla.firefox_beta", listOf("mozac_browser_toolbar_url_view", "url_bar_title")),
        BrowserUrlProfile("org.mozilla.focus", listOf("mozac_browser_toolbar_url_view", "display_url")),
        BrowserUrlProfile("com.brave.browser", listOf("url_bar")),
        BrowserUrlProfile("com.microsoft.emmx", listOf("url_bar")),
        BrowserUrlProfile("com.sec.android.app.sbrowser", listOf("location_bar_edit_text")),
        BrowserUrlProfile("com.duckduckgo.mobile.android", listOf("omnibarTextInput")),
        BrowserUrlProfile("com.opera.browser", listOf("url_field")),
        BrowserUrlProfile("com.opera.mini.native", listOf("url_field")),
        BrowserUrlProfile("com.vivaldi.browser", listOf("url_bar")),
        BrowserUrlProfile("com.kiwibrowser.browser", listOf("url_bar")),
    )

    private val browserPackages: Set<String> = SUPPORTED_BROWSERS.map { it.packageName }.toSet()

    private val placeholderPhrases = setOf(
        "search or type web address",
        "search or type url",
        "search or enter address",
        "type a url",
        "enter search or web address",
    )

    fun isSupportedBrowser(packageName: String): Boolean = packageName in browserPackages

    fun extractUrlBarText(
        root: AccessibilityNodeInfo?,
        packageName: String,
        maxNodes: Int = 90,
    ): String? = runCatching {
        if (root == null) return@runCatching null
        val profile = SUPPORTED_BROWSERS.firstOrNull { it.packageName == packageName }
            ?: return@runCatching null

        // Fast direct viewId lookup first
        for (idSuffix in profile.urlBarIds) {
            val fullId = "$packageName:id/$idSuffix"
            val matched = runCatching { root.findAccessibilityNodeInfosByViewId(fullId) }.getOrNull().orEmpty()
            val candidate = matched.firstOrNull()?.text?.toString()?.trim()
            if (!candidate.isNullOrBlank() && candidate.lowercase() !in placeholderPhrases) {
                return@runCatching candidate
            }
        }

        // Bounded BFS fallback for browsers with sub-packaged or dynamic IDs
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()
            if (viewId.isNotEmpty() && profile.urlBarIds.any { viewId.endsWith(":id/$it") || viewId.contains(it) }) {
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrBlank() && text.lowercase() !in placeholderPhrases) {
                    return@runCatching text
                }
            }
            val childCount = node.childCount
            for (i in 0 until childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        null
    }.getOrNull()

    fun matchUrlOrKeyword(
        urlBarText: String?,
        rules: List<BrowserKeywordRule>,
    ): BrowserMatch? {
        val raw = urlBarText?.trim().orEmpty()
        if (raw.isEmpty() || raw.lowercase() in placeholderPhrases) return null

        val normalizedInput = raw.lowercase()
        val decodedForKeywords = normalizedInput
            .replace("+", " ")
            .replace("%20", " ")
        val hostCandidate = extractHost(normalizedInput)

        for (rule in rules) {
            if (!rule.isEnabled) continue
            val pattern = rule.pattern.trim()
                .lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .trimEnd('/')
            if (pattern.length < 2) continue

            if (rule.isDomain) {
                val domainOnly = pattern.substringBefore('/')
                val domainHit = hostCandidate == domainOnly ||
                    hostCandidate.endsWith(".$domainOnly") ||
                    normalizedInput.contains("://$domainOnly") ||
                    normalizedInput.contains(".$domainOnly/") ||
                    normalizedInput.startsWith("$domainOnly/")
                if (domainHit) {
                    return BrowserMatch(rule = rule, observedUrlText = raw)
                }
            } else {
                if (normalizedInput.contains(pattern) || decodedForKeywords.contains(pattern)) {
                    return BrowserMatch(rule = rule, observedUrlText = raw)
                }
            }
        }
        return null
    }

    private fun extractHost(normalizedUrl: String): String {
        val withoutScheme = normalizedUrl
            .removePrefix("https://")
            .removePrefix("http://")
        val hostPort = withoutScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
        return hostPort.removePrefix("www.")
    }
}
