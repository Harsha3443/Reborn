package com.harsha.reborn.core.engine

import com.harsha.reborn.core.data.local.BlockEventEntity
import com.harsha.reborn.core.data.model.AppScreenTimeItem
import com.harsha.reborn.core.data.model.DailyInsightPoint
import com.harsha.reborn.core.data.model.InsightsDashboardSnapshot
import com.harsha.reborn.core.data.model.PhoenixStreakSummary
import com.harsha.reborn.core.data.model.UnlockPassLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure JVM analytics engine for Phase 6 Insights.
 * Computes today's screen time, top apps, 7-day weekly breakdown, and the gentle
 * phoenix-themed "days reborn" streak without gamified currencies or leaderboards.
 */
object InsightsCalculator {

    fun buildDashboardSnapshot(
        today: LocalDate,
        zoneId: ZoneId,
        usageAccessGranted: Boolean,
        todayMinutesByPackage: Map<String, Int>,
        dailyScreenTimeMinutesByDate: Map<LocalDate, Int>,
        appLabelsByPackage: Map<String, String>,
        excludedPackages: Set<String>,
        blockEvents: List<BlockEventEntity>,
        unlockPasses: List<UnlockPassLog>,
        persistedRebornDatesIso: Set<String>,
        persistedBestStreak: Int,
        hasAnyActiveProtectionToday: Boolean,
    ): InsightsDashboardSnapshot {
        val todayAppEntries = todayMinutesByPackage
            .filter { (pkg, minutes) -> pkg !in excludedPackages && minutes > 0 }
            .entries
            .sortedByDescending { it.value }

        val todayScreenTimeMinutes = todayAppEntries.sumOf { it.value }
            .coerceAtLeast(dailyScreenTimeMinutesByDate[today] ?: 0)

        val maxAppMinutes = todayAppEntries.firstOrNull()?.value?.coerceAtLeast(1) ?: 1
        val topApps = todayAppEntries
            .take(6)
            .map { (pkg, minutes) ->
                AppScreenTimeItem(
                    packageName = pkg,
                    appLabel = appLabelsByPackage[pkg] ?: fallbackAppLabel(pkg),
                    minutesToday = minutes,
                    shareFraction = (minutes.toFloat() / maxAppMinutes.toFloat()).coerceIn(0.05f, 1f),
                )
            }

        val blockedCountsByDate = mutableMapOf<LocalDate, Int>()
        blockEvents.forEach { event ->
            val day = Instant.ofEpochMilli(event.timestampEpochMs).atZone(zoneId).toLocalDate()
            blockedCountsByDate[day] = (blockedCountsByDate[day] ?: 0) + 1
        }

        val unlockCountsByDate = mutableMapOf<LocalDate, Int>()
        unlockPasses.forEach { pass ->
            val day = Instant.ofEpochMilli(pass.grantedAtEpochMs).atZone(zoneId).toLocalDate()
            unlockCountsByDate[day] = (unlockCountsByDate[day] ?: 0) + 1
        }

        val rebornDates = mutableSetOf<LocalDate>()
        persistedRebornDatesIso.forEach { iso ->
            runCatching { LocalDate.parse(iso) }.getOrNull()?.let { rebornDates.add(it) }
        }
        blockedCountsByDate.forEach { (date, count) ->
            if (count > 0) rebornDates.add(date)
        }
        if (hasAnyActiveProtectionToday) {
            rebornDates.add(today)
        }

        val windowDates = (6 downTo 0).map { offset -> today.minusDays(offset.toLong()) }
        val weeklyPoints = windowDates.map { date ->
            val screenMinutes = if (date == today) {
                todayScreenTimeMinutes
            } else {
                (dailyScreenTimeMinutesByDate[date] ?: 0).coerceAtLeast(0)
            }
            val blocked = blockedCountsByDate[date] ?: 0
            val unlocks = unlockCountsByDate[date] ?: 0
            DailyInsightPoint(
                dateIso = date.toString(),
                dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                screenTimeMinutes = screenMinutes,
                blockedCount = blocked,
                unlockAttemptsCount = unlocks,
                isRebornDay = date in rebornDates,
            )
        }

        val totalWeeklyBlocked = weeklyPoints.sumOf { it.blockedCount }
        val totalWeeklyUnlocks = weeklyPoints.sumOf { it.unlockAttemptsCount }
        val activeUsageDays = weeklyPoints.filter { it.screenTimeMinutes > 0 }
        val averageScreenTime = if (activeUsageDays.isNotEmpty()) {
            activeUsageDays.sumOf { it.screenTimeMinutes } / activeUsageDays.size
        } else {
            0
        }

        val streak = computePhoenixStreak(
            today = today,
            rebornDates = rebornDates,
            persistedBestStreak = persistedBestStreak,
            resistedThisWeek = totalWeeklyBlocked,
            unlockAttemptsThisWeek = totalWeeklyUnlocks,
        )

        return InsightsDashboardSnapshot(
            usageAccessGranted = usageAccessGranted,
            todayScreenTimeMinutes = todayScreenTimeMinutes,
            blockedTodayCount = blockedCountsByDate[today] ?: 0,
            unlockAttemptsTodayCount = unlockCountsByDate[today] ?: 0,
            topAppsToday = topApps,
            weeklyPoints = weeklyPoints,
            streak = streak,
            averageDailyScreenTimeMinutes = averageScreenTime,
            totalWeeklyBlockedCount = totalWeeklyBlocked,
            totalWeeklyUnlockAttemptsCount = totalWeeklyUnlocks,
            recentUnlockReasons = unlockPasses
                .sortedByDescending { it.grantedAtEpochMs }
                .take(8),
        )
    }

