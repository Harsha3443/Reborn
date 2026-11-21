package com.harsha.reborn.core.engine

import android.view.accessibility.AccessibilityNodeInfo
import com.harsha.reborn.core.data.model.BlockDecision
import com.harsha.reborn.core.data.model.BlockReason

/**
 * Detects system screens capable of disabling Accessibility, Force Stopping,
 * revoking Device Admin, or uninstalling Reborn while Strict Mode is active.
 * Fails open on unrelated system screens (Wi-Fi, Bluetooth, Display, etc.)
 * so the user's phone settings remain usable.
 */
object StrictSystemScreenDetector {

    val monitoredSystemPackages = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
    )

    private val installerPackages = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
    )

    fun evaluateSystemWindow(
        packageName: String,
        className: String?,
        visibleTexts: List<String>,
        appName: String = "Reborn",
    ): BlockDecision {
        if (packageName !in monitoredSystemPackages) return BlockDecision.Allow

        val normalizedClass = className?.lowercase().orEmpty()
        val combinedText = visibleTexts.joinToString(" ").lowercase()
        val normalizedApp = appName.lowercase()
        val mentionsReborn = combinedText.contains(normalizedApp) ||
            combinedText.contains("reborn app guard") ||
            combinedText.contains("reborn uninstall guard")

        // 1. Package Installer uninstall prompts mentioning Reborn
        if (packageName in installerPackages && mentionsReborn) {
            return BlockDecision.Block(
                reason = BlockReason.STRICT_SYSTEM_GUARD,
                title = "Strict Mode Protected",
                detail = "Uninstalling Reborn is locked until Strict Mode finishes.",
            )
        }

        // 2. Device Admin deactivation screens
        if (normalizedClass.contains("deviceadmin") && (mentionsReborn || combinedText.contains("deactivate"))) {
            return BlockDecision.Block(
                reason = BlockReason.STRICT_SYSTEM_GUARD,
                title = "Device Admin Locked",
                detail = "Deactivating Device Admin is blocked while Strict Mode is active.",
            )
        }

        // 3. Accessibility service toggle / Accessibility settings when Reborn is visible or target fragment
        val isAccessibilityClass = normalizedClass.contains("accessibility") ||
            normalizedClass.contains("toggleaccessibility")
        if (isAccessibilityClass && (mentionsReborn || combinedText.contains("accessibility"))) {
            return BlockDecision.Block(
                reason = BlockReason.STRICT_SYSTEM_GUARD,
                title = "Accessibility Guard Locked",
                detail = "Turning off Accessibility is blocked while Strict Mode is active.",
            )
        }

        // 4. App Info / Force Stop / Uninstall inside Settings for Reborn
        val isAppInfoScreen = normalizedClass.contains("installedappdetails") ||
            normalizedClass.contains("appinfo") ||
            normalizedClass.contains("subsettings") ||
            normalizedClass.contains("securitycenter")
        val hasDangerControls = combinedText.contains("force stop") ||
            combinedText.contains("uninstall") ||
            combinedText.contains("use reborn") ||
            combinedText.contains("accessibility") ||
            combinedText.contains("device admin")

        if (mentionsReborn && (isAppInfoScreen || hasDangerControls)) {
            return BlockDecision.Block(
                reason = BlockReason.STRICT_SYSTEM_GUARD,
                title = "Strict Mode Protected",
                detail = "Settings that disable Reborn, Accessibility, or Force Stop are locked during Strict Mode.",
            )
        }

        return BlockDecision.Allow
    }

    fun collectBoundedTexts(root: AccessibilityNodeInfo?, maxNodes: Int = 80): List<String> {
        if (root == null) return emptyList()
        val texts = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return texts
    }
}
