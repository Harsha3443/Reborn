package com.harsha.reborn.core.data.repository

import androidx.room.withTransaction
import com.example.BuildConfig
import com.harsha.reborn.core.data.local.AllowlistPackageEntity
import com.harsha.reborn.core.data.local.BlockingRulesDao
import com.harsha.reborn.core.data.local.BrowserKeywordRuleEntity
import com.harsha.reborn.core.data.local.ConditionalRuleEntity
import com.harsha.reborn.core.data.local.DailyTimeLimitEntity
import com.harsha.reborn.core.data.local.LaunchLimitEntity
import com.harsha.reborn.core.data.local.RebornDatabase
import com.harsha.reborn.core.data.local.ScheduleProfileEntity
import com.harsha.reborn.core.data.local.SelectedAppDao
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.model.BackupAllowlistPackage
import com.harsha.reborn.core.data.model.BackupBrowserKeywordRule
import com.harsha.reborn.core.data.model.BackupConditionalRule
import com.harsha.reborn.core.data.model.BackupDailyTimeLimit
import com.harsha.reborn.core.data.model.BackupLaunchLimit
import com.harsha.reborn.core.data.model.BackupPreferencesPayload
import com.harsha.reborn.core.data.model.BackupRestoreResult
import com.harsha.reborn.core.data.model.BackupScheduleProfile
import com.harsha.reborn.core.data.model.BackupSelectedApp
import com.harsha.reborn.core.data.model.ChargerCondition
import com.harsha.reborn.core.data.model.DailyLimitAfterAction
import com.harsha.reborn.core.data.model.FrictionSettings
import com.harsha.reborn.core.data.model.RebornBackupDocument
import com.harsha.reborn.core.data.model.SelectedAppEntity
import com.harsha.reborn.core.data.model.SmartRulesConfig
import com.harsha.reborn.core.data.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports and restores all Reborn settings, selected apps, schedules, limits, and smart rules
 * locally as JSON via Android's Storage Access Framework (zero network permissions).
 */
