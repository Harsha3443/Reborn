package com.harsha.reborn.feature.blocking

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsha.reborn.core.data.model.BlockingRulesSnapshot
import com.harsha.reborn.core.data.model.DelayedDisableTargetType
import com.harsha.reborn.core.data.model.FrictionSettings
import com.harsha.reborn.core.data.model.InsightsDashboardSnapshot
import com.harsha.reborn.core.data.model.PendingDisableRequest
import com.harsha.reborn.core.data.model.SelectedAppEntity
import com.harsha.reborn.core.data.model.StrictModeConfig
import com.harsha.reborn.core.data.model.UnlockPassLog
import com.harsha.reborn.core.data.repository.BlockingRulesRepository
import com.harsha.reborn.core.data.repository.FrictionRepository
import com.harsha.reborn.core.data.repository.InsightsRepository
import com.harsha.reborn.core.data.repository.InstalledAppsRepository
import com.harsha.reborn.core.data.repository.SelectedAppsRepository
import com.harsha.reborn.core.data.repository.StrictModeRepository
import com.harsha.reborn.core.engine.PermissionHealthManager
import com.harsha.reborn.core.engine.PermissionHealthSnapshot
import com.harsha.reborn.core.engine.RebornAccessibilityService
import com.harsha.reborn.core.engine.RebornGuardService
import com.harsha.reborn.core.engine.RuleEvaluator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class ActiveProtectionSummary(
    val isEngineConnected: Boolean = false,
    val isGuardServiceRunning: Boolean = false,
    val quickBlockRemainingMinutes: Int? = null,
    val activeScheduleNames: List<String> = emptyList(),
    val activeLimitCount: Int = 0,
    val allowlistEnabled: Boolean = false,
    val blockedTodayCount: Int = 0,
    val pauseSeconds: Int = 10,
    val passesUsedToday: Int = 0,
    val dailyUnlockPassCap: Int = 3,
    val delayedDisableEnabled: Boolean = true,
    val delayedDisableMinutes: Int = 10,
    val pendingDisablesCount: Int = 0,
    val quickBlockPendingDisable: Boolean = false,
    val isStrictLocked: Boolean = false,
    val strictRemainingMinutes: Int = 0,
    val conditionalRuleCount: Int = 0,
    val inAppFeedBlockingEnabled: Boolean = false,
    val browserRuleCount: Int = 0,
    val grayscaleDimEnabled: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val permissionHealthManager: PermissionHealthManager,
    selectedAppsRepository: SelectedAppsRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val blockingRulesRepository: BlockingRulesRepository,
    private val frictionRepository: FrictionRepository,
    private val strictModeRepository: StrictModeRepository,
    private val insightsRepository: InsightsRepository,
) : ViewModel() {

    val healthSnapshot: StateFlow<PermissionHealthSnapshot> = permissionHealthManager.healthSnapshot

    val insightsPreview: StateFlow<InsightsDashboardSnapshot> = insightsRepository
        .observeDashboardSnapshot()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = InsightsDashboardSnapshot(),
        )

    val selectedApps: StateFlow<List<SelectedAppEntity>> = selectedAppsRepository.selectedApps
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(5_000L)
        }
    }

    private data class FrictionHomeSlice(
        val settings: FrictionSettings = FrictionSettings(),
        val todayLogs: List<UnlockPassLog> = emptyList(),
        val pendingDisables: List<PendingDisableRequest> = emptyList(),
        val strictConfig: StrictModeConfig = StrictModeConfig(),
    )

    private val frictionHomeSlice = combine(
        frictionRepository.frictionSettingsFlow,
        frictionRepository.observeUnlockLogsToday(),
        frictionRepository.pendingDisablesFlow,
        strictModeRepository.strictConfigFlow,
    ) { settings, todayLogs, pending, strict ->
        FrictionHomeSlice(
            settings = settings,
            todayLogs = todayLogs,
            pendingDisables = pending,
            strictConfig = strict,
        )
    }

    val protectionSummary: StateFlow<ActiveProtectionSummary> = combine(
        blockingRulesRepository.rulesSnapshotFlow,
        blockingRulesRepository.observeBlockedAttemptsToday(),
        RebornAccessibilityService.isConnected,
        RebornGuardService.isRunning,
        combine(frictionHomeSlice, tickerFlow) { friction, nowMs -> friction to nowMs },
    ) { snapshot: BlockingRulesSnapshot,
        blockedToday: Int,
        a11yConnected: Boolean,
        guardRunning: Boolean,
        (frictionSlice, nowMs) ->
        val quick = snapshot.quickBlock
        val quickRemaining = if (quick != null && quick.isActive && nowMs < quick.endTimeEpochMs) {
            ((quick.endTimeEpochMs - nowMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        } else {
            null
        }
        val nowLocal = LocalDateTime.now()
        val activeSchedules = snapshot.schedules
            .filter { it.isEnabled && it.packageNames.isNotEmpty() && RuleEvaluator.isScheduleActiveAt(it, nowLocal) }
            .map { it.name }
        val limitCount = snapshot.dailyTimeLimits.count { it.isEnabled } + snapshot.launchLimits.count { it.isEnabled }
        val pendingQuick = frictionSlice.pendingDisables.any {
            it.targetType == DelayedDisableTargetType.QUICK_BLOCK
        }
        val strictLocked = frictionSlice.strictConfig.isEffectivelyLocked(nowMs)
        val strictRemMin = if (strictLocked) {
            ((frictionSlice.strictConfig.endsAtEpochMs - nowMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        } else {
            0
        }

        ActiveProtectionSummary(
            isEngineConnected = a11yConnected,
            isGuardServiceRunning = guardRunning,
            quickBlockRemainingMinutes = quickRemaining,
            activeScheduleNames = activeSchedules,
            activeLimitCount = limitCount,
            allowlistEnabled = snapshot.allowlist.isEnabled,
            blockedTodayCount = blockedToday,
            pauseSeconds = frictionSlice.settings.pauseSeconds,
            passesUsedToday = frictionSlice.todayLogs.size,
            dailyUnlockPassCap = frictionSlice.settings.dailyUnlockPassCap,
            delayedDisableEnabled = frictionSlice.settings.delayedDisableEnabled,
            delayedDisableMinutes = frictionSlice.settings.delayedDisableMinutes,
            pendingDisablesCount = frictionSlice.pendingDisables.size,
            quickBlockPendingDisable = pendingQuick,
            isStrictLocked = strictLocked,
            strictRemainingMinutes = strictRemMin,
            conditionalRuleCount = snapshot.conditionalRules.count { it.isEnabled },
            inAppFeedBlockingEnabled = snapshot.smartRulesConfig.inAppFeedBlockingEnabled &&
                snapshot.smartRulesConfig.enabledFeedProfileIds.isNotEmpty(),
            browserRuleCount = if (snapshot.smartRulesConfig.browserBlockingEnabled) {
                snapshot.browserKeywordRules.count { it.isEnabled }
            } else {
                0
            },
            grayscaleDimEnabled = snapshot.smartRulesConfig.grayscaleDimOnLimitEnabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActiveProtectionSummary(),
    )

    fun refreshHealth() {
        permissionHealthManager.refresh()
        insightsRepository.refreshUsageStats()
    }

    fun stopQuickBlock() {
        viewModelScope.launch {
            val summary = protectionSummary.value
            if (summary.isStrictLocked) return@launch
            if (summary.delayedDisableEnabled) {
                frictionRepository.requestDelayedDisable(
                    targetType = DelayedDisableTargetType.QUICK_BLOCK,
                    targetId = 1L,
                    targetLabel = "Quick Block",
                    delayMinutes = summary.delayedDisableMinutes,
                )
            } else {
                blockingRulesRepository.stopQuickBlock()
            }
        }
    }

    suspend fun loadIcon(packageName: String): ImageBitmap? =
        installedAppsRepository.loadAppIcon(packageName)
}
