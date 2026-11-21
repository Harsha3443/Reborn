package com.harsha.reborn.feature.blocking

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsha.reborn.core.data.model.AppUsageSnapshot
import com.harsha.reborn.core.data.model.BlockingRulesSnapshot
import com.harsha.reborn.core.data.model.ConditionalRule
import com.harsha.reborn.core.data.model.DailyTimeLimitRule
import com.harsha.reborn.core.data.model.DelayedDisableTargetType
import com.harsha.reborn.core.data.model.FrictionSettings
import com.harsha.reborn.core.data.model.InstalledAppInfo
import com.harsha.reborn.core.data.model.LaunchLimitRule
import com.harsha.reborn.core.data.model.PendingDisableRequest
import com.harsha.reborn.core.data.model.ScheduleProfile
import com.harsha.reborn.core.data.repository.BlockingRulesRepository
import com.harsha.reborn.core.data.repository.FrictionRepository
import com.harsha.reborn.core.data.repository.InstalledAppsRepository
import com.harsha.reborn.core.data.repository.SelectedAppsRepository
import com.harsha.reborn.core.data.repository.StrictModeRepository
import com.harsha.reborn.core.engine.NetworkWifiHelper
import com.harsha.reborn.core.engine.OfflineCoordinates
import com.harsha.reborn.core.engine.OfflineLocationHelper
import com.harsha.reborn.core.engine.PermissionHealthManager
import com.harsha.reborn.core.engine.RebornPermissionType
import com.harsha.reborn.core.engine.UsageStatsTracker
import com.harsha.reborn.core.engine.feed.FeedSelectorRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RulesTab(val title: String) {
    QUICK_BLOCK("Quick"),
    SCHEDULES("Schedules"),
    LIMITS("Limits"),
    ALLOWLIST("Allowlist"),
    SMART("Smart"),
}

