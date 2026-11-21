package com.harsha.reborn.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockingRulesDao {
    // Quick Block
    @Query("SELECT * FROM quick_block_sessions WHERE id = 1 LIMIT 1")
    fun observeQuickBlock(): Flow<QuickBlockEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuickBlock(entity: QuickBlockEntity)

    @Query("UPDATE quick_block_sessions SET isActive = 0 WHERE id = 1")
    suspend fun deactivateQuickBlock()

    // Schedule Profiles
    @Query("SELECT * FROM schedule_profiles ORDER BY id ASC")
    fun observeSchedules(): Flow<List<ScheduleProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(entity: ScheduleProfileEntity): Long

    @Query("UPDATE schedule_profiles SET isEnabled = :enabled WHERE id = :id")
    suspend fun setScheduleEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM schedule_profiles WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    @Query("SELECT COUNT(*) FROM schedule_profiles")
    suspend fun getScheduleCount(): Int

    // Daily Time Limits
    @Query("SELECT * FROM daily_time_limits ORDER BY id ASC")
    fun observeDailyTimeLimits(): Flow<List<DailyTimeLimitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyTimeLimit(entity: DailyTimeLimitEntity): Long

    @Query("UPDATE daily_time_limits SET isEnabled = :enabled WHERE id = :id")
    suspend fun setDailyTimeLimitEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM daily_time_limits WHERE id = :id")
    suspend fun deleteDailyTimeLimit(id: Long)

    // Launch Limits
    @Query("SELECT * FROM launch_limits ORDER BY appLabel ASC")
    fun observeLaunchLimits(): Flow<List<LaunchLimitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLaunchLimit(entity: LaunchLimitEntity): Long

    @Query("UPDATE launch_limits SET isEnabled = :enabled WHERE id = :id")
    suspend fun setLaunchLimitEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM launch_limits WHERE id = :id")
    suspend fun deleteLaunchLimit(id: Long)

    // Allowlist
    @Query("SELECT * FROM allowlist_packages ORDER BY appLabel ASC")
    fun observeAllowlistPackages(): Flow<List<AllowlistPackageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllowlistPackage(entity: AllowlistPackageEntity)

    @Query("DELETE FROM allowlist_packages WHERE packageName = :packageName")
    suspend fun deleteAllowlistPackage(packageName: String)

    // Conditional Rules
    @Query("SELECT * FROM conditional_rules ORDER BY id ASC")
    fun observeConditionalRules(): Flow<List<ConditionalRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConditionalRule(entity: ConditionalRuleEntity): Long

    @Query("UPDATE conditional_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setConditionalRuleEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM conditional_rules WHERE id = :id")
    suspend fun deleteConditionalRule(id: Long)

    // Browser URL & Keyword Rules
    @Query("SELECT * FROM browser_keyword_rules ORDER BY id DESC")
    fun observeBrowserKeywordRules(): Flow<List<BrowserKeywordRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBrowserKeywordRule(entity: BrowserKeywordRuleEntity): Long

    @Query("UPDATE browser_keyword_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setBrowserKeywordRuleEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM browser_keyword_rules WHERE id = :id")
    suspend fun deleteBrowserKeywordRule(id: Long)

    // Block Events (local-only audit log)
    @Insert
    suspend fun insertBlockEvent(entity: BlockEventEntity)

    @Query("SELECT COUNT(*) FROM block_events WHERE timestampEpochMs >= :sinceEpochMs")
    fun observeBlockedCountSince(sinceEpochMs: Long): Flow<Int>

    @Query("SELECT * FROM block_events WHERE timestampEpochMs >= :sinceEpochMs ORDER BY timestampEpochMs DESC")
    fun observeBlockEventsSince(sinceEpochMs: Long): Flow<List<BlockEventEntity>>

    @Query("DELETE FROM schedule_profiles")
    suspend fun clearSchedules()

    @Query("DELETE FROM daily_time_limits")
    suspend fun clearDailyTimeLimits()

    @Query("DELETE FROM launch_limits")
    suspend fun clearLaunchLimits()

    @Query("DELETE FROM allowlist_packages")
    suspend fun clearAllowlistPackages()

    @Query("DELETE FROM conditional_rules")
    suspend fun clearConditionalRules()

    @Query("DELETE FROM browser_keyword_rules")
    suspend fun clearBrowserKeywordRules()
}
