package com.harsha.reborn.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_block_sessions")
data class QuickBlockEntity(
    @PrimaryKey val id: Long = 1L,
    val packageNamesCsv: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val isActive: Boolean,
)

@Entity(tableName = "schedule_profiles")
data class ScheduleProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val daysOfWeekCsv: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val packageNamesCsv: String,
    val isEnabled: Boolean,
)

@Entity(tableName = "daily_time_limits")
data class DailyTimeLimitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val packageNamesCsv: String,
    val dailyLimitMinutes: Int,
    val afterLimitAction: String = "BLOCK_APP",
    val isEnabled: Boolean,
)

@Entity(tableName = "launch_limits")
data class LaunchLimitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val appLabel: String,
    val maxLaunchesPerDay: Int,
    val isEnabled: Boolean,
)

@Entity(tableName = "allowlist_packages")
data class AllowlistPackageEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
)

@Entity(tableName = "block_events")
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val appLabel: String,
    val reason: String,
    val timestampEpochMs: Long,
)

@Entity(tableName = "conditional_rules")
data class ConditionalRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val packageNamesCsv: String,
    val requireWifi: Boolean,
    val wifiSsid: String,
    val chargerCondition: String,
    val useTimeWindow: Boolean,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val useLocation: Boolean,
    val locationLabel: String,
    val locationLat: Double,
    val locationLon: Double,
    val locationRadiusMeters: Int,
    val isEnabled: Boolean,
)

@Entity(tableName = "browser_keyword_rules")
data class BrowserKeywordRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val pattern: String,
    val isDomain: Boolean,
    val isEnabled: Boolean,
)
