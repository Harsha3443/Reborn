package com.harsha.reborn.core.data.model

enum class DelayedDisableTargetType {
    QUICK_BLOCK,
    SCHEDULE,
    DAILY_TIME_LIMIT,
    LAUNCH_LIMIT,
    ALLOWLIST,
    CONDITIONAL_RULE,
}

data class FrictionSettings(
    val pauseSeconds: Int = 10,
    val dailyUnlockPassCap: Int = 3,
    val delayedDisableEnabled: Boolean = true,
    val delayedDisableMinutes: Int = 10,
)

data class ActiveUnlockPass(
    val packageName: String,
    val appLabel: String,
    val reason: String,
    val grantedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

data class UnlockPassLog(
    val id: Long,
    val packageName: String,
    val appLabel: String,
    val reason: String,
    val durationMinutes: Int,
    val grantedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

data class PendingDisableRequest(
    val id: Long,
    val targetType: DelayedDisableTargetType,
    val targetId: Long,
    val targetLabel: String,
    val requestedAtEpochMs: Long,
    val executeAfterEpochMs: Long,
)
