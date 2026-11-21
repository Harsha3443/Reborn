package com.harsha.reborn.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.harsha.reborn.core.data.model.FrictionSettings
import com.harsha.reborn.core.data.model.SmartRulesConfig
import com.harsha.reborn.core.data.model.StrictModeConfig
import com.harsha.reborn.core.data.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "reborn_settings")

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColorEnabled: Boolean = false,
    val allowlistModeEnabled: Boolean = false,
    val frictionSettings: FrictionSettings = FrictionSettings(),
    val strictModeConfig: StrictModeConfig = StrictModeConfig(),
    val smartRulesConfig: SmartRulesConfig = SmartRulesConfig(),
    val rebornActiveDatesIso: Set<String> = emptySet(),
    val bestStreakDays: Int = 0,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        val ALLOWLIST_MODE_ENABLED = booleanPreferencesKey("allowlist_mode_enabled")
        val PAUSE_SECONDS = intPreferencesKey("friction_pause_seconds")
        val DAILY_UNLOCK_PASS_CAP = intPreferencesKey("friction_daily_unlock_pass_cap")
        val DELAYED_DISABLE_ENABLED = booleanPreferencesKey("friction_delayed_disable_enabled")
        val DELAYED_DISABLE_MINUTES = intPreferencesKey("friction_delayed_disable_minutes")

        val STRICT_ACTIVE = booleanPreferencesKey("strict_active")
        val STRICT_STARTED_AT = longPreferencesKey("strict_started_at")
        val STRICT_ENDS_AT = longPreferencesKey("strict_ends_at")
        val STRICT_REQUIRE_PIN = booleanPreferencesKey("strict_require_pin")
        val STRICT_PIN_HASH = stringPreferencesKey("strict_pin_hash")
        val STRICT_PIN_SALT = stringPreferencesKey("strict_pin_salt")
        val STRICT_REQUIRE_CHARGER = booleanPreferencesKey("strict_require_charger")
        val STRICT_REQUIRE_TRUSTED = booleanPreferencesKey("strict_require_trusted")
        val STRICT_TRUSTED_HASH = stringPreferencesKey("strict_trusted_hash")
        val STRICT_TRUSTED_SALT = stringPreferencesKey("strict_trusted_salt")
        val STRICT_BLOCK_SYSTEM_GUARDS = booleanPreferencesKey("strict_block_system_guards")
        val STRICT_DISALLOW_PASSES = booleanPreferencesKey("strict_disallow_passes")
        val STRICT_EMERGENCY_WAIT_MIN = intPreferencesKey("strict_emergency_wait_min")

        val SMART_FEED_BLOCKING_ENABLED = booleanPreferencesKey("smart_feed_blocking_enabled")
        val SMART_FEED_PROFILE_IDS = stringSetPreferencesKey("smart_feed_profile_ids")
        val SMART_FEED_ALLOW_REELS_FROM_DMS = booleanPreferencesKey("smart_feed_allow_reels_from_dms")
        val SMART_FEED_CUSTOM_JSON = stringPreferencesKey("smart_feed_custom_json")
        val SMART_BROWSER_BLOCKING_ENABLED = booleanPreferencesKey("smart_browser_blocking_enabled")
        val SMART_GRAYSCALE_DIM_ON_LIMIT = booleanPreferencesKey("smart_grayscale_dim_on_limit")

        val INSIGHTS_REBORN_DATES_ISO = stringSetPreferencesKey("insights_reborn_dates_iso")
        val INSIGHTS_BEST_STREAK_DAYS = intPreferencesKey("insights_best_streak_days")
    }

    val preferencesFlow: Flow<UserPreferences> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val modeName = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
            val mode = runCatching { ThemeMode.valueOf(modeName) }.getOrDefault(ThemeMode.DARK)
            UserPreferences(
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
                themeMode = mode,
                dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: false,
                allowlistModeEnabled = prefs[Keys.ALLOWLIST_MODE_ENABLED] ?: false,
                frictionSettings = FrictionSettings(
                    pauseSeconds = (prefs[Keys.PAUSE_SECONDS] ?: 10).coerceIn(5, 30),
                    dailyUnlockPassCap = (prefs[Keys.DAILY_UNLOCK_PASS_CAP] ?: 3).coerceIn(0, 10),
                    delayedDisableEnabled = prefs[Keys.DELAYED_DISABLE_ENABLED] ?: true,
                    delayedDisableMinutes = (prefs[Keys.DELAYED_DISABLE_MINUTES] ?: 10).coerceIn(1, 30),
                ),
                strictModeConfig = StrictModeConfig(
                    isActive = prefs[Keys.STRICT_ACTIVE] ?: false,
                    startedAtEpochMs = prefs[Keys.STRICT_STARTED_AT] ?: 0L,
                    endsAtEpochMs = prefs[Keys.STRICT_ENDS_AT] ?: 0L,
                    requirePin = prefs[Keys.STRICT_REQUIRE_PIN] ?: false,
                    pinHashHex = prefs[Keys.STRICT_PIN_HASH] ?: "",
                    pinSaltHex = prefs[Keys.STRICT_PIN_SALT] ?: "",
                    requireChargerConnected = prefs[Keys.STRICT_REQUIRE_CHARGER] ?: false,
                    requireTrustedContactCode = prefs[Keys.STRICT_REQUIRE_TRUSTED] ?: false,
                    trustedCodeHashHex = prefs[Keys.STRICT_TRUSTED_HASH] ?: "",
                    trustedCodeSaltHex = prefs[Keys.STRICT_TRUSTED_SALT] ?: "",
                    blockAppSettingsAndSystemGuards = prefs[Keys.STRICT_BLOCK_SYSTEM_GUARDS] ?: true,
                    disallowUnlockPassesWhileActive = prefs[Keys.STRICT_DISALLOW_PASSES] ?: true,
                    emergencyWaitMinutes = (prefs[Keys.STRICT_EMERGENCY_WAIT_MIN] ?: 5).coerceIn(1, 30),
                ),
                smartRulesConfig = SmartRulesConfig(
                    inAppFeedBlockingEnabled = prefs[Keys.SMART_FEED_BLOCKING_ENABLED] ?: false,
                    enabledFeedProfileIds = prefs[Keys.SMART_FEED_PROFILE_IDS] ?: SmartRulesConfig.DEFAULT_FEED_PROFILE_IDS,
                    allowReelsFromDms = prefs[Keys.SMART_FEED_ALLOW_REELS_FROM_DMS] ?: false,
                    customFeedSelectorsJson = prefs[Keys.SMART_FEED_CUSTOM_JSON] ?: "",
                    browserBlockingEnabled = prefs[Keys.SMART_BROWSER_BLOCKING_ENABLED] ?: true,
                    grayscaleDimOnLimitEnabled = prefs[Keys.SMART_GRAYSCALE_DIM_ON_LIMIT] ?: false,
                ),
                rebornActiveDatesIso = prefs[Keys.INSIGHTS_REBORN_DATES_ISO] ?: emptySet(),
                bestStreakDays = (prefs[Keys.INSIGHTS_BEST_STREAK_DAYS] ?: 0).coerceAtLeast(0),
            )
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAllowlistModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ALLOWLIST_MODE_ENABLED] = enabled }
    }

    suspend fun setPauseSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.PAUSE_SECONDS] = seconds.coerceIn(5, 30) }
    }

    suspend fun setDailyUnlockPassCap(cap: Int) {
        context.settingsDataStore.edit { it[Keys.DAILY_UNLOCK_PASS_CAP] = cap.coerceIn(0, 10) }
    }

    suspend fun setDelayedDisableEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DELAYED_DISABLE_ENABLED] = enabled }
    }

    suspend fun setDelayedDisableMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.DELAYED_DISABLE_MINUTES] = minutes.coerceIn(1, 30) }
    }

    suspend fun saveStrictModeConfig(config: StrictModeConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.STRICT_ACTIVE] = config.isActive
            prefs[Keys.STRICT_STARTED_AT] = config.startedAtEpochMs
            prefs[Keys.STRICT_ENDS_AT] = config.endsAtEpochMs
            prefs[Keys.STRICT_REQUIRE_PIN] = config.requirePin
            prefs[Keys.STRICT_PIN_HASH] = config.pinHashHex
            prefs[Keys.STRICT_PIN_SALT] = config.pinSaltHex
            prefs[Keys.STRICT_REQUIRE_CHARGER] = config.requireChargerConnected
            prefs[Keys.STRICT_REQUIRE_TRUSTED] = config.requireTrustedContactCode
            prefs[Keys.STRICT_TRUSTED_HASH] = config.trustedCodeHashHex
            prefs[Keys.STRICT_TRUSTED_SALT] = config.trustedCodeSaltHex
            prefs[Keys.STRICT_BLOCK_SYSTEM_GUARDS] = config.blockAppSettingsAndSystemGuards
            prefs[Keys.STRICT_DISALLOW_PASSES] = config.disallowUnlockPassesWhileActive
            prefs[Keys.STRICT_EMERGENCY_WAIT_MIN] = config.emergencyWaitMinutes.coerceIn(1, 30)
        }
    }

    suspend fun setInAppFeedBlockingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SMART_FEED_BLOCKING_ENABLED] = enabled }
    }

    suspend fun toggleFeedProfileId(profileId: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val current = (prefs[Keys.SMART_FEED_PROFILE_IDS] ?: SmartRulesConfig.DEFAULT_FEED_PROFILE_IDS).toMutableSet()
            if (enabled) current.add(profileId) else current.remove(profileId)
            prefs[Keys.SMART_FEED_PROFILE_IDS] = current
        }
    }

    suspend fun setCustomFeedSelectorsJson(json: String) {
        context.settingsDataStore.edit { it[Keys.SMART_FEED_CUSTOM_JSON] = json.trim() }
    }

    suspend fun setAllowReelsFromDms(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SMART_FEED_ALLOW_REELS_FROM_DMS] = enabled }
    }

    suspend fun updateFeedBlockingState(enabled: Boolean, enabledProfileIds: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SMART_FEED_BLOCKING_ENABLED] = enabled
            prefs[Keys.SMART_FEED_PROFILE_IDS] = enabledProfileIds
        }
    }

    suspend fun setBrowserBlockingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SMART_BROWSER_BLOCKING_ENABLED] = enabled }
    }

    suspend fun setGrayscaleDimOnLimitEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SMART_GRAYSCALE_DIM_ON_LIMIT] = enabled }
    }

    suspend fun recordMindfulDay(dateIso: String, currentStreak: Int) {
        context.settingsDataStore.edit { prefs ->
            val updated = (prefs[Keys.INSIGHTS_REBORN_DATES_ISO] ?: emptySet()).toMutableSet()
            updated.add(dateIso)
            // Keep up to 90 recent dates locally to remain compact.
            val trimmed = if (updated.size > 90) {
                updated.sortedDescending().take(90).toSet()
            } else {
                updated
            }
            prefs[Keys.INSIGHTS_REBORN_DATES_ISO] = trimmed
            val prevBest = prefs[Keys.INSIGHTS_BEST_STREAK_DAYS] ?: 0
            if (currentStreak > prevBest) {
                prefs[Keys.INSIGHTS_BEST_STREAK_DAYS] = currentStreak
            }
        }
    }

    suspend fun updateBestStreakDays(best: Int) {
        context.settingsDataStore.edit { prefs ->
            val prev = prefs[Keys.INSIGHTS_BEST_STREAK_DAYS] ?: 0
            if (best > prev) {
                prefs[Keys.INSIGHTS_BEST_STREAK_DAYS] = best
            }
        }
    }

    suspend fun applyBackupPreferences(
        themeMode: ThemeMode,
        dynamicColorEnabled: Boolean,
        allowlistModeEnabled: Boolean,
        frictionSettings: FrictionSettings,
        smartRulesConfig: SmartRulesConfig,
        rebornActiveDatesIso: Set<String>,
        bestStreakDays: Int,
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.DYNAMIC_COLOR] = dynamicColorEnabled
            prefs[Keys.ALLOWLIST_MODE_ENABLED] = allowlistModeEnabled
            prefs[Keys.PAUSE_SECONDS] = frictionSettings.pauseSeconds.coerceIn(5, 30)
            prefs[Keys.DAILY_UNLOCK_PASS_CAP] = frictionSettings.dailyUnlockPassCap.coerceIn(0, 10)
            prefs[Keys.DELAYED_DISABLE_ENABLED] = frictionSettings.delayedDisableEnabled
            prefs[Keys.DELAYED_DISABLE_MINUTES] = frictionSettings.delayedDisableMinutes.coerceIn(1, 30)
            prefs[Keys.SMART_FEED_BLOCKING_ENABLED] = smartRulesConfig.inAppFeedBlockingEnabled
            prefs[Keys.SMART_FEED_PROFILE_IDS] = smartRulesConfig.enabledFeedProfileIds
            prefs[Keys.SMART_FEED_ALLOW_REELS_FROM_DMS] = smartRulesConfig.allowReelsFromDms
            prefs[Keys.SMART_FEED_CUSTOM_JSON] = smartRulesConfig.customFeedSelectorsJson.trim()
            prefs[Keys.SMART_BROWSER_BLOCKING_ENABLED] = smartRulesConfig.browserBlockingEnabled
            prefs[Keys.SMART_GRAYSCALE_DIM_ON_LIMIT] = smartRulesConfig.grayscaleDimOnLimitEnabled
            val mergedDates = ((prefs[Keys.INSIGHTS_REBORN_DATES_ISO] ?: emptySet()) + rebornActiveDatesIso)
                .sortedDescending()
                .take(90)
                .toSet()
            prefs[Keys.INSIGHTS_REBORN_DATES_ISO] = mergedDates
            val prevBest = prefs[Keys.INSIGHTS_BEST_STREAK_DAYS] ?: 0
            prefs[Keys.INSIGHTS_BEST_STREAK_DAYS] = maxOf(prevBest, bestStreakDays.coerceAtLeast(0))
        }
    }
}