@Singleton
class BackupRepository @Inject constructor(
    private val database: RebornDatabase,
    private val blockingRulesDao: BlockingRulesDao,
    private val selectedAppDao: SelectedAppDao,
    private val blockingRulesRepository: BlockingRulesRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportBackupJson(): String {
        val prefs = settingsRepository.preferencesFlow.first()
        val rules = blockingRulesRepository.rulesSnapshotFlow.first()
        val allowlist = blockingRulesRepository.allowlistEntitiesFlow.first()
        val selectedApps = selectedAppDao.getSelectedAppsOnce()

        val document = RebornBackupDocument(
            schemaVersion = RebornBackupDocument.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMs = System.currentTimeMillis(),
            appVersionName = BuildConfig.VERSION_NAME,
            preferences = BackupPreferencesPayload(
                themeMode = prefs.themeMode.name,
                dynamicColorEnabled = prefs.dynamicColorEnabled,
                allowlistModeEnabled = prefs.allowlistModeEnabled,
                pauseSeconds = prefs.frictionSettings.pauseSeconds,
                dailyUnlockPassCap = prefs.frictionSettings.dailyUnlockPassCap,
                delayedDisableEnabled = prefs.frictionSettings.delayedDisableEnabled,
                delayedDisableMinutes = prefs.frictionSettings.delayedDisableMinutes,
                inAppFeedBlockingEnabled = prefs.smartRulesConfig.inAppFeedBlockingEnabled,
                enabledFeedProfileIds = prefs.smartRulesConfig.enabledFeedProfileIds.sorted(),
                allowReelsFromDms = prefs.smartRulesConfig.allowReelsFromDms,
                customFeedSelectorsJson = prefs.smartRulesConfig.customFeedSelectorsJson,
                browserBlockingEnabled = prefs.smartRulesConfig.browserBlockingEnabled,
                grayscaleDimOnLimitEnabled = prefs.smartRulesConfig.grayscaleDimOnLimitEnabled,
                rebornActiveDatesIso = prefs.rebornActiveDatesIso.sorted(),
                bestStreakDays = prefs.bestStreakDays,
            ),
            selectedApps = selectedApps.map {
                BackupSelectedApp(
                    packageName = it.packageName,
                    appLabel = it.appLabel,
                    categoryName = it.categoryName,
                )
            },
            schedules = rules.schedules.map {
                BackupScheduleProfile(
                    name = it.name,
                    daysOfWeek = it.daysOfWeek.sorted(),
                    startMinuteOfDay = it.startMinuteOfDay,
                    endMinuteOfDay = it.endMinuteOfDay,
                    packageNames = it.packageNames.sorted(),
                    isEnabled = it.isEnabled,
                )
            },
            dailyTimeLimits = rules.dailyTimeLimits.map {
                BackupDailyTimeLimit(
                    name = it.name,
                    packageNames = it.packageNames.sorted(),
                    dailyLimitMinutes = it.dailyLimitMinutes,
                    afterLimitAction = it.afterLimitAction.name,
                    isEnabled = it.isEnabled,
                )
            },
            launchLimits = rules.launchLimits.map {
                BackupLaunchLimit(
                    packageName = it.packageName,
                    appLabel = it.appLabel,
                    maxLaunchesPerDay = it.maxLaunchesPerDay,
                    isEnabled = it.isEnabled,
                )
            },
            allowlistPackages = allowlist.map {
                BackupAllowlistPackage(
                    packageName = it.packageName,
                    appLabel = it.appLabel,
                )
            },
            conditionalRules = rules.conditionalRules.map {
                BackupConditionalRule(
                    name = it.name,
                    packageNames = it.packageNames.sorted(),
                    requireWifi = it.requireWifi,
                    wifiSsid = it.wifiSsid,
                    chargerCondition = it.chargerCondition.name,
                    useTimeWindow = it.useTimeWindow,
                    startMinuteOfDay = it.startMinuteOfDay,
                    endMinuteOfDay = it.endMinuteOfDay,
                    useLocation = it.useLocation,
                    locationLabel = it.locationLabel,
                    locationLat = it.locationLat,
                    locationLon = it.locationLon,
                    locationRadiusMeters = it.locationRadiusMeters,
                    isEnabled = it.isEnabled,
                )
            },
            browserKeywordRules = rules.browserKeywordRules.map {
                BackupBrowserKeywordRule(
                    pattern = it.pattern,
                    isDomain = it.isDomain,
                    isEnabled = it.isEnabled,
                )
            },
        )
        return json.encodeToString(RebornBackupDocument.serializer(), document)
    }

    suspend fun importBackupJson(rawJson: String): BackupRestoreResult {
        val prefs = settingsRepository.preferencesFlow.first()
        if (prefs.strictModeConfig.isEffectivelyLocked(System.currentTimeMillis())) {
            return BackupRestoreResult.BlockedByStrictMode
        }

        val document = runCatching {
            json.decodeFromString(RebornBackupDocument.serializer(), rawJson)
        }.getOrElse { err ->
            return BackupRestoreResult.InvalidBackup(err.message ?: "Unrecognized JSON format")
        }

        if (document.schemaVersion !in 1..RebornBackupDocument.CURRENT_SCHEMA_VERSION) {
            return BackupRestoreResult.InvalidBackup(
                "Unsupported backup version ${document.schemaVersion}"
            )
        }

        database.withTransaction {
            selectedAppDao.clearAll()
            val selectedEntities = document.selectedApps
                .filter { it.packageName.isNotBlank() }
                .map {
                    SelectedAppEntity(
                        packageName = it.packageName.trim(),
                        appLabel = it.appLabel.ifBlank { it.packageName },
                        categoryName = it.categoryName,
                    )
                }
            if (selectedEntities.isNotEmpty()) {
                selectedAppDao.upsertApps(selectedEntities)
            }

            blockingRulesDao.clearSchedules()
            document.schedules.forEach { schedule ->
                blockingRulesDao.upsertSchedule(
                    ScheduleProfileEntity(
                        name = schedule.name.trim().ifEmpty { "Focus Schedule" },
                        daysOfWeekCsv = schedule.daysOfWeek.distinct().sorted().joinToString(","),
                        startMinuteOfDay = schedule.startMinuteOfDay.coerceIn(0, 1439),
                        endMinuteOfDay = schedule.endMinuteOfDay.coerceIn(0, 1439),
                        packageNamesCsv = schedule.packageNames.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
                        isEnabled = schedule.isEnabled,
                    )
                )
            }

            blockingRulesDao.clearDailyTimeLimits()
            document.dailyTimeLimits
                .filter { it.packageNames.isNotEmpty() && it.dailyLimitMinutes > 0 }
                .forEach { limit ->
                    val afterAction = runCatching {
                        DailyLimitAfterAction.valueOf(limit.afterLimitAction)
                    }.getOrDefault(DailyLimitAfterAction.BLOCK_APP)
                    blockingRulesDao.upsertDailyTimeLimit(
                        DailyTimeLimitEntity(
                            name = limit.name.trim().ifEmpty { "Daily Limit" },
                            packageNamesCsv = limit.packageNames.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
                            dailyLimitMinutes = limit.dailyLimitMinutes.coerceAtLeast(1),
                            afterLimitAction = afterAction.name,
                            isEnabled = limit.isEnabled,
                        )
                    )
                }

            blockingRulesDao.clearLaunchLimits()
            document.launchLimits
                .filter { it.packageName.isNotBlank() && it.maxLaunchesPerDay > 0 }
                .forEach { launch ->
                    blockingRulesDao.upsertLaunchLimit(
                        LaunchLimitEntity(
                            packageName = launch.packageName.trim(),
                            appLabel = launch.appLabel.ifBlank { launch.packageName },
                            maxLaunchesPerDay = launch.maxLaunchesPerDay.coerceAtLeast(1),
                            isEnabled = launch.isEnabled,
                        )
                    )
                }

            blockingRulesDao.clearAllowlistPackages()
            document.allowlistPackages
                .filter { it.packageName.isNotBlank() }
                .forEach { allowed ->
                    blockingRulesDao.upsertAllowlistPackage(
                        AllowlistPackageEntity(
                            packageName = allowed.packageName.trim(),
                            appLabel = allowed.appLabel.ifBlank { allowed.packageName },
                        )
                    )
                }

            blockingRulesDao.clearConditionalRules()
            document.conditionalRules
                .filter { it.packageNames.isNotEmpty() }
                .forEach { rule ->
                    val charger = runCatching { ChargerCondition.valueOf(rule.chargerCondition) }
                        .getOrDefault(ChargerCondition.ANY)
                    blockingRulesDao.upsertConditionalRule(
                        ConditionalRuleEntity(
                            name = rule.name.trim().ifEmpty { "Smart Condition" },
                            packageNamesCsv = rule.packageNames.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
                            requireWifi = rule.requireWifi,
                            wifiSsid = rule.wifiSsid.trim(),
                            chargerCondition = charger.name,
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

            blockingRulesDao.clearBrowserKeywordRules()
            document.browserKeywordRules
                .filter { it.pattern.trim().length >= 2 }
                .forEach { keyword ->
                    blockingRulesDao.upsertBrowserKeywordRule(
                        BrowserKeywordRuleEntity(
                            pattern = keyword.pattern.trim(),
                            isDomain = keyword.isDomain,
                            isEnabled = keyword.isEnabled,
                        )
                    )
                }
        }

        val payload = document.preferences
        val parsedTheme = runCatching { ThemeMode.valueOf(payload.themeMode) }
            .getOrDefault(ThemeMode.DARK)
        val profileIds = payload.enabledFeedProfileIds.toSet()
            .ifEmpty { SmartRulesConfig.DEFAULT_FEED_PROFILE_IDS }
        settingsRepository.applyBackupPreferences(
            themeMode = parsedTheme,
            dynamicColorEnabled = payload.dynamicColorEnabled,
            allowlistModeEnabled = payload.allowlistModeEnabled,
            frictionSettings = FrictionSettings(
                pauseSeconds = payload.pauseSeconds.coerceIn(5, 30),
                dailyUnlockPassCap = payload.dailyUnlockPassCap.coerceIn(0, 10),
                delayedDisableEnabled = payload.delayedDisableEnabled,
                delayedDisableMinutes = payload.delayedDisableMinutes.coerceIn(1, 30),
            ),
            smartRulesConfig = SmartRulesConfig(
                inAppFeedBlockingEnabled = payload.inAppFeedBlockingEnabled,
                enabledFeedProfileIds = profileIds,
                allowReelsFromDms = payload.allowReelsFromDms,
                customFeedSelectorsJson = payload.customFeedSelectorsJson,
                browserBlockingEnabled = payload.browserBlockingEnabled,
                grayscaleDimOnLimitEnabled = payload.grayscaleDimOnLimitEnabled,
            ),
            rebornActiveDatesIso = payload.rebornActiveDatesIso.toSet(),
            bestStreakDays = payload.bestStreakDays,
        )

        val totalRules = document.schedules.size +
            document.dailyTimeLimits.size +
            document.launchLimits.size +
            document.conditionalRules.size +
            document.browserKeywordRules.size

        return BackupRestoreResult.Success(
            restoredAppsCount = document.selectedApps.size,
            restoredRulesCount = totalRules,
        )
    }
}