data class RulesUiState(
    val snapshot: BlockingRulesSnapshot = BlockingRulesSnapshot(),
    val usageSnapshot: AppUsageSnapshot = AppUsageSnapshot(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val defaultSelectedPackages: Set<String> = emptySet(),
    val frictionSettings: FrictionSettings = FrictionSettings(),
    val pendingDisables: List<PendingDisableRequest> = emptyList(),
    val isStrictSettingsLocked: Boolean = false,
    val strictEndsAtEpochMs: Long = 0L,
    val isOverlayGranted: Boolean = true,
    val hasCoarseLocationPermission: Boolean = false,
    val currentWifiConnected: Boolean = false,
    val currentWifiSsid: String? = null,
    val feedbackMessage: String? = null,
    val nowEpochMs: Long = System.currentTimeMillis(),
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val rulesRepository: BlockingRulesRepository,
    private val frictionRepository: FrictionRepository,
    private val strictModeRepository: StrictModeRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val selectedAppsRepository: SelectedAppsRepository,
    private val usageStatsTracker: UsageStatsTracker,
    private val permissionHealthManager: PermissionHealthManager,
    private val networkWifiHelper: NetworkWifiHelper,
    private val offlineLocationHelper: OfflineLocationHelper,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(RulesTab.QUICK_BLOCK)
    val selectedTab: StateFlow<RulesTab> = _selectedTab

    private val installedAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val feedbackMessageFlow = MutableStateFlow<String?>(null)
    private val environmentRefreshTick = MutableStateFlow(0)

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private data class FrictionCombineSlice(
        val settings: FrictionSettings,
        val pending: List<PendingDisableRequest>,
        val strictLocked: Boolean,
        val strictEndsAtMs: Long,
        val feedback: String?,
        val nowMs: Long,
        val envTick: Int,
    )

    private val frictionSliceFlow = combine(
        frictionRepository.frictionSettingsFlow,
        frictionRepository.pendingDisablesFlow,
        strictModeRepository.strictConfigFlow,
        feedbackMessageFlow,
        combine(tickerFlow, environmentRefreshTick) { nowMs, envTick -> nowMs to envTick },
    ) { settings, pending, strictConfig, feedback, (nowMs, envTick) ->
        val strictLocked = strictConfig.isEffectivelyLocked(nowMs) && strictConfig.blockAppSettingsAndSystemGuards
        if (pending.any { it.executeAfterEpochMs <= nowMs } && !strictLocked) {
            viewModelScope.launch {
                frictionRepository.executeDueDelayedDisables(nowMs)
            }
        }
        FrictionCombineSlice(
            settings = settings,
            pending = pending,
            strictLocked = strictLocked,
            strictEndsAtMs = strictConfig.endsAtEpochMs,
            feedback = feedback,
            nowMs = nowMs,
            envTick = envTick,
        )
    }

    val uiState: StateFlow<RulesUiState> = combine(
        rulesRepository.rulesSnapshotFlow,
        usageStatsTracker.usageSnapshot,
        installedAppsFlow,
        selectedAppsRepository.selectedPackageNames,
        frictionSliceFlow,
    ) { snapshot, usage, apps, selectedPkgs, frictionSlice ->
        val wifi = networkWifiHelper.getWifiSnapshot()
        RulesUiState(
            snapshot = snapshot,
            usageSnapshot = usage,
            installedApps = apps,
            defaultSelectedPackages = selectedPkgs,
            frictionSettings = frictionSlice.settings,
            pendingDisables = frictionSlice.pending,
            isStrictSettingsLocked = frictionSlice.strictLocked,
            strictEndsAtEpochMs = frictionSlice.strictEndsAtMs,
            isOverlayGranted = permissionHealthManager.isOverlayGranted(),
            hasCoarseLocationPermission = offlineLocationHelper.hasCoarseLocationPermission(),
            currentWifiConnected = wifi.isWifiConnected,
            currentWifiSsid = wifi.ssid,
            feedbackMessage = frictionSlice.feedback,
            nowEpochMs = frictionSlice.nowMs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RulesUiState(),
    )

    init {
        viewModelScope.launch {
            rulesRepository.ensureDefaultSchedulesSeeded()
            installedAppsFlow.value = installedAppsRepository.getLaunchableApps()
            usageStatsTracker.refreshTodayUsage()
        }
    }

    fun selectTab(tab: RulesTab) {
        _selectedTab.value = tab
    }

    fun refreshUsage() {
        environmentRefreshTick.value += 1
        viewModelScope.launch {
            usageStatsTracker.refreshTodayUsage()
            frictionRepository.executeDueDelayedDisables()
        }
    }

    fun clearFeedback() {
        feedbackMessageFlow.value = null
    }

    fun startQuickBlock(packageNames: Set<String>, durationMinutes: Int) {
        viewModelScope.launch {
            rulesRepository.startQuickBlock(packageNames, durationMinutes)
        }
    }

    fun stopQuickBlock() {
        viewModelScope.launch {
            if (isLockedByStrict()) return@launch
            val friction = uiState.value.frictionSettings
            if (friction.delayedDisableEnabled) {
                frictionRepository.requestDelayedDisable(
                    targetType = DelayedDisableTargetType.QUICK_BLOCK,
                    targetId = 1L,
                    targetLabel = "Quick Block",
                    delayMinutes = friction.delayedDisableMinutes,
                )
                feedbackMessageFlow.value =
                    "Quick Block will turn off after a ${friction.delayedDisableMinutes}-minute cool-down."
            } else {
                rulesRepository.stopQuickBlock()
            }
        }
    }

    fun saveSchedule(profile: ScheduleProfile) {
        viewModelScope.launch {
            if (profile.id != 0L && isLockedByStrict()) return@launch
            rulesRepository.saveSchedule(profile)
        }
    }

    fun toggleSchedule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isLockedByStrict()) return@launch
            val friction = uiState.value.frictionSettings
            if (!enabled && friction.delayedDisableEnabled) {
                val label = uiState.value.snapshot.schedules.firstOrNull { it.id == id }?.name ?: "Schedule"
                frictionRepository.requestDelayedDisable(
                    targetType = DelayedDisableTargetType.SCHEDULE,
                    targetId = id,
                    targetLabel = "$label Schedule",
                    delayMinutes = friction.delayedDisableMinutes,
                )
                feedbackMessageFlow.value =
                    "\"$label\" will disable after ${friction.delayedDisableMinutes} min."
            } else {
                rulesRepository.setScheduleEnabled(id, enabled)
            }
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            val schedule = uiState.value.snapshot.schedules.firstOrNull { it.id == id }
            if (schedule != null && schedule.isEnabled && isLockedByStrict()) return@launch
            val friction = uiState.value.frictionSettings
            if (schedule != null && schedule.isEnabled && friction.delayedDisableEnabled) {
                frictionRepository.requestDelayedDisable(
                    targetType = DelayedDisableTargetType.SCHEDULE,
                    targetId = id,
                    targetLabel = "${schedule.name} Schedule",
                    delayMinutes = friction.delayedDisableMinutes,
                )
                feedbackMessageFlow.value =
                    "Disable active schedule \"${schedule.name}\" first (scheduled in ${friction.delayedDisableMinutes} min)."
            } else {
                rulesRepository.deleteSchedule(id)
            }
        }
    }

    fun saveDailyTimeLimit(rule: DailyTimeLimitRule) {
        viewModelScope.launch {
            if (rule.id != 0L && isLockedByStrict()) return@launch
            rulesRepository.saveDailyTimeLimit(rule)
        }
    }

    fun toggleDailyTimeLimit(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isLockedByStrict()) return@launch
            val friction = uiState.value.frictionSettings
            if (!enabled && friction.delayedDisableEnabled) {
                val label = uiState.value.snapshot.dailyTimeLimits.firstOrNull { it.id == id }?.name
                    ?: "Daily Time Limit"
                frictionRepository.requestDelayedDisable(
                    targetType = DelayedDisableTargetType.DAILY_TIME_LIMIT,
                    targetId = id,
                    targetLabel = label,
                    delayMinutes = friction.delayedDisableMinutes,
                )
                feedbackMessageFlow.value =
                    "\"$label\" will disable after ${friction.delayedDisableMinutes} min."
            } else {
                rulesRepository.setDailyTimeLimitEnabled(id, enabled)
            }
        }
    }

    fun deleteDailyTimeLimit(id: Long) {
        viewModelScope.launch {
            val rule = uiState.value.snapshot.dailyTimeLimits.firstOrNull { it.id == id }
            if (rule != null && rule.isEnabled && isLockedByStrict()) return@launch
            val friction = uiState.value.frictionSettings
            if (rule != null && rule.isEnabled && friction.delayedDisableEnabled) {
                frictionRepository.requestDelayedDisable(
                    targetType = DelayedDisableTargetType.DAILY_TIME_LIMIT,
                    targetId = id,
                    targetLabel = rule.name,
                    delayMinutes = friction.delayedDisableMinutes,
                )
                feedbackMessageFlow.value =
                    "Disable active limit \"${rule.name}\" first (scheduled in ${friction.delayedDisableMinutes} min)."
            } else {
                rulesRepository.deleteDailyTimeLimit(id)
            }
        }
    }

    fun saveLaunchLimit(rule: LaunchLimitRule) {
        viewModelScope.launch {
            if (rule.id != 0L && isLockedByStrict()) return@launch
            rulesRepository.saveLaunchLimit(rule)
        }
    }

    fun toggleLaunchLimit(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isLockedByStrict()) return@launch
            val friction = uiState.value.frictionSettings
            if (!enabled && friction.delayedDisableEnabled) {
                val appLabel = uiState.value.snapshot.launchLimits.firstOrNull { it.id == id }?.appLabel
                    ?: "App"
                val label = "$appLabel Launch Limit"
                frictionRepository.requestDelayedDisable(
                    targetType = DelayedDisableTargetType.LAUNCH_LIMIT,
                    targetId = id,
                    targetLabel = label,
                    delayMinutes = friction.delayedDisableMinutes,
                )
                feedbackMessageFlow.value =
                    "\"$label\" will disable after ${friction.delayedDisableMinutes} min."
            } else {
                rulesRepository.setLaunchLimitEnabled(id, enabled)
            }
        }
    }

    fun deleteLaunchLimit(id: Long) {
        viewModelScope.launch {
            val rule = uiState.value.snapshot.launchLimits.firstOrNull { it.id == id }
            if (rule != null && rule.isEnabled && isLockedByStrict()) return@launch
            val friction = uiState.value.frictionSettings
            if (rule != null && rule.isEnabled && friction.delayedDisableEnabled) {
                val label = "${rule.appLabel} Launch Limit"
                frictionRepository.requestDelayedDisable(
                    targetType = DelayedDisableTargetType.LAUNCH_LIMIT,
                    targetId = id,
                    targetLabel = label,
                    delayMinutes = friction.delayedDisableMinutes,
                )
                feedbackMessageFlow.value =
                    "Disable active limit \"$label\" first (scheduled in ${friction.delayedDisableMinutes} min)."
            } else {
                rulesRepository.deleteLaunchLimit(id)
            }
        }
    }

    fun setAllowlistEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isLockedByStrict()) return@launch
            val friction = uiState.value.frictionSettings
            if (!enabled && friction.delayedDisableEnabled) {
                frictionRepository.requestDelayedDisable(
                    targetType = DelayedDisableTargetType.ALLOWLIST,
                    targetId = 1L,
                    targetLabel = "Allowlist Mode",
                    delayMinutes = friction.delayedDisableMinutes,
                )
                feedbackMessageFlow.value =
                    "Allowlist Mode will turn off after ${friction.delayedDisableMinutes} min."
            } else {
                rulesRepository.setAllowlistModeEnabled(enabled)
            }
        }
    }

    fun toggleAllowlistPackage(app: InstalledAppInfo, currentlyAllowed: Boolean) {
        if (app.isProtectedSystemApp) return
        viewModelScope.launch {
            if (!currentlyAllowed && isLockedByStrict()) return@launch
            rulesRepository.toggleAllowlistPackage(app.packageName, app.label, currentlyAllowed)
        }
    }

    // Phase 5: Smarter Rules Actions
    fun saveConditionalRule(rule: ConditionalRule) {
        viewModelScope.launch {
            if (rule.id != 0L && isLockedByStrict()) return@launch
            rulesRepository.saveConditionalRule(rule)
            feedbackMessageFlow.value = "Saved smart rule \"${rule.name}\"."
        }
    }

    fun toggleConditionalRule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isLockedByStrict()) return@launch
            val friction = uiState.value.frictionSettings
            if (!enabled && friction.delayedDisableEnabled) {
                val label = uiState.value.snapshot.conditionalRules.firstOrNull { it.id == id }?.name
                    ?: "Smart Rule"
                frictionRepository.requestDelayedDisable(
                    targetType = DelayedDisableTargetType.CONDITIONAL_RULE,
                    targetId = id,
                    targetLabel = label,
                    delayMinutes = friction.delayedDisableMinutes,
                )
                feedbackMessageFlow.value =
                    "\"$label\" will disable after ${friction.delayedDisableMinutes} min."
            } else {
                rulesRepository.setConditionalRuleEnabled(id, enabled)
            }
        }
    }

    fun deleteConditionalRule(id: Long) {
        viewModelScope.launch {
            val rule = uiState.value.snapshot.conditionalRules.firstOrNull { it.id == id }
            if (rule != null && rule.isEnabled && isLockedByStrict()) return@launch
            val friction = uiState.value.frictionSettings
            if (rule != null && rule.isEnabled && friction.delayedDisableEnabled) {
                frictionRepository.requestDelayedDisable(
                    targetType = DelayedDisableTargetType.CONDITIONAL_RULE,
                    targetId = id,
                    targetLabel = rule.name,
                    delayMinutes = friction.delayedDisableMinutes,
                )
                feedbackMessageFlow.value =
                    "Disable active rule \"${rule.name}\" first (scheduled in ${friction.delayedDisableMinutes} min)."
            } else {
                rulesRepository.deleteConditionalRule(id)
            }
        }
    }

    fun setInAppFeedBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isLockedByStrict()) return@launch
            rulesRepository.setInAppFeedBlockingEnabled(enabled)
        }
    }

    fun toggleFeedProfile(profileId: String, enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isLockedByStrict()) return@launch
            rulesRepository.toggleFeedProfile(profileId, enabled)
        }
    }

    fun saveCustomFeedSelectorsJson(rawJson: String): Boolean {
        if (isLockedByStrict()) return false
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            viewModelScope.launch {
                rulesRepository.setCustomFeedSelectorsJson("")
                feedbackMessageFlow.value = "Restored built-in feed selector config."
            }
            return true
        }
        val validation = FeedSelectorRegistry.validateCatalogJson(trimmed)
        if (validation.isFailure) {
            feedbackMessageFlow.value =
                "Invalid selector JSON: ${validation.exceptionOrNull()?.message ?: "check format"}"
            return false
        }
        viewModelScope.launch {
            rulesRepository.setCustomFeedSelectorsJson(trimmed)
            feedbackMessageFlow.value = "Updated config-driven feed selector rules."
        }
        return true
    }

    fun setBrowserBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isLockedByStrict()) return@launch
            rulesRepository.setBrowserBlockingEnabled(enabled)
        }
    }

    fun addBrowserKeywordRule(pattern: String, isDomain: Boolean) {
        val clean = pattern.trim()
        if (clean.length < 2) {
            feedbackMessageFlow.value = "Enter at least 2 characters (e.g. reddit.com or news)."
            return
        }
        viewModelScope.launch {
            rulesRepository.addBrowserKeywordRule(clean, isDomain)
        }
    }

    fun toggleBrowserKeywordRule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isLockedByStrict()) return@launch
            rulesRepository.setBrowserKeywordRuleEnabled(id, enabled)
        }
    }

    fun deleteBrowserKeywordRule(id: Long) {
        viewModelScope.launch {
            val rule = uiState.value.snapshot.browserKeywordRules.firstOrNull { it.id == id }
            if (rule != null && rule.isEnabled && isLockedByStrict()) return@launch
            rulesRepository.deleteBrowserKeywordRule(id)
        }
    }

    fun setGrayscaleDimOnLimitEnabled(enabled: Boolean) {
        viewModelScope.launch {
            rulesRepository.setGrayscaleDimOnLimitEnabled(enabled)
        }
    }

    fun openOverlayPermissionSettings() {
        permissionHealthManager.openPermissionSettings(RebornPermissionType.OVERLAY)
    }

    fun sampleCurrentWifiSsid(): String? {
        environmentRefreshTick.value += 1
        return networkWifiHelper.getWifiSnapshot().ssid
    }

    fun sampleCurrentCoordinates(): OfflineCoordinates? {
        environmentRefreshTick.value += 1
        return offlineLocationHelper.getBestLastKnownCoordinates()
    }

    private fun isLockedByStrict(): Boolean {
        if (uiState.value.isStrictSettingsLocked) {
            feedbackMessageFlow.value = "Locked by Strict Mode until the timer ends."
            return true
        }
        return false
    }

    fun cancelPendingDisable(id: Long) {
        viewModelScope.launch {
            frictionRepository.cancelPendingDisable(id)
            feedbackMessageFlow.value = "Scheduled disable cancelled — protection stays active."
        }
    }

    suspend fun loadIcon(packageName: String): ImageBitmap? =
        installedAppsRepository.loadAppIcon(packageName)
}
