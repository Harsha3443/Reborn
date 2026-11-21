package com.harsha.reborn.core.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.harsha.reborn.core.data.model.ActiveUnlockPass
import com.harsha.reborn.core.data.model.BlockDecision
import com.harsha.reborn.core.data.model.BlockReason
import com.harsha.reborn.core.data.model.BlockingRulesSnapshot
import com.harsha.reborn.core.data.model.DeviceConditionContext
import com.harsha.reborn.core.data.model.SmartRulesConfig
import com.harsha.reborn.core.data.model.StrictModeConfig
import com.harsha.reborn.core.data.repository.BlockingRulesRepository
import com.harsha.reborn.core.data.repository.FrictionRepository
import com.harsha.reborn.core.data.repository.StrictModeRepository
import com.harsha.reborn.core.engine.browser.BrowserKeywordDetector
import com.harsha.reborn.core.engine.feed.FeedBlocker
import com.harsha.reborn.core.engine.feed.FeedSelectorCatalog
import com.harsha.reborn.core.engine.feed.FeedSelectorRegistry
import com.harsha.reborn.core.engine.feed.InAppFeedDetector
import com.harsha.reborn.core.engine.overlay.GrayscaleDimOverlayManager
import com.harsha.reborn.feature.blocking.BlockActivity
import com.harsha.reborn.feature.widget.QuickBlockWidgetProvider
import com.harsha.reborn.feature.widget.RebornQuickBlockTileService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maintains an in-memory cache of active rules, smart selectors, temporary unlock passes,
 * strict mode lock, and today's usage so AccessibilityService evaluates window transitions
 * in microseconds. Drives auto re-blocking, delayed disables, and grayscale dim overlays.
 */
