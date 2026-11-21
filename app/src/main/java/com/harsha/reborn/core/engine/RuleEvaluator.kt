package com.harsha.reborn.core.engine

import com.harsha.reborn.core.data.model.ActiveUnlockPass
import com.harsha.reborn.core.data.model.AppUsageSnapshot
import com.harsha.reborn.core.data.model.BlockDecision
import com.harsha.reborn.core.data.model.BlockReason
import com.harsha.reborn.core.data.model.BlockingRulesSnapshot
import com.harsha.reborn.core.data.model.ChargerCondition
import com.harsha.reborn.core.data.model.ConditionalRule
import com.harsha.reborn.core.data.model.DailyLimitAfterAction
import com.harsha.reborn.core.data.model.DeviceConditionContext
import com.harsha.reborn.core.data.model.GrayscaleDimDecision
import com.harsha.reborn.core.data.model.ScheduleProfile
import java.time.LocalDateTime
import java.util.Locale

/**
 * Pure, deterministic rule evaluation engine.
 * Designed with zero Android framework dependencies so every rule type and edge case
 * can be thoroughly unit-tested on the JVM.
 */
object RuleEvaluator {

    fun evaluate(
        packageName: String,
        snapshot: BlockingRulesSnapshot,
        usageSnapshot: AppUsageSnapshot,
        protectedPackages: Set<String>,
        launchablePackages: Set<String>,
        nowEpochMs: Long,
        localDateTime: LocalDateTime,
        activeUnlockPasses: Map<String, ActiveUnlockPass> = emptyMap(),
        conditionContext: DeviceConditionContext = DeviceConditionContext(),
    ): BlockDecision {
        // Safety invariant: never block dialer, emergency, launcher, Settings (non-strict), or Reborn itself.
        if (packageName.isBlank() || packageName in protectedPackages) {
            return BlockDecision.Allow
        }

        // Temporary Intent-Based Unlock Pass: allow while pass is unexpired.
        val activePass = activeUnlockPasses[packageName]
        if (activePass != null && nowEpochMs < activePass.expiresAtEpochMs) {
            return BlockDecision.Allow
        }

        // 1. Quick Block
        val quickBlock = snapshot.quickBlock
        if (quickBlock != null &&
            quickBlock.isActive &&
            nowEpochMs in quickBlock.startTimeEpochMs until quickBlock.endTimeEpochMs &&
            packageName in quickBlock.packageNames
        ) {
            val remainingMin = ((quickBlock.endTimeEpochMs - nowEpochMs + 59_999L) / 60_000L).coerceAtLeast(1L)
            return BlockDecision.Block(
                reason = BlockReason.QUICK_BLOCK,
                title = "Quick Block Active",
                detail = "Paused for $remainingMin more min.",
            )
        }

        // 2. Schedules (supports both same-day and overnight wrap-around ranges)
        val matchingSchedule = snapshot.schedules.firstOrNull { profile ->
            profile.isEnabled &&
                packageName in profile.packageNames &&
                isScheduleActiveAt(profile, localDateTime)
        }
        if (matchingSchedule != null) {
            return BlockDecision.Block(
                reason = BlockReason.SCHEDULE,
                title = "${matchingSchedule.name} Schedule",
                detail = "Active ${formatMinute(matchingSchedule.startMinuteOfDay)} – ${formatMinute(matchingSchedule.endMinuteOfDay)}.",
            )
        }

        // 3. Daily Time Limits (supports hard block or grayscale dim overlay once hit)
        val useGlobalDim = snapshot.smartRulesConfig.grayscaleDimOnLimitEnabled
        for (limit in snapshot.dailyTimeLimits) {
            if (!limit.isEnabled || packageName !in limit.packageNames) continue
            val usedMinutes = limit.packageNames.sumOf { pkg ->
                usageSnapshot.dailyMinutesByPackage[pkg] ?: 0
            }
            if (usedMinutes >= limit.dailyLimitMinutes) {
                val shouldDimInstead = useGlobalDim || limit.afterLimitAction == DailyLimitAfterAction.GRAYSCALE_DIM
                if (!shouldDimInstead) {
                    return BlockDecision.Block(
                        reason = BlockReason.DAILY_TIME_LIMIT,
                        title = "Daily Limit Reached",
                        detail = "${limit.name}: $usedMinutes / ${limit.dailyLimitMinutes} min used today.",
                    )
                }
            }
        }

        // 4. Per-App Launch Limits (allows up to maxLaunchesPerDay opens; blocks the next open)
        val launchLimit = snapshot.launchLimits.firstOrNull { rule ->
            rule.isEnabled && rule.packageName == packageName
        }
        if (launchLimit != null) {
            val launchesToday = usageSnapshot.dailyLaunchesByPackage[packageName] ?: 0
            if (launchesToday > launchLimit.maxLaunchesPerDay) {
                return BlockDecision.Block(
                    reason = BlockReason.LAUNCH_LIMIT,
                    title = "Launch Limit Reached",
                    detail = "Limit of ${launchLimit.maxLaunchesPerDay} opens/day reached.",
                )
            }
        }

        // 5. Conditional Rules (Wi-Fi, charging state, time of day, optional location zone)
        val matchingConditional = snapshot.conditionalRules.firstOrNull { rule ->
            rule.isEnabled &&
                packageName in rule.packageNames &&
                doesConditionalRuleMatch(rule, localDateTime, conditionContext)
        }
        if (matchingConditional != null) {
            return BlockDecision.Block(
                reason = BlockReason.CONDITIONAL_RULE,
                title = matchingConditional.name,
                detail = buildConditionalDetail(matchingConditional),
            )
        }

        // 6. Allowlist Mode (block every launchable non-protected app not on the allowlist)
        if (snapshot.allowlist.isEnabled) {
            val isUserFacingLaunchable = launchablePackages.isEmpty() || packageName in launchablePackages
            if (isUserFacingLaunchable && packageName !in snapshot.allowlist.allowedPackages) {
                return BlockDecision.Block(
                    reason = BlockReason.ALLOWLIST,
                    title = "Allowlist Mode Active",
                    detail = "Only your allowed focus apps can open right now.",
                )
            }
        }

        return BlockDecision.Allow
    }

