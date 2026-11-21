package com.harsha.reborn

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.harsha.reborn.core.data.local.QuickBlockEntity
import com.harsha.reborn.core.data.local.RebornDatabase
import com.harsha.reborn.core.data.local.ScheduleProfileEntity
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.model.ActiveUnlockPass
import com.harsha.reborn.core.data.model.AppUsageSnapshot
import com.harsha.reborn.core.data.model.BlockDecision
import com.harsha.reborn.core.data.model.BlockReason
import com.harsha.reborn.core.data.model.BlockingRulesSnapshot
import com.harsha.reborn.core.data.model.DelayedDisableTargetType
import com.harsha.reborn.core.data.model.QuickBlockRule
import com.harsha.reborn.core.data.repository.FrictionRepository
import com.harsha.reborn.core.data.repository.GrantPassResult
import com.harsha.reborn.core.engine.RuleEvaluator
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
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FrictionPhase3Test {

    private lateinit var context: Context
    private lateinit var db: RebornDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var frictionRepository: FrictionRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RebornDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = SettingsRepository(context)
        frictionRepository = FrictionRepository(
            frictionDao = db.frictionDao(),
            blockingRulesDao = db.blockingRulesDao(),
            settingsRepository = settingsRepository,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `active unlock pass allows blocked app until expiration then re-blocks`() {
        val snapshot = BlockingRulesSnapshot(
            quickBlock = QuickBlockRule(
                packageNames = setOf("com.instagram.android"),
                startTimeEpochMs = 1_000L,
                endTimeEpochMs = 600_000L,
                isActive = true,
            )
        )
        val pass = ActiveUnlockPass(
            packageName = "com.instagram.android",
            appLabel = "Instagram",
            reason = "Reply to organizer",
            grantedAtEpochMs = 10_000L,
            expiresAtEpochMs = 310_000L,
        )
        val monday10am = LocalDateTime.of(2026, 11, 16, 10, 0)

        val duringPass = RuleEvaluator.evaluate(
            packageName = "com.instagram.android",
            snapshot = snapshot,
            usageSnapshot = AppUsageSnapshot(),
            protectedPackages = setOf("com.harsha.reborn"),
            launchablePackages = setOf("com.instagram.android"),
            nowEpochMs = 200_000L,
            localDateTime = monday10am,
            activeUnlockPasses = mapOf("com.instagram.android" to pass),
        )
        assertEquals(BlockDecision.Allow, duringPass)

        val afterExpiry = RuleEvaluator.evaluate(
            packageName = "com.instagram.android",
            snapshot = snapshot,
            usageSnapshot = AppUsageSnapshot(),
            protectedPackages = setOf("com.harsha.reborn"),
            launchablePackages = setOf("com.instagram.android"),
            nowEpochMs = 310_000L,
            localDateTime = monday10am,
            activeUnlockPasses = mapOf("com.instagram.android" to pass),
        )
        assertTrue(afterExpiry is BlockDecision.Block && afterExpiry.reason == BlockReason.QUICK_BLOCK)
    }

    @Test
    fun `grantUnlockPass validates reason length and enforces daily cap`() = runBlocking {
        frictionRepository.clearUnlockLogs()
        frictionRepository.setDailyUnlockPassCap(2)

        val invalid = frictionRepository.grantUnlockPass(
            packageName = "com.instagram.android",
            appLabel = "Instagram",
            reason = "hey",
            durationMinutes = 5,
        )
        assertEquals(GrantPassResult.InvalidReason, invalid)

        val firstPass = frictionRepository.grantUnlockPass(
            packageName = "com.instagram.android",
            appLabel = "Instagram",
            reason = "Check DM from sister",
            durationMinutes = 5,
        )
        assertTrue(firstPass is GrantPassResult.Granted)

        val secondPass = frictionRepository.grantUnlockPass(
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            reason = "Watch tutorial for repair",
            durationMinutes = 10,
        )
        assertTrue(secondPass is GrantPassResult.Granted)

        val cappedPass = frictionRepository.grantUnlockPass(
            packageName = "com.instagram.android",
            appLabel = "Instagram",
            reason = "Scroll feed",
            durationMinutes = 15,
        )
        assertEquals(GrantPassResult.DailyCapReached, cappedPass)

        val todayLogs = frictionRepository.observeUnlockLogsToday().first()
        assertEquals(2, todayLogs.size)
        assertEquals(2, frictionRepository.activePassesFlow.first().size)
    }

    @Test
    fun `delayed disable waits until due timestamp and supports cancellation`() = runBlocking {
        db.blockingRulesDao().upsertQuickBlock(
            QuickBlockEntity(
                id = 1L,
                packageNamesCsv = "com.instagram.android",
                startTimeEpochMs = 1_000L,
                endTimeEpochMs = System.currentTimeMillis() + 3_600_000L,
                isActive = true,
            )
        )
        val scheduleId = db.blockingRulesDao().upsertSchedule(
            ScheduleProfileEntity(
                name = "Work",
                daysOfWeekCsv = "1,2,3,4,5",
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 17 * 60,
                packageNamesCsv = "com.instagram.android",
                isEnabled = true,
            )
        )

        val quickReq = frictionRepository.requestDelayedDisable(
            targetType = DelayedDisableTargetType.QUICK_BLOCK,
            targetId = 1L,
            targetLabel = "Quick Block",
            delayMinutes = 10,
        )
        val scheduleReq = frictionRepository.requestDelayedDisable(
            targetType = DelayedDisableTargetType.SCHEDULE,
            targetId = scheduleId,
            targetLabel = "Work Schedule",
            delayMinutes = 10,
        )

        // Cancel the schedule disable request; only Quick Block remains pending
        frictionRepository.cancelPendingDisable(scheduleReq.id)

        // Before executeAfterEpochMs: Quick Block must remain active
        val executedEarly = frictionRepository.executeDueDelayedDisables(quickReq.executeAfterEpochMs - 1_000L)
        assertEquals(0, executedEarly)
        assertTrue(db.blockingRulesDao().observeQuickBlock().first()?.isActive == true)

        // At/after executeAfterEpochMs: Quick Block is deactivated, Work schedule stays enabled
        val executedDue = frictionRepository.executeDueDelayedDisables(quickReq.executeAfterEpochMs + 1_000L)
        assertEquals(1, executedDue)
        assertFalse(db.blockingRulesDao().observeQuickBlock().first()?.isActive == true)
        assertTrue(db.blockingRulesDao().observeSchedules().first().first { it.id == scheduleId }.isEnabled)
        assertTrue(frictionRepository.pendingDisablesFlow.first().isEmpty())
    }
}
