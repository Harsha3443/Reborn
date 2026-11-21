package com.harsha.reborn.core.data.repository

import com.harsha.reborn.core.data.local.BlockingRulesDao
import com.harsha.reborn.core.data.local.FrictionDao
import com.harsha.reborn.core.data.local.PendingDisableEntity
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.local.UnlockPassLogEntity
import com.harsha.reborn.core.data.model.ActiveUnlockPass
import com.harsha.reborn.core.data.model.DelayedDisableTargetType
import com.harsha.reborn.core.data.model.FrictionSettings
import com.harsha.reborn.core.data.model.PendingDisableRequest
import com.harsha.reborn.core.data.model.UnlockPassLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GrantPassResult {
    data class Granted(val pass: ActiveUnlockPass) : GrantPassResult
    data object DailyCapReached : GrantPassResult
    data object InvalidReason : GrantPassResult
}

@Singleton
class FrictionRepository @Inject constructor(
    private val frictionDao: FrictionDao,
    private val blockingRulesDao: BlockingRulesDao,
    private val settingsRepository: SettingsRepository,
) {
    val frictionSettingsFlow: Flow<FrictionSettings> =
        settingsRepository.preferencesFlow.map { it.frictionSettings }

    val allUnlockLogsFlow: Flow<List<UnlockPassLog>> =
        frictionDao.observeAllUnlockPasses().map { list -> list.map { it.toDomain() } }

    fun observeUnlockLogsToday(): Flow<List<UnlockPassLog>> {
        val midnightMs = todayMidnightEpochMs()
        return frictionDao.observeUnlockPassesSince(midnightMs).map { list ->
            list.map { it.toDomain() }
        }
    }

    val activePassesFlow: Flow<Map<String, ActiveUnlockPass>> =
        frictionDao.observeAllUnlockPasses().map { list ->
            val now = System.currentTimeMillis()
            list.asSequence()
                .filter { it.expiresAtEpochMs > now }
                .groupBy { it.packageName }
                .mapValues { (_, entries) ->
                    val latest = entries.maxBy { it.expiresAtEpochMs }
                    ActiveUnlockPass(
                        packageName = latest.packageName,
                        appLabel = latest.appLabel,
                        reason = latest.reason,
                        grantedAtEpochMs = latest.grantedAtEpochMs,
                        expiresAtEpochMs = latest.expiresAtEpochMs,
                    )
                }
        }

    val pendingDisablesFlow: Flow<List<PendingDisableRequest>> =
        frictionDao.observePendingDisables().map { list ->
            list.mapNotNull { entity ->
                val type = runCatching { DelayedDisableTargetType.valueOf(entity.targetType) }.getOrNull()
                    ?: return@mapNotNull null
                PendingDisableRequest(
                    id = entity.id,
                    targetType = type,
                    targetId = entity.targetId,
                    targetLabel = entity.targetLabel,
                    requestedAtEpochMs = entity.requestedAtEpochMs,
                    executeAfterEpochMs = entity.executeAfterEpochMs,
                )
            }
        }

    suspend fun grantUnlockPass(
        packageName: String,
        appLabel: String,
        reason: String,
        durationMinutes: Int,
    ): GrantPassResult {
        val cleanedReason = reason.trim()
        if (cleanedReason.length < 4) {
            return GrantPassResult.InvalidReason
        }

        val settings = frictionSettingsFlow.first()
        val usedToday = frictionDao.countUnlockPassesSince(todayMidnightEpochMs())
        if (usedToday >= settings.dailyUnlockPassCap) {
            return GrantPassResult.DailyCapReached
        }

        val now = System.currentTimeMillis()
        val expiresAt = now + durationMinutes.coerceIn(1, 60) * 60_000L
        frictionDao.insertUnlockPass(
            UnlockPassLogEntity(
                packageName = packageName,
                appLabel = appLabel,
                reason = cleanedReason,
                durationMinutes = durationMinutes,
                grantedAtEpochMs = now,
                expiresAtEpochMs = expiresAt,
            )
        )
        return GrantPassResult.Granted(
            ActiveUnlockPass(
                packageName = packageName,
                appLabel = appLabel,
                reason = cleanedReason,
                grantedAtEpochMs = now,
                expiresAtEpochMs = expiresAt,
            )
        )
    }

    suspend fun requestDelayedDisable(
        targetType: DelayedDisableTargetType,
        targetId: Long,
        targetLabel: String,
        delayMinutes: Int,
    ): PendingDisableRequest {
        val existing = frictionDao.findPendingForTarget(targetType.name, targetId)
        val now = System.currentTimeMillis()
        if (existing != null && existing.executeAfterEpochMs > now) {
            return PendingDisableRequest(
                id = existing.id,
                targetType = targetType,
                targetId = targetId,
                targetLabel = existing.targetLabel,
                requestedAtEpochMs = existing.requestedAtEpochMs,
                executeAfterEpochMs = existing.executeAfterEpochMs,
            )
        }

        val executeAfter = now + delayMinutes.coerceAtLeast(1) * 60_000L
        val entity = PendingDisableEntity(
            id = existing?.id ?: 0L,
            targetType = targetType.name,
            targetId = targetId,
            targetLabel = targetLabel,
            requestedAtEpochMs = now,
            executeAfterEpochMs = executeAfter,
        )
        val rowId = frictionDao.insertPendingDisable(entity)
        return PendingDisableRequest(
            id = if (entity.id != 0L) entity.id else rowId,
            targetType = targetType,
            targetId = targetId,
            targetLabel = targetLabel,
            requestedAtEpochMs = now,
            executeAfterEpochMs = executeAfter,
        )
    }

    suspend fun cancelPendingDisable(id: Long) {
        frictionDao.deletePendingDisableById(id)
    }

    suspend fun executeDueDelayedDisables(nowEpochMs: Long = System.currentTimeMillis()): Int {
        val due = frictionDao.getDuePendingDisables(nowEpochMs)
        var executedCount = 0
        for (item in due) {
            val type = runCatching { DelayedDisableTargetType.valueOf(item.targetType) }.getOrNull()
            when (type) {
                DelayedDisableTargetType.QUICK_BLOCK -> blockingRulesDao.deactivateQuickBlock()
                DelayedDisableTargetType.SCHEDULE -> blockingRulesDao.setScheduleEnabled(item.targetId, false)
                DelayedDisableTargetType.DAILY_TIME_LIMIT -> blockingRulesDao.setDailyTimeLimitEnabled(item.targetId, false)
                DelayedDisableTargetType.LAUNCH_LIMIT -> blockingRulesDao.setLaunchLimitEnabled(item.targetId, false)
                DelayedDisableTargetType.ALLOWLIST -> settingsRepository.setAllowlistModeEnabled(false)
                DelayedDisableTargetType.CONDITIONAL_RULE -> blockingRulesDao.setConditionalRuleEnabled(item.targetId, false)
                null -> Unit
            }
            frictionDao.deletePendingDisableById(item.id)
            executedCount++
        }
        return executedCount
    }

    suspend fun setPauseSeconds(seconds: Int) = settingsRepository.setPauseSeconds(seconds)
    suspend fun setDailyUnlockPassCap(cap: Int) = settingsRepository.setDailyUnlockPassCap(cap)
    suspend fun setDelayedDisableEnabled(enabled: Boolean) = settingsRepository.setDelayedDisableEnabled(enabled)
    suspend fun setDelayedDisableMinutes(minutes: Int) = settingsRepository.setDelayedDisableMinutes(minutes)
    suspend fun clearUnlockLogs() = frictionDao.clearAllUnlockPasses()

    private fun todayMidnightEpochMs(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun UnlockPassLogEntity.toDomain() = UnlockPassLog(
        id = id,
        packageName = packageName,
        appLabel = appLabel,
        reason = reason,
        durationMinutes = durationMinutes,
        grantedAtEpochMs = grantedAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs,
    )
}