    fun evaluateGrayscaleDim(
        packageName: String,
        snapshot: BlockingRulesSnapshot,
        usageSnapshot: AppUsageSnapshot,
        protectedPackages: Set<String>,
    ): GrayscaleDimDecision {
        if (packageName.isBlank() || packageName in protectedPackages) {
            return GrayscaleDimDecision(shouldDim = false)
        }

        val useGlobalDim = snapshot.smartRulesConfig.grayscaleDimOnLimitEnabled
        for (limit in snapshot.dailyTimeLimits) {
            if (!limit.isEnabled || packageName !in limit.packageNames) continue
            val wantsDim = useGlobalDim || limit.afterLimitAction == DailyLimitAfterAction.GRAYSCALE_DIM
            if (!wantsDim) continue
            val usedMinutes = limit.packageNames.sumOf { pkg ->
                usageSnapshot.dailyMinutesByPackage[pkg] ?: 0
            }
            if (usedMinutes >= limit.dailyLimitMinutes) {
                return GrayscaleDimDecision(
                    shouldDim = true,
                    label = "${limit.name} (${usedMinutes}m / ${limit.dailyLimitMinutes}m)",
                )
            }
        }

        return GrayscaleDimDecision(shouldDim = false)
    }

    fun doesConditionalRuleMatch(
        rule: ConditionalRule,
        dateTime: LocalDateTime,
        context: DeviceConditionContext,
    ): Boolean {
        if (!rule.isEnabled || !rule.hasAnyCondition()) return false

        if (rule.requireWifi) {
            if (!context.isWifiConnected) return false
            val targetSsid = rule.wifiSsid.trim()
            if (targetSsid.isNotEmpty()) {
                val activeSsid = context.wifiSsid?.trim().orEmpty()
                if (!activeSsid.equals(targetSsid, ignoreCase = true)) return false
            }
        }

        when (rule.chargerCondition) {
            ChargerCondition.ANY -> Unit
            ChargerCondition.CHARGING -> if (!context.isCharging) return false
            ChargerCondition.NOT_CHARGING -> if (context.isCharging) return false
        }

        if (rule.useTimeWindow) {
            if (!isTimeWindowActive(rule.startMinuteOfDay, rule.endMinuteOfDay, dateTime)) {
                return false
            }
        }

        if (rule.useLocation) {
            val lat = context.latitude ?: return false
            val lon = context.longitude ?: return false
            val distance = OfflineLocationHelper.haversineDistanceMeters(
                lat1 = lat,
                lon1 = lon,
                lat2 = rule.locationLat,
                lon2 = rule.locationLon,
            )
            if (distance > rule.locationRadiusMeters.toDouble()) return false
        }

        return true
    }

    fun isTimeWindowActive(
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        dateTime: LocalDateTime,
    ): Boolean {
        val minuteOfDay = dateTime.hour * 60 + dateTime.minute
        return when {
            startMinuteOfDay == endMinuteOfDay -> true
            startMinuteOfDay < endMinuteOfDay -> minuteOfDay in startMinuteOfDay until endMinuteOfDay
            else -> minuteOfDay >= startMinuteOfDay || minuteOfDay < endMinuteOfDay
        }
    }

    fun isScheduleActiveAt(profile: ScheduleProfile, dateTime: LocalDateTime): Boolean {
        if (!profile.isEnabled || profile.daysOfWeek.isEmpty()) return false

        val currentDay = dateTime.dayOfWeek.value // 1 (Mon) .. 7 (Sun)
        val previousDay = dateTime.minusDays(1).dayOfWeek.value
        val minuteOfDay = dateTime.hour * 60 + dateTime.minute

        val start = profile.startMinuteOfDay
        val end = profile.endMinuteOfDay

        return when {
            start == end -> currentDay in profile.daysOfWeek
            start < end -> currentDay in profile.daysOfWeek && minuteOfDay in start until end
            else -> {
                (currentDay in profile.daysOfWeek && minuteOfDay >= start) ||
                    (previousDay in profile.daysOfWeek && minuteOfDay < end)
            }
        }
    }

    fun buildConditionalDetail(rule: ConditionalRule): String {
        val parts = buildList {
            if (rule.requireWifi) {
                add(if (rule.wifiSsid.isBlank()) "Wi-Fi connected" else "Wi-Fi: ${rule.wifiSsid}")
            }
            when (rule.chargerCondition) {
                ChargerCondition.CHARGING -> add("Charging")
                ChargerCondition.NOT_CHARGING -> add("On battery")
                ChargerCondition.ANY -> Unit
            }
            if (rule.useTimeWindow) {
                add("${formatMinute(rule.startMinuteOfDay)}–${formatMinute(rule.endMinuteOfDay)}")
            }
            if (rule.useLocation) {
                add(rule.locationLabel.ifBlank { "Within ${rule.locationRadiusMeters}m zone" })
            }
        }
        return if (parts.isEmpty()) "Condition matched." else "Triggered by: ${parts.joinToString(" • ")}."
    }

    fun formatMinute(minuteOfDay: Int): String {
        val clamped = ((minuteOfDay % 1440) + 1440) % 1440
        val hour24 = clamped / 60
        val minute = clamped % 60
        val amPm = if (hour24 < 12) "AM" else "PM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
    }
}