@Singleton
class RulesCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rulesRepository: BlockingRulesRepository,
    private val frictionRepository: FrictionRepository,
    private val strictModeRepository: StrictModeRepository,
    private val usageStatsTracker: UsageStatsTracker,
    private val protectedPackagesProvider: ProtectedPackagesProvider,
    private val batteryStatusHelper: BatteryStatusHelper,
    private val networkWifiHelper: NetworkWifiHelper,
    private val offlineLocationHelper: OfflineLocationHelper,
    private val grayscaleDimOverlayManager: GrayscaleDimOverlayManager,
    private val feedBlocker: FeedBlocker,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _rulesSnapshot = MutableStateFlow(BlockingRulesSnapshot())
    val rulesSnapshot: StateFlow<BlockingRulesSnapshot> = _rulesSnapshot.asStateFlow()

    private val _activePasses = MutableStateFlow<Map<String, ActiveUnlockPass>>(emptyMap())
    val activePasses: StateFlow<Map<String, ActiveUnlockPass>> = _activePasses.asStateFlow()

    private val _strictConfig = MutableStateFlow(StrictModeConfig())
    val strictConfig: StateFlow<StrictModeConfig> = _strictConfig.asStateFlow()

    @Volatile
    private var feedCatalog: FeedSelectorCatalog = FeedSelectorRegistry.defaultCatalog

    @Volatile
    private var currentForegroundPackage: String? = null

    @Volatile
    private var started = false

    private var passExpiryJob: Job? = null
    private var delayedDisableJob: Job? = null
    private var strictExpiryJob: Job? = null

    fun ensureStarted() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }

        // Collect Room rules + smart settings reactively into memory
        scope.launch {
            rulesRepository.rulesSnapshotFlow.collect { snapshot ->
                _rulesSnapshot.value = snapshot
                feedCatalog = feedBlocker.loadCatalog(snapshot.smartRulesConfig.customFeedSelectorsJson)
                syncGuardServiceAndWatchdog(snapshot)
                currentForegroundPackage?.let { syncGrayscaleDimOverlay(it) }
                QuickBlockWidgetProvider.refreshAll(context)
                RebornQuickBlockTileService.requestRefresh(context)
            }
        }

        // Collect active unlock passes and schedule automatic re-blocking on expiry
        scope.launch {
            frictionRepository.activePassesFlow.collect { passes ->
                _activePasses.value = passes
                schedulePassExpiryCheck(passes)
            }
        }

        // Collect pending delayed disable requests and execute them right when their timer expires
        scope.launch {
            frictionRepository.pendingDisablesFlow.collect { pending ->
                delayedDisableJob?.cancel()
                if (pending.isEmpty()) return@collect
                val earliest = pending.minOf { it.executeAfterEpochMs }
                delayedDisableJob = scope.launch {
                    val waitMs = (earliest - System.currentTimeMillis()).coerceAtLeast(0L)
                    if (waitMs > 0L) delay(waitMs)
                    runCatching { frictionRepository.executeDueDelayedDisables() }
                }
            }
        }

        // Collect Strict Mode lock state and auto-expire when timer completes
        scope.launch {
            strictModeRepository.strictConfigFlow.collect { config ->
                _strictConfig.value = config
                syncGuardServiceAndWatchdog(_rulesSnapshot.value, config)
                QuickBlockWidgetProvider.refreshAll(context)
                RebornQuickBlockTileService.requestRefresh(context)
                strictExpiryJob?.cancel()
                if (config.isActive) {
                    val waitMs = (config.endsAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
                    strictExpiryJob = scope.launch {
                        if (waitMs > 0L) delay(waitMs)
                        runCatching { strictModeRepository.expireIfDue() }
                    }
                }
            }
        }

        // Refresh UsageStats periodically while engine is active
        scope.launch {
            while (true) {
                runCatching {
                    usageStatsTracker.refreshTodayUsage()
                    frictionRepository.executeDueDelayedDisables()
                    strictModeRepository.expireIfDue()
                    currentForegroundPackage?.let { syncGrayscaleDimOverlay(it) }
                }
                delay(15_000L)
            }
        }
    }

    fun onForegroundPackageChanged(packageName: String) {
        if (packageName != context.packageName && packageName != "com.android.systemui") {
            currentForegroundPackage = packageName
        }
    }

    fun registerActivePassImmediately(pass: ActiveUnlockPass) {
        val updated = _activePasses.value.toMutableMap()
        updated[pass.packageName] = pass
        _activePasses.value = updated
        schedulePassExpiryCheck(updated)
    }

    fun isStrictLockActive(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        _strictConfig.value.isEffectivelyLocked(nowEpochMs)

    fun isStrictSystemGuardActive(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val config = _strictConfig.value
        return config.isEffectivelyLocked(nowEpochMs) && config.blockAppSettingsAndSystemGuards
    }

    fun evaluateSystemWindow(
        packageName: String,
        className: String?,
        visibleTexts: List<String>,
    ): BlockDecision {
        if (!isStrictSystemGuardActive()) return BlockDecision.Allow
        return StrictSystemScreenDetector.evaluateSystemWindow(
            packageName = packageName,
            className = className,
            visibleTexts = visibleTexts,
        )
    }

    fun evaluatePackage(packageName: String): BlockDecision {
        val now = System.currentTimeMillis()
        val protected = protectedPackagesProvider.getProtectedPackages()
        val launchable = protectedPackagesProvider.getLaunchablePackages()

        usageStatsTracker.recordForegroundEntry(packageName, protected)

        return RuleEvaluator.evaluate(
            packageName = packageName,
            snapshot = _rulesSnapshot.value,
            usageSnapshot = usageStatsTracker.usageSnapshot.value,
            protectedPackages = protected,
            launchablePackages = launchable,
            nowEpochMs = now,
            localDateTime = LocalDateTime.now(),
            activeUnlockPasses = _activePasses.value,
            conditionContext = captureConditionContext(),
        )
    }

    fun currentSmartRulesConfig(): SmartRulesConfig = _rulesSnapshot.value.smartRulesConfig

    fun hasActiveUnlockPass(packageName: String, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val activePass = _activePasses.value[packageName] ?: return false
        return nowEpochMs < activePass.expiresAtEpochMs
    }

    fun shouldInspectBrowserContent(packageName: String): Boolean {
        val snapshot = _rulesSnapshot.value
        val smart = snapshot.smartRulesConfig
        return smart.browserBlockingEnabled &&
            snapshot.browserKeywordRules.any { it.isEnabled } &&
            BrowserKeywordDetector.isSupportedBrowser(packageName)
    }

    fun evaluateBrowserContent(
        packageName: String,
        rootNode: AccessibilityNodeInfo?,
    ): BlockDecision {
        if (hasActiveUnlockPass(packageName)) return BlockDecision.Allow
        val snapshot = _rulesSnapshot.value
        val smart = snapshot.smartRulesConfig
        if (smart.browserBlockingEnabled &&
            BrowserKeywordDetector.isSupportedBrowser(packageName) &&
            snapshot.browserKeywordRules.any { it.isEnabled }
        ) {
            val urlText = BrowserKeywordDetector.extractUrlBarText(rootNode, packageName)
            val match = BrowserKeywordDetector.matchUrlOrKeyword(urlText, snapshot.browserKeywordRules)
            if (match != null) {
                return BlockDecision.Block(
                    reason = BlockReason.BROWSER_KEYWORD,
                    title = "Website / Keyword Blocked",
                    detail = "Matched rule \"${match.rule.pattern}\" in the browser address bar.",
                )
            }
        }
        return BlockDecision.Allow
    }

    fun shouldInspectInAppContent(packageName: String): Boolean {
        val snapshot = _rulesSnapshot.value
        val smart = snapshot.smartRulesConfig
        val feedMonitored = smart.inAppFeedBlockingEnabled &&
            smart.enabledFeedProfileIds.isNotEmpty() &&
            feedCatalog.profiles.any { it.id in smart.enabledFeedProfileIds && packageName in it.packageNames }
        return feedMonitored || shouldInspectBrowserContent(packageName)
    }

    fun evaluateInAppContent(
        packageName: String,
        rootNode: AccessibilityNodeInfo?,
    ): BlockDecision {
        val now = System.currentTimeMillis()
        val activePass = _activePasses.value[packageName]
        if (activePass != null && now < activePass.expiresAtEpochMs) {
            return BlockDecision.Allow
        }

        val snapshot = _rulesSnapshot.value
        val smart = snapshot.smartRulesConfig

        // 1. Config-driven In-App Short Feed Blocker (Shorts, Reels, TikTok) — fails open
        if (smart.inAppFeedBlockingEnabled && smart.enabledFeedProfileIds.isNotEmpty()) {
            val isMonitoredApp = feedCatalog.profiles.any {
                it.id in smart.enabledFeedProfileIds && packageName in it.packageNames
            }
            if (isMonitoredApp) {
                val windowSnapshot = InAppFeedDetector.captureSnapshot(rootNode, packageName)
                val match = InAppFeedDetector.evaluateWindow(
                    snapshot = windowSnapshot,
                    catalog = feedCatalog,
                    enabledProfileIds = smart.enabledFeedProfileIds,
                )
                if (match != null) {
                    return BlockDecision.Block(
                        reason = BlockReason.IN_APP_FEED,
                        title = "${match.displayName} Blocked",
                        detail = "Short-form video feeds are blocked while the rest of this app stays usable.",
                    )
                }
            }
        }

        // 2. Browser URL & Keyword Blocker — fails open
        if (smart.browserBlockingEnabled &&
            BrowserKeywordDetector.isSupportedBrowser(packageName) &&
            snapshot.browserKeywordRules.any { it.isEnabled }
        ) {
            val urlText = BrowserKeywordDetector.extractUrlBarText(rootNode, packageName)
            val match = BrowserKeywordDetector.matchUrlOrKeyword(urlText, snapshot.browserKeywordRules)
            if (match != null) {
                return BlockDecision.Block(
                    reason = BlockReason.BROWSER_KEYWORD,
                    title = "Website / Keyword Blocked",
                    detail = "Matched rule \"${match.rule.pattern}\" in the browser address bar.",
                )
            }
        }

        return BlockDecision.Allow
    }

    fun syncGrayscaleDimOverlay(packageName: String) {
        if (packageName == "com.android.systemui") return
        val protected = protectedPackagesProvider.getProtectedPackages()
        val dimDecision = RuleEvaluator.evaluateGrayscaleDim(
            packageName = packageName,
            snapshot = _rulesSnapshot.value,
            usageSnapshot = usageStatsTracker.usageSnapshot.value,
            protectedPackages = protected,
        )
        grayscaleDimOverlayManager.updateOverlay(
            packageName = packageName,
            shouldShow = dimDecision.shouldDim,
            limitLabel = dimDecision.label,
        )
    }

    fun hideGrayscaleOverlay() {
        grayscaleDimOverlayManager.hideOverlay()
    }

    private fun captureConditionContext(): DeviceConditionContext {
        val wifi = networkWifiHelper.getWifiSnapshot()
        val charging = batteryStatusHelper.isChargerConnected()
        val coords = offlineLocationHelper.getBestLastKnownCoordinates()
        return DeviceConditionContext(
            isWifiConnected = wifi.isWifiConnected,
            wifiSsid = wifi.ssid,
            isCharging = charging,
            latitude = coords?.latitude,
            longitude = coords?.longitude,
        )
    }

    fun logBlockAsync(packageName: String, appLabel: String, reason: String) {
        scope.launch {
            runCatching { rulesRepository.logBlockEvent(packageName, appLabel, reason) }
        }
    }

    fun syncGuardServiceAndWatchdog(
        snapshot: BlockingRulesSnapshot = _rulesSnapshot.value,
        strictConfig: StrictModeConfig = _strictConfig.value,
    ) {
        val now = System.currentTimeMillis()
        val hasActive = snapshot.hasAnyPotentiallyActiveRule(now) || strictConfig.isEffectivelyLocked(now)
        if (hasActive) {
            val serviceIntent = Intent(context, RebornGuardService::class.java)
            runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
            scheduleWatchdog()
        } else {
            val serviceIntent = Intent(context, RebornGuardService::class.java)
            runCatching { context.stopService(serviceIntent) }
            cancelWatchdog()
        }
    }

    private fun schedulePassExpiryCheck(passes: Map<String, ActiveUnlockPass>) {
        passExpiryJob?.cancel()
        val now = System.currentTimeMillis()
        val activeOnly = passes.values.filter { it.expiresAtEpochMs > now }
        if (activeOnly.isEmpty()) return

        val earliest = activeOnly.minBy { it.expiresAtEpochMs }
        passExpiryJob = scope.launch {
            val waitMs = (earliest.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(50L)
            delay(waitMs)

            val currentNow = System.currentTimeMillis()
            val remaining = _activePasses.value.filterValues { it.expiresAtEpochMs > currentNow }
            _activePasses.value = remaining

            val fgPkg = currentForegroundPackage
            if (fgPkg != null && fgPkg == earliest.packageName && RebornAccessibilityService.isConnected.value) {
                val decision = evaluatePackage(fgPkg)
                if (decision is BlockDecision.Block) {
                    val label = resolveAppLabel(fgPkg)
                    logBlockAsync(fgPkg, label, decision.reason.name)
                    launchBlockOverlay(fgPkg, label, decision)
                }
            }

            if (remaining.isNotEmpty()) {
                schedulePassExpiryCheck(remaining)
            }
        }
    }

    private fun launchBlockOverlay(
        packageName: String,
        appLabel: String,
        decision: BlockDecision.Block,
    ) {
        val intent = Intent(context, BlockActivity::class.java).apply {
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
        runCatching { context.startActivity(intent) }
    }

    private fun resolveAppLabel(packageName: String): String {
        val pm = context.packageManager
        return runCatching {
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            pm.getApplicationLabel(appInfo).toString()
        }.getOrElse {
            runCatching {
                @Suppress("DEPRECATION")
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrDefault(packageName)
        }
    }

    private fun scheduleWatchdog() {
        runCatching {
            val request = PeriodicWorkRequestBuilder<GuardWatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WATCHDOG_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    private fun cancelWatchdog() {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(WATCHDOG_WORK_NAME)
        }
    }

    companion object {
        private const val WATCHDOG_WORK_NAME = "reborn_guard_watchdog"
    }
}
