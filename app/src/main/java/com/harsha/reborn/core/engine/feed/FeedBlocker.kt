package com.harsha.reborn.core.engine.feed

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.harsha.reborn.core.data.model.SmartRulesConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FeedBlockCheckOutcome {
    data object Allow : FeedBlockCheckOutcome
    data object Debounced : FeedBlockCheckOutcome
    data object CooldownActive : FeedBlockCheckOutcome
    data class Block(val match: FeedBlockMatch) : FeedBlockCheckOutcome
}

/**
 * Isolated Feed Blocker module for in-app short-video feeds
 * (YouTube Shorts, Instagram Reels, Facebook Reels, Snapchat Spotlight).
 *
 * Guarantees:
 * - Config-driven selector catalog backed by `assets/feed_selectors.json` or imported JSON.
 * - Strictly inspects only target packages.
 * - Bounded tree depth & node traversal.
 * - Event debounce + per-package post-Back cooldown to prevent Back loops.
 * - Fails open (`Allow`) on any uncertainty or exception.
 */
@Singleton
class FeedBlocker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cachedAssetCatalog: FeedSelectorCatalog by lazy {
        FeedSelectorRegistry.loadFromAsset(context)
    }

    private val engine = FeedBlockerEngine()

    fun loadCatalog(customJson: String?): FeedSelectorCatalog =
        FeedSelectorRegistry.loadCatalog(customJson = customJson, fallbackCatalog = cachedAssetCatalog)

    fun getAssetCatalog(): FeedSelectorCatalog = cachedAssetCatalog

    fun shouldScanPackage(packageName: String, config: SmartRulesConfig): Boolean {
        if (!config.inAppFeedBlockingEnabled || config.enabledFeedProfileIds.isEmpty()) return false
        val catalog = loadCatalog(config.customFeedSelectorsJson)
        return isTargetPackage(packageName, catalog) &&
            catalog.profiles.any { it.id in config.enabledFeedProfileIds && packageName in it.packageNames }
    }

    fun inspectAccessibilityWindow(
        packageName: String,
        rootNode: AccessibilityNodeInfo?,
        config: SmartRulesConfig,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): FeedBlockCheckOutcome {
        return try {
            val catalog = loadCatalog(config.customFeedSelectorsJson)
            if (!shouldScanPackage(packageName, config)) {
                return FeedBlockCheckOutcome.Allow
            }
            if (!engine.canScanPackage(packageName, nowElapsedMs)) {
                return if (engine.isInCooldown(packageName, nowElapsedMs)) {
                    FeedBlockCheckOutcome.CooldownActive
                } else {
                    FeedBlockCheckOutcome.Debounced
                }
            }
            val snapshot = InAppFeedDetector.captureSnapshot(
                root = rootNode,
                packageName = packageName,
                maxNodes = DEFAULT_MAX_NODES,
                maxDepth = DEFAULT_MAX_TREE_DEPTH,
            )
            engine.evaluateWindowSnapshot(
                packageName = packageName,
                snapshot = snapshot,
                catalog = catalog,
                config = config,
                nowElapsedMs = nowElapsedMs,
            )
        } catch (_: Throwable) {
            FeedBlockCheckOutcome.Allow
        }
    }

    fun resetPackageState(packageName: String) {
        engine.resetPackage(packageName)
    }

    companion object {
        val TARGET_PACKAGES: Set<String> = setOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.facebook.katana",
            "com.snapchat.android",
        )

        const val DEFAULT_EVENT_DEBOUNCE_MS = 380L
        const val DEFAULT_BACK_COOLDOWN_MS = 2_500L
        const val DEFAULT_DM_GRACE_MS = 20_000L
        const val DEFAULT_MAX_TREE_DEPTH = InAppFeedDetector.DEFAULT_MAX_DEPTH
        const val DEFAULT_MAX_NODES = InAppFeedDetector.DEFAULT_MAX_NODES

        fun isTargetPackage(
            packageName: String,
            catalog: FeedSelectorCatalog = FeedSelectorRegistry.defaultCatalog,
        ): Boolean {
            if (packageName.isBlank()) return false
            if (packageName in TARGET_PACKAGES) return true
            return catalog.profiles.any { packageName in it.packageNames }
        }
    }
}

