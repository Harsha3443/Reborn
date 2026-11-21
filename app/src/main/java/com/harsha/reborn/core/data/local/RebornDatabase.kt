package com.harsha.reborn.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.harsha.reborn.core.data.model.SelectedAppEntity

@Database(
    entities = [
        SelectedAppEntity::class,
        QuickBlockEntity::class,
        ScheduleProfileEntity::class,
        DailyTimeLimitEntity::class,
        LaunchLimitEntity::class,
        AllowlistPackageEntity::class,
        BlockEventEntity::class,
        UnlockPassLogEntity::class,
        PendingDisableEntity::class,
        StrictModeLogEntity::class,
        ConditionalRuleEntity::class,
        BrowserKeywordRuleEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class RebornDatabase : RoomDatabase() {
    abstract fun selectedAppDao(): SelectedAppDao
    abstract fun blockingRulesDao(): BlockingRulesDao
    abstract fun frictionDao(): FrictionDao
    abstract fun strictModeDao(): StrictModeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quick_block_sessions (
                        id INTEGER NOT NULL PRIMARY KEY,
                        packageNamesCsv TEXT NOT NULL,
                        startTimeEpochMs INTEGER NOT NULL,
                        endTimeEpochMs INTEGER NOT NULL,
                        isActive INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS schedule_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        daysOfWeekCsv TEXT NOT NULL,
                        startMinuteOfDay INTEGER NOT NULL,
                        endMinuteOfDay INTEGER NOT NULL,
                        packageNamesCsv TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_time_limits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        packageNamesCsv TEXT NOT NULL,
                        dailyLimitMinutes INTEGER NOT NULL,
                        isEnabled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS launch_limits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        appLabel TEXT NOT NULL,
                        maxLaunchesPerDay INTEGER NOT NULL,
                        isEnabled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS allowlist_packages (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        appLabel TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS block_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        appLabel TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        timestampEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS unlock_pass_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        appLabel TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        grantedAtEpochMs INTEGER NOT NULL,
                        expiresAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_disable_requests (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        targetType TEXT NOT NULL,
                        targetId INTEGER NOT NULL,
                        targetLabel TEXT NOT NULL,
                        requestedAtEpochMs INTEGER NOT NULL,
                        executeAfterEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS strict_mode_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventType TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        timestampEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE daily_time_limits
                    ADD COLUMN afterLimitAction TEXT NOT NULL DEFAULT 'BLOCK_APP'
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conditional_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        packageNamesCsv TEXT NOT NULL,
                        requireWifi INTEGER NOT NULL,
                        wifiSsid TEXT NOT NULL,
                        chargerCondition TEXT NOT NULL,
                        useTimeWindow INTEGER NOT NULL,
                        startMinuteOfDay INTEGER NOT NULL,
                        endMinuteOfDay INTEGER NOT NULL,
                        useLocation INTEGER NOT NULL,
                        locationLabel TEXT NOT NULL,
                        locationLat REAL NOT NULL,
                        locationLon REAL NOT NULL,
                        locationRadiusMeters INTEGER NOT NULL,
                        isEnabled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS browser_keyword_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        pattern TEXT NOT NULL,
                        isDomain INTEGER NOT NULL,
                        isEnabled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
