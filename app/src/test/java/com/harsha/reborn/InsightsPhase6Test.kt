package com.harsha.reborn

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.harsha.reborn.core.data.local.BlockEventEntity
import com.harsha.reborn.core.data.local.RebornDatabase
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.local.UnlockPassLogEntity
import com.harsha.reborn.core.data.model.UnlockPassLog
import com.harsha.reborn.core.engine.InsightsCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InsightsPhase6Test {

    private lateinit var context: Context
    private lateinit var db: RebornDatabase
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RebornDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = SettingsRepository(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `calculator computes today screen time, sorted top apps, and excludes protected packages`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 20)
        val todayNoonMs = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val yesterdayMs = today.minusDays(1).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()

        val snapshot = InsightsCalculator.buildDashboardSnapshot(
            today = today,
            zoneId = zone,
            usageAccessGranted = true,
            todayMinutesByPackage = mapOf(
                "com.example" to 50,
                "com.instagram.android" to 72,
                "com.google.android.youtube" to 38,
                "com.reddit.frontpage" to 15,
            ),
            dailyScreenTimeMinutesByDate = mapOf(
                today.minusDays(2) to 90,
                today.minusDays(1) to 110,
                today to 125,
            ),
            appLabelsByPackage = mapOf(
                "com.instagram.android" to "Instagram",
                "com.google.android.youtube" to "YouTube",
            ),
            excludedPackages = setOf("com.example"),
            blockEvents = listOf(
                BlockEventEntity(packageName = "com.instagram.android", appLabel = "Instagram", reason = "QUICK_BLOCK", timestampEpochMs = todayNoonMs),
                BlockEventEntity(packageName = "com.instagram.android", appLabel = "Instagram", reason = "QUICK_BLOCK", timestampEpochMs = todayNoonMs + 1000L),
                BlockEventEntity(packageName = "com.google.android.youtube", appLabel = "YouTube", reason = "SCHEDULE", timestampEpochMs = yesterdayMs),
            ),
            unlockPasses = listOf(
                UnlockPassLog(
                    id = 1L,
                    packageName = "com.instagram.android",
                    appLabel = "Instagram",
                    reason = "Reply to friend",
                    durationMinutes = 5,
                    grantedAtEpochMs = todayNoonMs + 5000L,
                    expiresAtEpochMs = todayNoonMs + 305_000L,
                )
            ),
            persistedRebornDatesIso = emptySet(),
            persistedBestStreak = 0,
            hasAnyActiveProtectionToday = true,
        )

        assertEquals(125, snapshot.todayScreenTimeMinutes)
        assertEquals(2, snapshot.blockedTodayCount)
        assertEquals(1, snapshot.unlockAttemptsTodayCount)
        assertEquals(3, snapshot.topAppsToday.size)
        assertEquals("Instagram", snapshot.topAppsToday[0].appLabel)
        assertEquals(72, snapshot.topAppsToday[0].minutesToday)
        assertEquals("YouTube", snapshot.topAppsToday[1].appLabel)
        assertEquals("Frontpage", snapshot.topAppsToday[2].appLabel)
        assertEquals(7, snapshot.weeklyPoints.size)
        assertEquals(today.toString(), snapshot.weeklyPoints.last().dateIso)
        assertEquals(125, snapshot.weeklyPoints.last().screenTimeMinutes)
        assertEquals(3, snapshot.totalWeeklyBlockedCount)
        assertEquals(1, snapshot.totalWeeklyUnlockAttemptsCount)
        assertEquals((90 + 110 + 125) / 3, snapshot.averageDailyScreenTimeMinutes)
    }

    @Test
    fun `gentle phoenix streak counts consecutive reborn days and never shames after a break`() {
        val today = LocalDate.of(2026, 11, 20)

        // Consecutive 4-day streak ending today
        val fourDayStreak = InsightsCalculator.computePhoenixStreak(
            today = today,
            rebornDates = setOf(
                today,
                today.minusDays(1),
                today.minusDays(2),
                today.minusDays(3),
            ),
            persistedBestStreak = 2,
            resistedThisWeek = 8,
            unlockAttemptsThisWeek = 2,
        )
        assertEquals(4, fourDayStreak.daysReborn)
        assertEquals(4, fourDayStreak.bestStreak)
        assertTrue(fourDayStreak.isTodayReborn)
        assertTrue(fourDayStreak.encouragingTitle.contains("4 days reborn"))

        // Streak ending yesterday stays intact until end of today
        val activeFromYesterday = InsightsCalculator.computePhoenixStreak(
            today = today,
            rebornDates = setOf(today.minusDays(1), today.minusDays(2)),
            persistedBestStreak = 2,
            resistedThisWeek = 3,
            unlockAttemptsThisWeek = 0,
        )
        assertEquals(2, activeFromYesterday.daysReborn)
        assertFalse(activeFromYesterday.isTodayReborn)

        // Gap resets smoothly to 0 while keeping bestStreak and encouraging gently
        val afterBreak = InsightsCalculator.computePhoenixStreak(
            today = today,
            rebornDates = setOf(today.minusDays(3), today.minusDays(4)),
            persistedBestStreak = 6,
            resistedThisWeek = 2,
            unlockAttemptsThisWeek = 1,
        )
        assertEquals(0, afterBreak.daysReborn)
        assertEquals(6, afterBreak.bestStreak)
        assertTrue(afterBreak.encouragingTitle.contains("Begin again"))
    }

    @Test
    fun `dao weekly queries and settings reborn dates persistence work together`() = runBlocking {
        val now = System.currentTimeMillis()
        val tenDaysAgo = now - 10L * 86_400_000L
        val sevenDaysCutoff = now - 6L * 86_400_000L

        val rulesDao = db.blockingRulesDao()
        rulesDao.insertBlockEvent(BlockEventEntity(packageName = "com.old.app", appLabel = "Old", reason = "QUICK_BLOCK", timestampEpochMs = tenDaysAgo))
        rulesDao.insertBlockEvent(BlockEventEntity(packageName = "com.recent.app", appLabel = "Recent", reason = "QUICK_BLOCK", timestampEpochMs = now - 3_600_000L))

        val frictionDao = db.frictionDao()
        frictionDao.insertUnlockPass(
            UnlockPassLogEntity(
                packageName = "com.recent.app",
                appLabel = "Recent",
                reason = "Verify login code",
                durationMinutes = 5,
                grantedAtEpochMs = now - 1_800_000L,
                expiresAtEpochMs = now - 1_500_000L,
            )
        )

        val recentEvents = rulesDao.observeBlockEventsSince(sevenDaysCutoff).first()
        assertEquals(1, recentEvents.size)
        assertEquals("com.recent.app", recentEvents.first().packageName)

        val recentPasses = frictionDao.observeUnlockPassesSince(sevenDaysCutoff).first()
        assertEquals(1, recentPasses.size)

        val todayIso = LocalDate.now().toString()
        settingsRepository.recordMindfulDay(todayIso, currentStreak = 3)
        val prefs = settingsRepository.preferencesFlow.first()
        assertTrue(todayIso in prefs.rebornActiveDatesIso)
        assertEquals(3, prefs.bestStreakDays)
    }
}
