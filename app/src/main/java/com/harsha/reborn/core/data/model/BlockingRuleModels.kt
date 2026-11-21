package com.harsha.reborn.core.data.model

enum class BlockReason {
    QUICK_BLOCK,
    SCHEDULE,
    DAILY_TIME_LIMIT,
    LAUNCH_LIMIT,
    ALLOWLIST,
    STRICT_SYSTEM_GUARD,
    CONDITIONAL_RULE,
    IN_APP_FEED,
    BROWSER_KEYWORD,
}

enum class DailyLimitAfterAction {
    BLOCK_APP,
    GRAYSCALE_DIM,
}

data class QuickBlockRule(
    val id: Long = 1L,
    val packageNames: Set<String> = emptySet(),
    val startTimeEpochMs: Long = 0L,
    val endTimeEpochMs: Long = 0L,
    val isActive: Boolean = false,
)

data class ScheduleProfile(
    val id: Long = 0L,
    val name: String,
    val daysOfWeek: Set<Int>, // 1 = Monday .. 7 = Sunday (java.time.DayOfWeek)
    val startMinuteOfDay: Int, // 0..1439
    val endMinuteOfDay: Int, // 0..1439 (may be < startMinuteOfDay for overnight)
    val packageNames: Set<String>,
    val isEnabled: Boolean = true,
)

data class DailyTimeLimitRule(
    val id: Long = 0L,
    val name: String,
    val packageNames: Set<String>,
    val dailyLimitMinutes: Int,
    val afterLimitAction: DailyLimitAfterAction = DailyLimitAfterAction.BLOCK_APP,
    val isEnabled: Boolean = true,
)

data class LaunchLimitRule(
    val id: Long = 0L,
    val packageName: String,
    val appLabel: String,
    val maxLaunchesPerDay: Int,
    val isEnabled: Boolean = true,
)

data class AllowlistConfig(
    val isEnabled: Boolean = false,
    val allowedPackages: Set<String> = emptySet(),
)

data class BlockingRulesSnapshot(
    val quickBlock: QuickBlockRule? = null,
    val schedules: List<ScheduleProfile> = emptyList(),
    val dailyTimeLimits: List<DailyTimeLimitRule> = emptyList(),
    val launchLimits: List<LaunchLimitRule> = emptyList(),
    val allowlist: AllowlistConfig = AllowlistConfig(),
    val conditionalRules: List<ConditionalRule> = emptyList(),
    val browserKeywordRules: List<BrowserKeywordRule> = emptyList(),
    val smartRulesConfig: SmartRulesConfig = SmartRulesConfig(),
) {
    fun hasAnyPotentiallyActiveRule(nowEpochMs: Long): Boolean {
        val quickActive = quickBlock != null && quickBlock.isActive && nowEpochMs < quickBlock.endTimeEpochMs
        val scheduleActive = schedules.any { it.isEnabled && it.packageNames.isNotEmpty() && it.daysOfWeek.isNotEmpty() }
        val timeLimitActive = dailyTimeLimits.any { it.isEnabled && it.packageNames.isNotEmpty() }
        val launchLimitActive = launchLimits.any { it.isEnabled }
        val conditionalActive = conditionalRules.any { it.isEnabled && it.packageNames.isNotEmpty() && it.hasAnyCondition() }
        val feedActive = smartRulesConfig.inAppFeedBlockingEnabled && smartRulesConfig.enabledFeedProfileIds.isNotEmpty()
        val browserActive = smartRulesConfig.browserBlockingEnabled && browserKeywordRules.any { it.isEnabled }
        return quickActive ||
            scheduleActive ||
            timeLimitActive ||
            launchLimitActive ||
            allowlist.isEnabled ||
            conditionalActive ||
            feedActive ||
            browserActive
    }
}

data class AppUsageSnapshot(
    val dailyMinutesByPackage: Map<String, Int> = emptyMap(),
    val dailyLaunchesByPackage: Map<String, Int> = emptyMap(),
    val capturedAtEpochMs: Long = 0L,
)

sealed interface BlockDecision {
    data object Allow : BlockDecision
    data class Block(
        val reason: BlockReason,
        val title: String,
        val detail: String,
    ) : BlockDecision
}
