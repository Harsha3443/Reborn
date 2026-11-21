package com.harsha.reborn.core.data.model

data class AppScreenTimeItem(
    val packageName: String,
    val appLabel: String,
    val minutesToday: Int,
    val shareFraction: Float,
)

data class DailyInsightPoint(
    val dateIso: String,
    val dayLabel: String,
    val screenTimeMinutes: Int,
    val blockedCount: Int,
    val unlockAttemptsCount: Int,
    val isRebornDay: Boolean,
)

data class PhoenixStreakSummary(
    val daysReborn: Int = 0,
    val bestStreak: Int = 0,
    val resistedThisWeek: Int = 0,
    val unlockAttemptsThisWeek: Int = 0,
    val encouragingTitle: String = "Begin again today",
    val encouragingSubtitle: String = "Enable a rule or pause once to kindle your first reborn day.",
    val isTodayReborn: Boolean = false,
)

data class InsightsDashboardSnapshot(
    val usageAccessGranted: Boolean = false,
    val todayScreenTimeMinutes: Int = 0,
    val blockedTodayCount: Int = 0,
    val unlockAttemptsTodayCount: Int = 0,
    val topAppsToday: List<AppScreenTimeItem> = emptyList(),
    val weeklyPoints: List<DailyInsightPoint> = emptyList(),
    val streak: PhoenixStreakSummary = PhoenixStreakSummary(),
    val averageDailyScreenTimeMinutes: Int = 0,
    val totalWeeklyBlockedCount: Int = 0,
    val totalWeeklyUnlockAttemptsCount: Int = 0,
    val recentUnlockReasons: List<UnlockPassLog> = emptyList(),
)
