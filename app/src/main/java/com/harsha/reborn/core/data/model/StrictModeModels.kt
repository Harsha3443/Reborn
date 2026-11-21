package com.harsha.reborn.core.data.model

enum class StrictEventType {
    ACTIVATED,
    TIMER_ENDED,
    UNLOCKED_EARLY,
    EMERGENCY_ESCAPE,
}

data class StrictModeConfig(
    val isActive: Boolean = false,
    val startedAtEpochMs: Long = 0L,
    val endsAtEpochMs: Long = 0L,
    val requirePin: Boolean = false,
    val pinHashHex: String = "",
    val pinSaltHex: String = "",
    val requireChargerConnected: Boolean = false,
    val requireTrustedContactCode: Boolean = false,
    val trustedCodeHashHex: String = "",
    val trustedCodeSaltHex: String = "",
    val blockAppSettingsAndSystemGuards: Boolean = true,
    val disallowUnlockPassesWhileActive: Boolean = true,
    val emergencyWaitMinutes: Int = 5,
) {
    fun isEffectivelyLocked(nowEpochMs: Long): Boolean =
        isActive && nowEpochMs < endsAtEpochMs

    fun hasAnyEarlyUnlockMethod(): Boolean =
        requirePin || requireChargerConnected || requireTrustedContactCode
}

data class StrictModeLogEntry(
    val id: Long,
    val eventType: StrictEventType,
    val detail: String,
    val timestampEpochMs: Long,
)
