package com.harsha.reborn.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RebornBackupDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtEpochMs: Long = 0L,
    val appVersionName: String = "",
    val preferences: BackupPreferencesPayload = BackupPreferencesPayload(),
    val selectedApps: List<BackupSelectedApp> = emptyList(),
    val schedules: List<BackupScheduleProfile> = emptyList(),
    val dailyTimeLimits: List<BackupDailyTimeLimit> = emptyList(),
    val launchLimits: List<BackupLaunchLimit> = emptyList(),
    val allowlistPackages: List<BackupAllowlistPackage> = emptyList(),
    val conditionalRules: List<BackupConditionalRule> = emptyList(),
    val browserKeywordRules: List<BackupBrowserKeywordRule> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupPreferencesPayload(
    val themeMode: String = ThemeMode.DARK.name,
    val dynamicColorEnabled: Boolean = false,
    val allowlistModeEnabled: Boolean = false,
    val pauseSeconds: Int = 10,
    val dailyUnlockPassCap: Int = 3,
    val delayedDisableEnabled: Boolean = true,
    val delayedDisableMinutes: Int = 10,
    val inAppFeedBlockingEnabled: Boolean = false,
    val enabledFeedProfileIds: List<String> = emptyList(),
    val allowReelsFromDms: Boolean = false,
    val customFeedSelectorsJson: String = "",
    val browserBlockingEnabled: Boolean = true,
    val grayscaleDimOnLimitEnabled: Boolean = false,
    val rebornActiveDatesIso: List<String> = emptyList(),
    val bestStreakDays: Int = 0,
)

@Serializable
data class BackupSelectedApp(
    val packageName: String,
    val appLabel: String,
    val categoryName: String = AppCategory.OTHER.name,
)

@Serializable
data class BackupScheduleProfile(
    val name: String,
    val daysOfWeek: List<Int> = emptyList(),
    val startMinuteOfDay: Int = 9 * 60,
    val endMinuteOfDay: Int = 17 * 60,
    val packageNames: List<String> = emptyList(),
    val isEnabled: Boolean = false,
)

@Serializable
data class BackupDailyTimeLimit(
    val name: String,
    val packageNames: List<String> = emptyList(),
    val dailyLimitMinutes: Int = 30,
    val afterLimitAction: String = DailyLimitAfterAction.BLOCK_APP.name,
    val isEnabled: Boolean = true,
)

@Serializable
data class BackupLaunchLimit(
    val packageName: String,
    val appLabel: String,
    val maxLaunchesPerDay: Int = 5,
    val isEnabled: Boolean = true,
)

@Serializable
data class BackupAllowlistPackage(
    val packageName: String,
    val appLabel: String,
)

@Serializable
data class BackupConditionalRule(
    val name: String,
    val packageNames: List<String> = emptyList(),
    val requireWifi: Boolean = false,
    val wifiSsid: String = "",
    val chargerCondition: String = ChargerCondition.ANY.name,
    val useTimeWindow: Boolean = false,
    val startMinuteOfDay: Int = 9 * 60,
    val endMinuteOfDay: Int = 17 * 60,
    val useLocation: Boolean = false,
    val locationLabel: String = "",
    val locationLat: Double = 0.0,
    val locationLon: Double = 0.0,
    val locationRadiusMeters: Int = 250,
    val isEnabled: Boolean = true,
)

@Serializable
data class BackupBrowserKeywordRule(
    val pattern: String,
    val isDomain: Boolean = true,
    val isEnabled: Boolean = true,
)

sealed interface BackupRestoreResult {
    data class Success(
        val restoredAppsCount: Int,
        val restoredRulesCount: Int,
    ) : BackupRestoreResult

    data object BlockedByStrictMode : BackupRestoreResult
    data class InvalidBackup(val reason: String) : BackupRestoreResult
}