/**
 * Pure-Kotlin state machine for debouncing, Back-loop cooldowns, DM navigation grace,
 * and fail-open selector evaluation. Separated for deterministic JVM unit testing.
 */
class FeedBlockerEngine(
    private val debounceMs: Long = FeedBlocker.DEFAULT_EVENT_DEBOUNCE_MS,
    private val cooldownMs: Long = FeedBlocker.DEFAULT_BACK_COOLDOWN_MS,
    private val dmGraceMs: Long = FeedBlocker.DEFAULT_DM_GRACE_MS,
) {
    private val lastScannedElapsedMs = ConcurrentHashMap<String, Long>()
    private val lastBlockedElapsedMs = ConcurrentHashMap<String, Long>()
    private val lastDmContextElapsedMs = ConcurrentHashMap<String, Long>()

    fun isInCooldown(packageName: String, nowElapsedMs: Long): Boolean {
        val blockedAt = lastBlockedElapsedMs[packageName] ?: return false
        return (nowElapsedMs - blockedAt) in 0L until cooldownMs
    }

    fun canScanPackage(packageName: String, nowElapsedMs: Long): Boolean {
        if (isInCooldown(packageName, nowElapsedMs)) return false
        val scannedAt = lastScannedElapsedMs[packageName] ?: return true
        return (nowElapsedMs - scannedAt) >= debounceMs
    }

    fun evaluateWindowSnapshot(
        packageName: String,
        snapshot: FeedWindowSnapshot,
        catalog: FeedSelectorCatalog,
        config: SmartRulesConfig,
        nowElapsedMs: Long,
    ): FeedBlockCheckOutcome = runCatching {
        if (!config.inAppFeedBlockingEnabled || config.enabledFeedProfileIds.isEmpty()) {
            return@runCatching FeedBlockCheckOutcome.Allow
        }
        if (!FeedBlocker.isTargetPackage(packageName, catalog)) {
            return@runCatching FeedBlockCheckOutcome.Allow
        }
        if (isInCooldown(packageName, nowElapsedMs)) {
            return@runCatching FeedBlockCheckOutcome.CooldownActive
        }
        val scannedAt = lastScannedElapsedMs[packageName]
        if (scannedAt != null && (nowElapsedMs - scannedAt) < debounceMs) {
            return@runCatching FeedBlockCheckOutcome.Debounced
        }
        lastScannedElapsedMs[packageName] = nowElapsedMs

        if (snapshot.nodes.isEmpty()) {
            return@runCatching FeedBlockCheckOutcome.Allow
        }

        if (InAppFeedDetector.isDmWindowForPackage(snapshot, catalog)) {
            lastDmContextElapsedMs[packageName] = nowElapsedMs
        }

        val dmSeenAt = lastDmContextElapsedMs[packageName]
        val recentlyFromDm = config.allowReelsFromDms &&
            dmSeenAt != null &&
            (nowElapsedMs - dmSeenAt) in 0L..dmGraceMs

        val match = InAppFeedDetector.evaluateWindow(
            snapshot = snapshot,
            catalog = catalog,
            enabledProfileIds = config.enabledFeedProfileIds,
            allowReelsFromDms = config.allowReelsFromDms,
            recentlyFromDm = recentlyFromDm,
        )

        if (match == null) {
            FeedBlockCheckOutcome.Allow
        } else {
            lastDmContextElapsedMs.remove(packageName)
            lastBlockedElapsedMs[packageName] = nowElapsedMs
            FeedBlockCheckOutcome.Block(match)
        }
    }.getOrDefault(FeedBlockCheckOutcome.Allow)

    fun resetPackage(packageName: String) {
        lastScannedElapsedMs.remove(packageName)
        lastBlockedElapsedMs.remove(packageName)
        lastDmContextElapsedMs.remove(packageName)
    }
}