    fun computePhoenixStreak(
        today: LocalDate,
        rebornDates: Set<LocalDate>,
        persistedBestStreak: Int,
        resistedThisWeek: Int,
        unlockAttemptsThisWeek: Int,
    ): PhoenixStreakSummary {
        val isTodayReborn = today in rebornDates
        val anchorDate = when {
            isTodayReborn -> today
            today.minusDays(1) in rebornDates -> today.minusDays(1)
            else -> null
        }

        var consecutiveDays = 0
        if (anchorDate != null) {
            var cursor: LocalDate = anchorDate
            while (cursor in rebornDates) {
                consecutiveDays++
                cursor = cursor.minusDays(1)
            }
        }

        val bestStreak = maxOf(persistedBestStreak, consecutiveDays)
        val (title, subtitle) = when {
            consecutiveDays == 0 && persistedBestStreak > 0 -> {
                "Pause. Choose. Begin again." to
                    "Every dawn starts fresh. Your previous peak was $persistedBestStreak days—kindle today with one mindful pause."
            }
            consecutiveDays == 0 -> {
                "Begin again today" to
                    "Turn on a blocking rule or complete a mindful pause to kindle your first reborn day."
            }
            consecutiveDays == 1 -> {
                "1 day reborn" to
                    "A fresh spark. Every time you pause before opening a distraction, you choose your focus."
            }
            consecutiveDays in 2..6 -> {
                "$consecutiveDays days reborn" to
                    "Rising steadily—$consecutiveDays consecutive days choosing intention over impulse."
            }
            consecutiveDays in 7..29 -> {
                "$consecutiveDays days reborn" to
                    "Full phoenix wings—a sustained rhythm of calm attention without harsh pressure."
            }
            else -> {
                "$consecutiveDays days reborn" to
                    "Bright horizon—$consecutiveDays days of steady, gentle self-direction."
            }
        }

        return PhoenixStreakSummary(
            daysReborn = consecutiveDays,
            bestStreak = bestStreak,
            resistedThisWeek = resistedThisWeek,
            unlockAttemptsThisWeek = unlockAttemptsThisWeek,
            encouragingTitle = title,
            encouragingSubtitle = subtitle,
            isTodayReborn = isTodayReborn,
        )
    }

    fun formatDurationMinutes(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        val hours = safe / 60
        val mins = safe % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    internal fun fallbackAppLabel(packageName: String): String {
        val tail = packageName.substringAfterLast('.').trim()
        return tail.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            .ifEmpty { packageName }
    }
}
