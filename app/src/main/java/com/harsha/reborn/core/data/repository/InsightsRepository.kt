package com.harsha.reborn.core.data.repository

import androidx.compose.ui.graphics.ImageBitmap
import com.harsha.reborn.core.data.local.BlockEventEntity
import com.harsha.reborn.core.data.local.BlockingRulesDao
import com.harsha.reborn.core.data.local.FrictionDao
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.local.UserPreferences
import com.harsha.reborn.core.data.model.BlockingRulesSnapshot
import com.harsha.reborn.core.data.model.InsightsDashboardSnapshot
import com.harsha.reborn.core.data.model.UnlockPassLog
import com.harsha.reborn.core.engine.InsightsCalculator
import com.harsha.reborn.core.engine.PermissionHealthManager
import com.harsha.reborn.core.engine.PermissionHealthSnapshot
import com.harsha.reborn.core.engine.ProtectedPackagesProvider
import com.harsha.reborn.core.engine.RebornPermissionType
import com.harsha.reborn.core.engine.UsageStatsTracker
import com.harsha.reborn.core.engine.WeeklyUsageBreakdown
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightsRepository @Inject constructor(
    private val blockingRulesDao: BlockingRulesDao,
    private val frictionDao: FrictionDao,
    private val blockingRulesRepository: BlockingRulesRepository,
    private val settingsRepository: SettingsRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val usageStatsTracker: UsageStatsTracker,
    private val permissionHealthManager: PermissionHealthManager,
    private val protectedPackagesProvider: ProtectedPackagesProvider,
) {
    private val refreshTrigger = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val weeklyUsageAndLabelsFlow: Flow<Pair<WeeklyUsageBreakdown, Map<String, String>>> =
        combine(
            refreshTrigger,
            permissionHealthManager.healthSnapshot.map { it.hasUsageAccess() },
        ) { trigger, hasAccess -> trigger to hasAccess }
            .flatMapLatest {
                flow {
                    val protected = protectedPackagesProvider.getProtectedPackages()
                    val breakdown = usageStatsTracker.queryWeeklyUsage(
                        days = 7,
                        excludedPackages = protected,
                    )
                    val labels = installedAppsRepository.getLaunchableApps()
                        .associate { it.packageName to it.label }
                    emit(breakdown to labels)
                }
            }

    private data class RulesAndPrefsSlice(
        val rules: BlockingRulesSnapshot,
        val prefs: UserPreferences,
        val health: PermissionHealthSnapshot,
    )

    private val rulesAndPrefsFlow: Flow<RulesAndPrefsSlice> = combine(
        blockingRulesRepository.rulesSnapshotFlow,
        settingsRepository.preferencesFlow,
        permissionHealthManager.healthSnapshot,
    ) { rules, prefs, health ->
        RulesAndPrefsSlice(rules = rules, prefs = prefs, health = health)
    }

    fun observeDashboardSnapshot(): Flow<InsightsDashboardSnapshot> {
        val zone = ZoneId.systemDefault()
        val sevenDaysStartMs = LocalDate.now(zone)
            .minusDays(6L)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        val unlockLogsFlow: Flow<List<UnlockPassLog>> = frictionDao.observeUnlockPassesSince(sevenDaysStartMs)
            .map { entities ->
                entities.map { entity ->
                    UnlockPassLog(
                        id = entity.id,
                        packageName = entity.packageName,
                        appLabel = entity.appLabel,
                        reason = entity.reason,
                        durationMinutes = entity.durationMinutes,
                        grantedAtEpochMs = entity.grantedAtEpochMs,
                        expiresAtEpochMs = entity.expiresAtEpochMs,
                    )
                }
            }

        return combine(
            weeklyUsageAndLabelsFlow,
            blockingRulesDao.observeBlockEventsSince(sevenDaysStartMs),
            unlockLogsFlow,
            rulesAndPrefsFlow,
        ) { (weeklyUsage, appLabels), blockEvents, unlockPasses, rulesPrefs ->
            val currentZone = ZoneId.systemDefault()
            val today = LocalDate.now(currentZone)
            val excluded = protectedPackagesProvider.getProtectedPackages()
            val hasProtectionToday = hasAnyActiveRule(rulesPrefs.rules, rulesPrefs.prefs)

            val snapshot = InsightsCalculator.buildDashboardSnapshot(
                today = today,
                zoneId = currentZone,
                usageAccessGranted = rulesPrefs.health.hasUsageAccess(),
                todayMinutesByPackage = weeklyUsage.todayMinutesByPackage,
                dailyScreenTimeMinutesByDate = weeklyUsage.dailyTotalMinutesByDate,
                appLabelsByPackage = appLabels,
                excludedPackages = excluded,
                blockEvents = blockEvents,
                unlockPasses = unlockPasses,
                persistedRebornDatesIso = rulesPrefs.prefs.rebornActiveDatesIso,
                persistedBestStreak = rulesPrefs.prefs.bestStreakDays,
                hasAnyActiveProtectionToday = hasProtectionToday,
            )
            snapshot
        }
    }

    suspend fun syncTodayStreakIfNeeded(snapshot: InsightsDashboardSnapshot) {
        val todayIso = LocalDate.now().toString()
        if (snapshot.streak.isTodayReborn) {
            settingsRepository.recordMindfulDay(
                dateIso = todayIso,
                currentStreak = snapshot.streak.daysReborn,
            )
        } else if (snapshot.streak.bestStreak > 0) {
            settingsRepository.updateBestStreakDays(snapshot.streak.bestStreak)
        }
    }

    fun refreshUsageStats() {
        permissionHealthManager.refresh()
        refreshTrigger.value = System.currentTimeMillis()
    }

    suspend fun loadIcon(packageName: String): ImageBitmap? =
        installedAppsRepository.loadAppIcon(packageName)

    private fun hasAnyActiveRule(rules: BlockingRulesSnapshot, prefs: UserPreferences): Boolean {
        val nowMs = System.currentTimeMillis()
        val quickActive = rules.quickBlock?.let { it.isActive && nowMs < it.endTimeEpochMs } == true
        return quickActive ||
            rules.schedules.any { it.isEnabled && it.packageNames.isNotEmpty() } ||
            rules.dailyTimeLimits.any { it.isEnabled } ||
            rules.launchLimits.any { it.isEnabled } ||
            rules.allowlist.isEnabled ||
            rules.conditionalRules.any { it.isEnabled } ||
            (rules.smartRulesConfig.inAppFeedBlockingEnabled &&
                rules.smartRulesConfig.enabledFeedProfileIds.isNotEmpty()) ||
            (rules.smartRulesConfig.browserBlockingEnabled &&
                rules.browserKeywordRules.any { it.isEnabled }) ||
            prefs.strictModeConfig.isEffectivelyLocked(nowMs)
    }

    private fun PermissionHealthSnapshot.hasUsageAccess(): Boolean =
        items.any { it.type == RebornPermissionType.USAGE_ACCESS && it.isGranted }
}
