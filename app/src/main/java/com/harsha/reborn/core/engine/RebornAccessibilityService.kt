package com.harsha.reborn.core.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.R
import com.harsha.reborn.core.data.model.BlockDecision
import com.harsha.reborn.core.data.model.BlockReason
import com.harsha.reborn.core.engine.feed.FeedBlockCheckOutcome
import com.harsha.reborn.core.engine.feed.FeedBlocker
import com.harsha.reborn.feature.blocking.BlockActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core AccessibilityService detecting foreground app transitions (TYPE_WINDOW_STATE_CHANGED)
 * and throttled in-window transitions (TYPE_WINDOW_CONTENT_CHANGED) for Strict Mode system guards,
 * target-package short-video feed blocking (FeedBlocker), browser URL/keyword blocking, and
 * grayscale dim overlays.
 */
class RebornAccessibilityService : AccessibilityService() {

    private val entryPoint: EngineEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, EngineEntryPoint::class.java)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastEvaluatedPackage: String? = null
    private var lastEvaluatedElapsedMs: Long = 0L
    private var lastSystemGuardCheckMs: Long = 0L
    private var lastBrowserCheckMs: Long = 0L
    private var lastBrowserBlockedPackage: String? = null
    private var lastBrowserBlockedMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isConnected.value = true
        runCatching { entryPoint.rulesCacheManager().ensureStarted() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            val eventType = event.eventType
            if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) {
                return
            }

            val packageName = event.packageName?.toString()?.trim()
            if (packageName.isNullOrEmpty()) return

            val cacheManager = entryPoint.rulesCacheManager()
            cacheManager.ensureStarted()

            // Ignore Reborn's own windows (e.g., BlockActivity or Settings) to avoid self-trigger loops.
            if (packageName == applicationContext.packageName) {
                cacheManager.hideGrayscaleOverlay()
                return
            }

            val feedBlocker = entryPoint.feedBlocker()
            val smartConfig = cacheManager.currentSmartRulesConfig()
            val isSystemPackage = packageName in StrictSystemScreenDetector.monitoredSystemPackages
            val isTargetFeedPackage = feedBlocker.shouldScanPackage(packageName, smartConfig)
            val isBrowserPackage = cacheManager.shouldInspectBrowserContent(packageName)

            if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                val nowContentMs = SystemClock.elapsedRealtime()
                val allowSystemCheck = isSystemPackage &&
                    cacheManager.isStrictSystemGuardActive() &&
                    (nowContentMs - lastSystemGuardCheckMs >= SYSTEM_GUARD_DEBOUNCE_MS)
                val allowBrowserCheck = isBrowserPackage &&
                    (nowContentMs - lastBrowserCheckMs >= BROWSER_CONTENT_DEBOUNCE_MS)

                // For TYPE_WINDOW_CONTENT_CHANGED, only proceed for guarded system windows,
                // target feed packages (YouTube/Instagram/Facebook/Snapchat), or monitored browsers.
                if (!allowSystemCheck && !isTargetFeedPackage && !allowBrowserCheck) return

                if (allowSystemCheck) lastSystemGuardCheckMs = nowContentMs
                if (allowBrowserCheck) lastBrowserCheckMs = nowContentMs
            } else {
                val nowElapsed = SystemClock.elapsedRealtime()
                if (packageName == lastEvaluatedPackage && nowElapsed - lastEvaluatedElapsedMs < DEBOUNCE_WINDOW_MS) {
                    return
                }
                lastEvaluatedPackage = packageName
                lastEvaluatedElapsedMs = nowElapsed
                cacheManager.onForegroundPackageChanged(packageName)
            }

            if (isSystemPackage && cacheManager.isStrictSystemGuardActive()) {
                val eventTexts = event.text.mapNotNull { it?.toString()?.takeIf { t -> t.isNotBlank() } }
                val nodeTexts = StrictSystemScreenDetector.collectBoundedTexts(rootInActiveWindow)
                val systemDecision = cacheManager.evaluateSystemWindow(
                    packageName = packageName,
                    className = event.className?.toString(),
                    visibleTexts = eventTexts + nodeTexts,
                )
                if (systemDecision is BlockDecision.Block) {
                    val appLabel = resolveAppLabel(packageName)
                    cacheManager.hideGrayscaleOverlay()
                    cacheManager.logBlockAsync(packageName, appLabel, systemDecision.reason.name)
                    launchBlockOverlay(packageName, appLabel, systemDecision)
                    return
                }
            }

            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                when (val decision = cacheManager.evaluatePackage(packageName)) {
                    is BlockDecision.Block -> {
                        val appLabel = resolveAppLabel(packageName)
                        cacheManager.hideGrayscaleOverlay()
                        cacheManager.logBlockAsync(packageName, appLabel, decision.reason.name)
                        launchBlockOverlay(packageName, appLabel, decision)
                        return
                    }
                    is BlockDecision.Allow -> {
                        cacheManager.syncGrayscaleDimOverlay(packageName)
                    }
                }
            }

            // Target-package short-video Feed Blocking (debounced, depth-limited, cooled down, fail-open).
            if (isTargetFeedPackage && !cacheManager.hasActiveUnlockPass(packageName)) {
                val rootNode = try {
                    rootInActiveWindow
                } catch (_: Throwable) {
                    null
                }
                when (feedBlocker.inspectAccessibilityWindow(packageName, rootNode, smartConfig)) {
                    is FeedBlockCheckOutcome.Block -> {
                        try {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        } catch (_: Throwable) {
                            // Fail open if system refuses global action.
                        }
                        showFeedBlockedToast()
                        val appLabel = resolveAppLabel(packageName)
                        cacheManager.logBlockAsync(packageName, appLabel, BlockReason.IN_APP_FEED.name)
                        return
                    }
                    FeedBlockCheckOutcome.Allow,
                    FeedBlockCheckOutcome.Debounced,
                    FeedBlockCheckOutcome.CooldownActive -> Unit
                }
            }

            // Browser URL & keyword blocking (unchanged existing behavior).
            if (isBrowserPackage) {
                val nowMs = SystemClock.elapsedRealtime()
                val inCooldown = packageName == lastBrowserBlockedPackage &&
                    (nowMs - lastBrowserBlockedMs < IN_APP_POST_BLOCK_COOLDOWN_MS)
                if (!inCooldown) {
                    when (val browserDecision = cacheManager.evaluateBrowserContent(packageName, rootInActiveWindow)) {
                        is BlockDecision.Block -> {
                            lastBrowserBlockedPackage = packageName
                            lastBrowserBlockedMs = nowMs
                            val appLabel = resolveAppLabel(packageName)
                            cacheManager.logBlockAsync(packageName, appLabel, browserDecision.reason.name)
                            launchBlockOverlay(packageName, appLabel, browserDecision)
                        }
                        is BlockDecision.Allow -> Unit
                    }
                }
            }
        } catch (_: Throwable) {
            // Fail-safe: never crash the AccessibilityService under any OS/window state exception.
        }
    }

    private fun showFeedBlockedToast() {
        mainHandler.post {
            try {
                Toast.makeText(
                    applicationContext,
                    applicationContext.getString(R.string.feed_blocked_toast),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (_: Throwable) {
                // Ignore toast failure in headless/restricted states.
            }
        }
    }

    override fun onInterrupt() {
        runCatching { entryPoint.rulesCacheManager().hideGrayscaleOverlay() }
    }

    override fun onDestroy() {
        _isConnected.value = false
        runCatching { entryPoint.rulesCacheManager().hideGrayscaleOverlay() }
        super.onDestroy()
    }

    private fun resolveAppLabel(packageName: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrElse {
            runCatching {
                @Suppress("DEPRECATION")
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            }.getOrDefault(packageName)
        }
    }

    private fun launchBlockOverlay(
        packageName: String,
        appLabel: String,
        decision: BlockDecision.Block,
    ) {
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(BlockActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockActivity.EXTRA_APP_LABEL, appLabel)
            putExtra(BlockActivity.EXTRA_BLOCK_TITLE, decision.title)
            putExtra(BlockActivity.EXTRA_BLOCK_DETAIL, decision.detail)
            putExtra(BlockActivity.EXTRA_BLOCK_REASON, decision.reason.name)
        }
        startActivity(intent)
    }

    companion object {
        val FEED_TARGET_PACKAGES: Set<String> = FeedBlocker.TARGET_PACKAGES

        private const val DEBOUNCE_WINDOW_MS = 350L
        private const val SYSTEM_GUARD_DEBOUNCE_MS = 250L
        private const val BROWSER_CONTENT_DEBOUNCE_MS = 400L
        private const val IN_APP_POST_BLOCK_COOLDOWN_MS = 1_800L

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }
}
