package com.harsha.reborn.core.data.repository

import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.local.StrictModeDao
import com.harsha.reborn.core.data.local.StrictModeLogEntity
import com.harsha.reborn.core.data.model.StrictEventType
import com.harsha.reborn.core.data.model.StrictModeConfig
import com.harsha.reborn.core.data.model.StrictModeLogEntry
import com.harsha.reborn.core.engine.StrictModeCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface StrictUnlockResult {
    data object Unlocked : StrictUnlockResult
    data object NoEarlyUnlockEnabled : StrictUnlockResult
    data object ChargerNotConnected : StrictUnlockResult
    data object InvalidPin : StrictUnlockResult
    data object InvalidTrustedCode : StrictUnlockResult
    data object InvalidEscapePhrase : StrictUnlockResult
}

@Singleton
class StrictModeRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val strictModeDao: StrictModeDao,
) {
    val strictConfigFlow: Flow<StrictModeConfig> =
        settingsRepository.preferencesFlow.map { it.strictModeConfig }

    val strictLogsFlow: Flow<List<StrictModeLogEntry>> =
        strictModeDao.observeAllLogs().map { list ->
            list.mapNotNull { entity ->
                val type = runCatching { StrictEventType.valueOf(entity.eventType) }.getOrNull()
                    ?: return@mapNotNull null
                StrictModeLogEntry(
                    id = entity.id,
                    eventType = type,
                    detail = entity.detail,
                    timestampEpochMs = entity.timestampEpochMs,
                )
            }
        }

    suspend fun isEffectivelyLocked(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        strictConfigFlow.first().isEffectivelyLocked(nowEpochMs)

    suspend fun activateStrictMode(
        durationMinutes: Int,
        requirePin: Boolean,
        rawPin: String,
        requireChargerConnected: Boolean,
        requireTrustedContactCode: Boolean,
        rawTrustedContactCode: String,
        blockAppSettingsAndSystemGuards: Boolean,
        disallowUnlockPassesWhileActive: Boolean,
        emergencyWaitMinutes: Int,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): StrictModeConfig {
        val clampedDurationMin = durationMinutes.coerceIn(1, 7 * 24 * 60)
        val pinSalt = if (requirePin && rawPin.isNotBlank()) StrictModeCrypto.generateSaltHex() else ""
        val pinHash = if (requirePin && rawPin.isNotBlank()) StrictModeCrypto.hashSecret(rawPin.trim(), pinSalt) else ""

        val normalizedCode = StrictModeCrypto.normalizeTrustedCode(rawTrustedContactCode)
        val trustedSalt = if (requireTrustedContactCode && normalizedCode.isNotEmpty()) {
            StrictModeCrypto.generateSaltHex()
        } else {
            ""
        }
        val trustedHash = if (requireTrustedContactCode && normalizedCode.isNotEmpty()) {
            StrictModeCrypto.hashSecret(normalizedCode, trustedSalt)
        } else {
            ""
        }

        val config = StrictModeConfig(
            isActive = true,
            startedAtEpochMs = nowEpochMs,
            endsAtEpochMs = nowEpochMs + clampedDurationMin * 60_000L,
            requirePin = requirePin && pinHash.isNotEmpty(),
            pinHashHex = pinHash,
            pinSaltHex = pinSalt,
            requireChargerConnected = requireChargerConnected,
            requireTrustedContactCode = requireTrustedContactCode && trustedHash.isNotEmpty(),
            trustedCodeHashHex = trustedHash,
            trustedCodeSaltHex = trustedSalt,
            blockAppSettingsAndSystemGuards = blockAppSettingsAndSystemGuards,
            disallowUnlockPassesWhileActive = disallowUnlockPassesWhileActive,
            emergencyWaitMinutes = emergencyWaitMinutes.coerceIn(1, 30),
        )
        settingsRepository.saveStrictModeConfig(config)

        val guardsSummary = buildList {
            if (config.requirePin) add("PIN")
            if (config.requireChargerConnected) add("Charger")
            if (config.requireTrustedContactCode) add("Trusted Code")
        }.ifEmpty { listOf("Duration-only") }.joinToString(" + ")

        strictModeDao.insertLog(
            StrictModeLogEntity(
                eventType = StrictEventType.ACTIVATED.name,
                detail = "Locked for ${formatDurationLabel(clampedDurationMin)} ($guardsSummary)",
                timestampEpochMs = nowEpochMs,
            )
        )
        return config
    }

    suspend fun attemptEarlyUnlock(
        pinCandidate: String,
        trustedCodeCandidate: String,
        isChargerConnected: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): StrictUnlockResult {
        val config = strictConfigFlow.first()
        if (!config.isEffectivelyLocked(nowEpochMs)) {
            deactivateInternal(config, StrictEventType.TIMER_ENDED, "Timer completed", nowEpochMs)
            return StrictUnlockResult.Unlocked
        }
        if (!config.hasAnyEarlyUnlockMethod()) {
            return StrictUnlockResult.NoEarlyUnlockEnabled
        }
        if (config.requireChargerConnected && !isChargerConnected) {
            return StrictUnlockResult.ChargerNotConnected
        }
        if (config.requirePin) {
            val ok = StrictModeCrypto.verifySecret(
                candidate = pinCandidate.trim(),
                saltHex = config.pinSaltHex,
                expectedHashHex = config.pinHashHex,
            )
            if (!ok) return StrictUnlockResult.InvalidPin
        }
        if (config.requireTrustedContactCode) {
            val normalized = StrictModeCrypto.normalizeTrustedCode(trustedCodeCandidate)
            val ok = StrictModeCrypto.verifySecret(
                candidate = normalized,
                saltHex = config.trustedCodeSaltHex,
                expectedHashHex = config.trustedCodeHashHex,
            )
            if (!ok) return StrictUnlockResult.InvalidTrustedCode
        }

        val methodsUsed = buildList {
            if (config.requirePin) add("PIN")
            if (config.requireChargerConnected) add("Charger")
            if (config.requireTrustedContactCode) add("Trusted Code")
        }.joinToString(" + ")

        deactivateInternal(
            config = config,
            eventType = StrictEventType.UNLOCKED_EARLY,
            detail = "Unlocked early via $methodsUsed",
            nowEpochMs = nowEpochMs,
        )
        return StrictUnlockResult.Unlocked
    }

    suspend fun executeEmergencyEscape(
        typedPhrase: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): StrictUnlockResult {
        if (typedPhrase.trim() != EMERGENCY_ESCAPE_PHRASE) {
            return StrictUnlockResult.InvalidEscapePhrase
        }
        val config = strictConfigFlow.first()
        if (!config.isActive) return StrictUnlockResult.Unlocked
        val remainingMin = ((config.endsAtEpochMs - nowEpochMs).coerceAtLeast(0L) / 60_000L).toInt()
        deactivateInternal(
            config = config,
            eventType = StrictEventType.EMERGENCY_ESCAPE,
            detail = "Emergency escape used with ${remainingMin}m remaining",
            nowEpochMs = nowEpochMs,
        )
        return StrictUnlockResult.Unlocked
    }

    suspend fun expireIfDue(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val config = strictConfigFlow.first()
        if (config.isActive && nowEpochMs >= config.endsAtEpochMs) {
            deactivateInternal(
                config = config,
                eventType = StrictEventType.TIMER_ENDED,
                detail = "Strict Mode timer finished naturally",
                nowEpochMs = nowEpochMs,
            )
            return true
        }
        return false
    }

    suspend fun clearLogs() {
        strictModeDao.clearAllLogs()
    }

    private suspend fun deactivateInternal(
        config: StrictModeConfig,
        eventType: StrictEventType,
        detail: String,
        nowEpochMs: Long,
    ) {
        settingsRepository.saveStrictModeConfig(
            config.copy(
                isActive = false,
                startedAtEpochMs = 0L,
                endsAtEpochMs = 0L,
                pinHashHex = "",
                pinSaltHex = "",
                trustedCodeHashHex = "",
                trustedCodeSaltHex = "",
            )
        )
        strictModeDao.insertLog(
            StrictModeLogEntity(
                eventType = eventType.name,
                detail = detail,
                timestampEpochMs = nowEpochMs,
            )
        )
    }

    private fun formatDurationLabel(minutes: Int): String {
        val hours = minutes / 60
        val remMin = minutes % 60
        return when {
            hours > 0 && remMin > 0 -> "${hours}h ${remMin}m"
            hours > 0 -> "${hours}h"
            else -> "${remMin}m"
        }
    }

    companion object {
        const val EMERGENCY_ESCAPE_PHRASE = "I CHOOSE TO END STRICT MODE EARLY"
    }
}
