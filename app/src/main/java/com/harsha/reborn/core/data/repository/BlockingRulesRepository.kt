package com.harsha.reborn.core.data.repository

import com.harsha.reborn.core.data.local.AllowlistPackageEntity
import com.harsha.reborn.core.data.local.BlockEventEntity
import com.harsha.reborn.core.data.local.BlockingRulesDao
import com.harsha.reborn.core.data.local.BrowserKeywordRuleEntity
import com.harsha.reborn.core.data.local.ConditionalRuleEntity
import com.harsha.reborn.core.data.local.DailyTimeLimitEntity
import com.harsha.reborn.core.data.local.LaunchLimitEntity
import com.harsha.reborn.core.data.local.QuickBlockEntity
import com.harsha.reborn.core.data.local.ScheduleProfileEntity
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.model.AllowlistConfig
import com.harsha.reborn.core.data.model.BlockingRulesSnapshot
import com.harsha.reborn.core.data.model.BrowserKeywordRule
import com.harsha.reborn.core.data.model.ChargerCondition
import com.harsha.reborn.core.data.model.ConditionalRule
import com.harsha.reborn.core.data.model.DailyLimitAfterAction
import com.harsha.reborn.core.data.model.DailyTimeLimitRule
import com.harsha.reborn.core.data.model.LaunchLimitRule
import com.harsha.reborn.core.data.model.QuickBlockRule
import com.harsha.reborn.core.data.model.ScheduleProfile
import com.harsha.reborn.core.data.model.SmartRulesConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockingRulesRepository @Inject constructor(
    private val dao: BlockingRulesDao,
    private val settingsRepository: SettingsRepository,
) {
    private val baseRulesFlow = combine(
        dao.observeQuickBlock(),
        dao.observeSchedules(),
        dao.observeDailyTimeLimits(),
        dao.observeLaunchLimits(),
    ) { quickEntity, scheduleEntities, dailyLimitEntities, launchLimitEntities ->
        BaseRulesSlice(
            quickBlock = quickEntity?.toDomain(),
            schedules = scheduleEntities.map { it.toDomain() },
            dailyTimeLimits = dailyLimitEntities.map { it.toDomain() },
            launchLimits = launchLimitEntities.map { it.toDomain() },
        )
    }

    private val smartRulesFlow = combine(
        dao.observeConditionalRules(),
        dao.observeBrowserKeywordRules(),
        settingsRepository.preferencesFlow.map { it.smartRulesConfig },
    ) { conditionalEntities, browserEntities, smartConfig ->
        SmartRulesSlice(
            conditionalRules = conditionalEntities.map { it.toDomain() },
            browserKeywordRules = browserEntities.map { it.toDomain() },
            smartRulesConfig = smartConfig,
        )
    }

    val rulesSnapshotFlow: Flow<BlockingRulesSnapshot> = combine(
        baseRulesFlow,
        combine(
            settingsRepository.preferencesFlow.map { it.allowlistModeEnabled },
            dao.observeAllowlistPackages(),
        ) { enabled, packages ->
            AllowlistConfig(
                isEnabled = enabled,
                allowedPackages = packages.map { it.packageName }.toSet(),
            )
        },
        smartRulesFlow,
    ) { base, allowlist, smart ->
        BlockingRulesSnapshot(
            quickBlock = base.quickBlock,
            schedules = base.schedules,
            dailyTimeLimits = base.dailyTimeLimits,
            launchLimits = base.launchLimits,
            allowlist = allowlist,
            conditionalRules = smart.conditionalRules,
            browserKeywordRules = smart.browserKeywordRules,
            smartRulesConfig = smart.smartRulesConfig,
        )
    }

    val allowlistEntitiesFlow: Flow<List<AllowlistPackageEntity>> = dao.observeAllowlistPackages()

    fun observeBlockedAttemptsToday(): Flow<Int> {
        val midnightMs = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return dao.observeBlockedCountSince(midnightMs)
    }

    suspend fun startQuickBlock(packageNames: Set<String>, durationMinutes: Int) {
        if (packageNames.isEmpty() || durationMinutes <= 0) return
        val now = System.currentTimeMillis()
        val end = now + durationMinutes * 60_000L
        dao.upsertQuickBlock(
            QuickBlockEntity(
                id = 1L,
                packageNamesCsv = packageNames.joinToString(","),
                startTimeEpochMs = now,
                endTimeEpochMs = end,
                isActive = true,
            )
        )
    }

    suspend fun stopQuickBlock() {
        dao.deactivateQuickBlock()
    }

    suspend fun ensureDefaultSchedulesSeeded() {
        if (dao.getScheduleCount() > 0) return
        val presets = listOf(
            ScheduleProfileEntity(
                name = "Work",
                daysOfWeekCsv = "1,2,3,4,5",
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 17 * 60,
                packageNamesCsv = "",
                isEnabled = false,
            ),
            ScheduleProfileEntity(
                name = "Study",
                daysOfWeekCsv = "1,2,3,4,5,6,7",
                startMinuteOfDay = 18 * 60,
                endMinuteOfDay = 21 * 60,
                packageNamesCsv = "",
                isEnabled = false,
            ),
            ScheduleProfileEntity(
                name = "Sleep",
                daysOfWeekCsv = "1,2,3,4,5,6,7",
                startMinuteOfDay = 22 * 60 + 30,
                endMinuteOfDay = 7 * 60,
                packageNamesCsv = "",
                isEnabled = false,
            ),
        )
        presets.forEach { dao.upsertSchedule(it) }
    }

    suspend fun saveSchedule(profile: ScheduleProfile) {
        dao.upsertSchedule(
            ScheduleProfileEntity(
                id = profile.id,
                name = profile.name.trim().ifEmpty { "Focus Schedule" },
                daysOfWeekCsv = profile.daysOfWeek.sorted().joinToString(","),
                startMinuteOfDay = profile.startMinuteOfDay.coerceIn(0, 1439),
                endMinuteOfDay = profile.endMinuteOfDay.coerceIn(0, 1439),
                packageNamesCsv = profile.packageNames.joinToString(","),
                isEnabled = profile.isEnabled,
            )
        )
    }

    suspend fun setScheduleEnabled(id: Long, enabled: Boolean) {
        dao.setScheduleEnabled(id, enabled)
    }

    suspend fun deleteSchedule(id: Long) {
        dao.deleteSchedule(id)
    }

    suspend fun saveDailyTimeLimit(rule: DailyTimeLimitRule) {
        if (rule.packageNames.isEmpty() || rule.dailyLimitMinutes <= 0) return
        dao.upsertDailyTimeLimit(
            DailyTimeLimitEntity(
                id = rule.id,
                name = rule.name.trim().ifEmpty { "Daily Limit" },
                packageNamesCsv = rule.packageNames.joinToString(","),
                dailyLimitMinutes = rule.dailyLimitMinutes,
                afterLimitAction = rule.afterLimitAction.name,
                isEnabled = rule.isEnabled,
            )
        )
    }

    suspend fun setDailyTimeLimitEnabled(id: Long, enabled: Boolean) {
        dao.setDailyTimeLimitEnabled(id, enabled)
    }

    suspend fun deleteDailyTimeLimit(id: Long) {
        dao.deleteDailyTimeLimit(id)
    }

    suspend fun saveLaunchLimit(rule: LaunchLimitRule) {
        if (rule.maxLaunchesPerDay <= 0) return
        dao.upsertLaunchLimit(
            LaunchLimitEntity(
                id = rule.id,
                packageName = rule.packageName,
                appLabel = rule.appLabel,
                maxLaunchesPerDay = rule.maxLaunchesPerDay,
                isEnabled = rule.isEnabled,
            )
        )
    }

    suspend fun setLaunchLimitEnabled(id: Long, enabled: Boolean) {
        dao.setLaunchLimitEnabled(id, enabled)
    }

    suspend fun deleteLaunchLimit(id: Long) {
        dao.deleteLaunchLimit(id)
    }

    suspend fun setAllowlistModeEnabled(enabled: Boolean) {
        settingsRepository.setAllowlistModeEnabled(enabled)
    }

    suspend fun toggleAllowlistPackage(packageName: String, appLabel: String, currentlyAllowed: Boolean) {
        if (currentlyAllowed) {
            dao.deleteAllowlistPackage(packageName)
        } else {
            dao.upsertAllowlistPackage(AllowlistPackageEntity(packageName = packageName, appLabel = appLabel))
        }
    }

    // Phase 5: Conditional rules, Browser URL/keyword rules & Smart config
    suspend fun saveConditionalRule(rule: ConditionalRule) {
        if (rule.packageNames.isEmpty() || !rule.hasAnyCondition()) return
        dao.upsertConditionalRule(
            ConditionalRuleEntity(
                id = rule.id,
                name = rule.name.trim().ifEmpty { "Smart Condition" },
                packageNamesCsv = rule.packageNames.joinToString(","),
                requireWifi = rule.requireWifi,
                wifiSsid = rule.wifiSsid.trim(),
                chargerCondition = rule.chargerCondition.name,
                useTimeWindow = rule.useTimeWindow,
                startMinuteOfDay = rule.startMinuteOfDay.coerceIn(0, 1439),
                endMinuteOfDay = rule.endMinuteOfDay.coerceIn(0, 1439),
                useLocation = rule.useLocation,
                locationLabel = rule.locationLabel.trim(),
                locationLat = rule.locationLat,
                locationLon = rule.locationLon,
                locationRadiusMeters = rule.locationRadiusMeters.coerceIn(50, 10_000),
                isEnabled = rule.isEnabled,
            )
        )
    }

    suspend fun setConditionalRuleEnabled(id: Long, enabled: Boolean) {
        dao.setConditionalRuleEnabled(id, enabled)
    }

    suspend fun deleteConditionalRule(id: Long) {
        dao.deleteConditionalRule(id)
    }

    suspend fun addBrowserKeywordRule(rawPattern: String, isDomain: Boolean) {
        val cleaned = rawPattern.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        if (cleaned.length < 2) return
        dao.upsertBrowserKeywordRule(
            BrowserKeywordRuleEntity(
                pattern = cleaned,
                isDomain = isDomain,
                isEnabled = true,
            )
        )
    }

    suspend fun setBrowserKeywordRuleEnabled(id: Long, enabled: Boolean) {
        dao.setBrowserKeywordRuleEnabled(id, enabled)
    }

    suspend fun deleteBrowserKeywordRule(id: Long) {
        dao.deleteBrowserKeywordRule(id)
    }

    suspend fun setInAppFeedBlockingEnabled(enabled: Boolean) {
        settingsRepository.setInAppFeedBlockingEnabled(enabled)
    }

    suspend fun toggleFeedProfile(profileId: String, enabled: Boolean) {
        settingsRepository.toggleFeedProfileId(profileId, enabled)
    }

    suspend fun setCustomFeedSelectorsJson(json: String) {
        settingsRepository.setCustomFeedSelectorsJson(json)
    }

    suspend fun setAllowReelsFromDms(enabled: Boolean) {
        settingsRepository.setAllowReelsFromDms(enabled)
    }

    suspend fun updateFeedBlockingState(enabled: Boolean, enabledProfileIds: Set<String>) {
        settingsRepository.updateFeedBlockingState(enabled, enabledProfileIds)
    }

    suspend fun setBrowserBlockingEnabled(enabled: Boolean) {
        settingsRepository.setBrowserBlockingEnabled(enabled)
    }

    suspend fun setGrayscaleDimOnLimitEnabled(enabled: Boolean) {
        settingsRepository.setGrayscaleDimOnLimitEnabled(enabled)
    }

    suspend fun logBlockEvent(packageName: String, appLabel: String, reason: String) {
        dao.insertBlockEvent(
            BlockEventEntity(
                packageName = packageName,
                appLabel = appLabel,
                reason = reason,
                timestampEpochMs = System.currentTimeMillis(),
            )
        )
    }

    private data class BaseRulesSlice(
        val quickBlock: QuickBlockRule?,
        val schedules: List<ScheduleProfile>,
        val dailyTimeLimits: List<DailyTimeLimitRule>,
        val launchLimits: List<LaunchLimitRule>,
    )

    private data class SmartRulesSlice(
        val conditionalRules: List<ConditionalRule>,
        val browserKeywordRules: List<BrowserKeywordRule>,
        val smartRulesConfig: SmartRulesConfig,
    )

    private fun QuickBlockEntity.toDomain() = QuickBlockRule(
        id = id,
        packageNames = parseCsv(packageNamesCsv),
        startTimeEpochMs = startTimeEpochMs,
        endTimeEpochMs = endTimeEpochMs,
        isActive = isActive,
    )

    private fun ScheduleProfileEntity.toDomain() = ScheduleProfile(
        id = id,
        name = name,
        daysOfWeek = parseCsv(daysOfWeekCsv).mapNotNull { it.toIntOrNull() }.toSet(),
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay,
        packageNames = parseCsv(packageNamesCsv),
        isEnabled = isEnabled,
    )

    private fun DailyTimeLimitEntity.toDomain() = DailyTimeLimitRule(
        id = id,
        name = name,
        packageNames = parseCsv(packageNamesCsv),
        dailyLimitMinutes = dailyLimitMinutes,
        afterLimitAction = runCatching {
            DailyLimitAfterAction.valueOf(afterLimitAction)
        }.getOrDefault(DailyLimitAfterAction.BLOCK_APP),
        isEnabled = isEnabled,
    )

    private fun LaunchLimitEntity.toDomain() = LaunchLimitRule(
        id = id,
        packageName = packageName,
        appLabel = appLabel,
        maxLaunchesPerDay = maxLaunchesPerDay,
        isEnabled = isEnabled,
    )

    private fun ConditionalRuleEntity.toDomain() = ConditionalRule(
        id = id,
        name = name,
        packageNames = parseCsv(packageNamesCsv),
        requireWifi = requireWifi,
        wifiSsid = wifiSsid,
        chargerCondition = runCatching { ChargerCondition.valueOf(chargerCondition) }
            .getOrDefault(ChargerCondition.ANY),
        useTimeWindow = useTimeWindow,
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay,
        useLocation = useLocation,
        locationLabel = locationLabel,
        locationLat = locationLat,
        locationLon = locationLon,
        locationRadiusMeters = locationRadiusMeters,
        isEnabled = isEnabled,
    )

    private fun BrowserKeywordRuleEntity.toDomain() = BrowserKeywordRule(
        id = id,
        pattern = pattern,
        isDomain = isDomain,
        isEnabled = isEnabled,
    )

    private fun parseCsv(csv: String): Set<String> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
