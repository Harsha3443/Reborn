package com.harsha.reborn

import com.harsha.reborn.core.data.model.AllowlistConfig
import com.harsha.reborn.core.data.model.AppUsageSnapshot
import com.harsha.reborn.core.data.model.BlockDecision
import com.harsha.reborn.core.data.model.BlockReason
import com.harsha.reborn.core.data.model.BlockingRulesSnapshot
import com.harsha.reborn.core.data.model.DailyTimeLimitRule
import com.harsha.reborn.core.data.model.LaunchLimitRule
import com.harsha.reborn.core.data.model.QuickBlockRule
import com.harsha.reborn.core.data.model.ScheduleProfile
import com.harsha.reborn.core.engine.RuleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RuleEvaluatorTest {

    private val protected = setOf(
        "com.harsha.reborn",
        "com.google.android.dialer",
        "com.android.emergency",
        "com.google.android.apps.nexuslauncher",
    )
    private val launchables = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.whatsapp",
        "com.slack",
    )
    private val monday10am = LocalDateTime.of(2026, 11, 16, 10, 0) // Monday

    @Test
    fun `protected packages are never blocked even under quick block or allowlist`() {
        val snapshot = BlockingRulesSnapshot(
            quickBlock = QuickBlockRule(
                packageNames = setOf("com.google.android.dialer", "com.harsha.reborn"),
                startTimeEpochMs = 1000L,
                endTimeEpochMs = 99999L,
                isActive = true,
            ),
            allowlist = AllowlistConfig(isEnabled = true, allowedPackages = emptySet()),
        )

        protected.forEach { pkg ->
            val decision = RuleEvaluator.evaluate(
                packageName = pkg,
                snapshot = snapshot,
                usageSnapshot = AppUsageSnapshot(),
                protectedPackages = protected,
                launchablePackages = launchables,
                nowEpochMs = 5000L,
                localDateTime = monday10am,
            )
            assertEquals(BlockDecision.Allow, decision)
        }
    }

    @Test
    fun `quick block blocks target app while active and allows once expired`() {
        val snapshot = BlockingRulesSnapshot(
            quickBlock = QuickBlockRule(
                packageNames = setOf("com.instagram.android"),
                startTimeEpochMs = 1_000L,
                endTimeEpochMs = 61_000L,
                isActive = true,
            ),
        )

        val activeDecision = RuleEvaluator.evaluate(
            packageName = "com.instagram.android",
            snapshot = snapshot,
            usageSnapshot = AppUsageSnapshot(),
            protectedPackages = protected,
            launchablePackages = launchables,
            nowEpochMs = 30_000L,
            localDateTime = monday10am,
        )
        assertTrue(activeDecision is BlockDecision.Block && activeDecision.reason == BlockReason.QUICK_BLOCK)

        val expiredDecision = RuleEvaluator.evaluate(
            packageName = "com.instagram.android",
            snapshot = snapshot,
            usageSnapshot = AppUsageSnapshot(),
            protectedPackages = protected,
            launchablePackages = launchables,
            nowEpochMs = 61_000L,
            localDateTime = monday10am,
        )
        assertEquals(BlockDecision.Allow, expiredDecision)
    }

    @Test
    fun `same-day schedule blocks inside window and ignores disabled profiles`() {
        val workSchedule = ScheduleProfile(
            id = 1L,
            name = "Work",
            daysOfWeek = setOf(1, 2, 3, 4, 5), // Mon-Fri
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            packageNames = setOf("com.google.android.youtube"),
            isEnabled = true,
        )
        val snapshot = BlockingRulesSnapshot(schedules = listOf(workSchedule))

        val inside = RuleEvaluator.evaluate(
            packageName = "com.google.android.youtube",
            snapshot = snapshot,
            usageSnapshot = AppUsageSnapshot(),
            protectedPackages = protected,
            launchablePackages = launchables,
            nowEpochMs = 0L,
            localDateTime = monday10am,
        )
        assertTrue(inside is BlockDecision.Block && inside.reason == BlockReason.SCHEDULE)

        val saturday10am = LocalDateTime.of(2026, 11, 21, 10, 0) // Saturday
        val weekend = RuleEvaluator.evaluate(
            packageName = "com.google.android.youtube",
            snapshot = snapshot,
            usageSnapshot = AppUsageSnapshot(),
            protectedPackages = protected,
            launchablePackages = launchables,
            nowEpochMs = 0L,
            localDateTime = saturday10am,
        )
        assertEquals(BlockDecision.Allow, weekend)

        val disabledSnapshot = BlockingRulesSnapshot(
            schedules = listOf(workSchedule.copy(isEnabled = false)),
        )
        val disabled = RuleEvaluator.evaluate(
            packageName = "com.google.android.youtube",
            snapshot = disabledSnapshot,
            usageSnapshot = AppUsageSnapshot(),
            protectedPackages = protected,
            launchablePackages = launchables,
            nowEpochMs = 0L,
            localDateTime = monday10am,
        )
        assertEquals(BlockDecision.Allow, disabled)
    }

    @Test
    fun `overnight schedule handles midnight wrap-around and previous-day carryover`() {
        val sleepSchedule = ScheduleProfile(
            id = 2L,
            name = "Sleep",
            daysOfWeek = setOf(1), // Monday night 22:30 -> Tuesday morning 07:00
            startMinuteOfDay = 22 * 60 + 30,
            endMinuteOfDay = 7 * 60,
            packageNames = setOf("com.instagram.android"),
            isEnabled = true,
        )
        val snapshot = BlockingRulesSnapshot(schedules = listOf(sleepSchedule))

        // Monday 23:00 -> blocked
        val monNight = LocalDateTime.of(2026, 11, 16, 23, 0)
        assertTrue(
            RuleEvaluator.evaluate(
                "com.instagram.android", snapshot, AppUsageSnapshot(), protected, launchables, 0L, monNight
            ) is BlockDecision.Block
        )

        // Tuesday 06:30 (carried over from Monday) -> blocked
        val tueEarlyMorning = LocalDateTime.of(2026, 11, 17, 6, 30)
        assertTrue(
            RuleEvaluator.evaluate(
                "com.instagram.android", snapshot, AppUsageSnapshot(), protected, launchables, 0L, tueEarlyMorning
            ) is BlockDecision.Block
        )

        // Tuesday 07:00 -> allowed
        val tueWakeup = LocalDateTime.of(2026, 11, 17, 7, 0)
        assertEquals(
            BlockDecision.Allow,
            RuleEvaluator.evaluate(
                "com.instagram.android", snapshot, AppUsageSnapshot(), protected, launchables, 0L, tueWakeup
            ),
        )
    }

    @Test
    fun `daily time limits sum group usage and block once limit reached`() {
        val groupLimit = DailyTimeLimitRule(
            id = 1L,
            name = "Social Group",
            packageNames = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
            dailyLimitMinutes = 30,
            isEnabled = true,
        )
        val snapshot = BlockingRulesSnapshot(dailyTimeLimits = listOf(groupLimit))

        val underUsage = AppUsageSnapshot(
            dailyMinutesByPackage = mapOf("com.instagram.android" to 15, "com.zhiliaoapp.musically" to 10),
        )
        assertEquals(
            BlockDecision.Allow,
            RuleEvaluator.evaluate(
                "com.instagram.android", snapshot, underUsage, protected, launchables, 0L, monday10am
            ),
        )

        val reachedUsage = AppUsageSnapshot(
            dailyMinutesByPackage = mapOf("com.instagram.android" to 18, "com.zhiliaoapp.musically" to 12),
        )
        val blocked = RuleEvaluator.evaluate(
            "com.instagram.android", snapshot, reachedUsage, protected, launchables, 0L, monday10am
        )
        assertTrue(blocked is BlockDecision.Block && blocked.reason == BlockReason.DAILY_TIME_LIMIT)
    }

    @Test
    fun `launch limits allow up to max opens and block the next open`() {
        val launchRule = LaunchLimitRule(
            id = 1L,
            packageName = "com.instagram.android",
            appLabel = "Instagram",
            maxLaunchesPerDay = 5,
            isEnabled = true,
        )
        val snapshot = BlockingRulesSnapshot(launchLimits = listOf(launchRule))

        val fifthOpen = AppUsageSnapshot(dailyLaunchesByPackage = mapOf("com.instagram.android" to 5))
        assertEquals(
            BlockDecision.Allow,
            RuleEvaluator.evaluate(
                "com.instagram.android", snapshot, fifthOpen, protected, launchables, 0L, monday10am
            ),
        )

        val sixthOpen = AppUsageSnapshot(dailyLaunchesByPackage = mapOf("com.instagram.android" to 6))
        val blocked = RuleEvaluator.evaluate(
            "com.instagram.android", snapshot, sixthOpen, protected, launchables, 0L, monday10am
        )
        assertTrue(blocked is BlockDecision.Block && blocked.reason == BlockReason.LAUNCH_LIMIT)
    }

    @Test
    fun `allowlist mode blocks non-allowed launchables and allows explicitly allowed apps`() {
        val snapshot = BlockingRulesSnapshot(
            allowlist = AllowlistConfig(
                isEnabled = true,
                allowedPackages = setOf("com.whatsapp", "com.slack"),
            ),
        )

        assertEquals(
            BlockDecision.Allow,
            RuleEvaluator.evaluate(
                "com.whatsapp", snapshot, AppUsageSnapshot(), protected, launchables, 0L, monday10am
            ),
        )

        val blocked = RuleEvaluator.evaluate(
            "com.instagram.android", snapshot, AppUsageSnapshot(), protected, launchables, 0L, monday10am
        )
        assertTrue(blocked is BlockDecision.Block && blocked.reason == BlockReason.ALLOWLIST)
    }
}
