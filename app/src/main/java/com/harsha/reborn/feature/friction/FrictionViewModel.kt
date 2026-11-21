package com.harsha.reborn.feature.friction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsha.reborn.core.data.model.FrictionSettings
import com.harsha.reborn.core.data.model.PendingDisableRequest
import com.harsha.reborn.core.data.model.UnlockPassLog
import com.harsha.reborn.core.data.repository.FrictionRepository
import com.harsha.reborn.core.data.repository.StrictModeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FrictionUiState(
    val settings: FrictionSettings = FrictionSettings(),
    val passesUsedToday: Int = 0,
    val unlockLogs: List<UnlockPassLog> = emptyList(),
    val pendingDisables: List<PendingDisableRequest> = emptyList(),
    val isStrictSettingsLocked: Boolean = false,
    val nowEpochMs: Long = System.currentTimeMillis(),
)

@HiltViewModel
class FrictionViewModel @Inject constructor(
    private val frictionRepository: FrictionRepository,
    private val strictModeRepository: StrictModeRepository,
) : ViewModel() {

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private val strictAndTickFlow = combine(
        strictModeRepository.strictConfigFlow,
        tickerFlow,
    ) { strictConfig, nowMs ->
        (strictConfig.isEffectivelyLocked(nowMs) && strictConfig.blockAppSettingsAndSystemGuards) to nowMs
    }

    val uiState: StateFlow<FrictionUiState> = combine(
        frictionRepository.frictionSettingsFlow,
        frictionRepository.observeUnlockLogsToday(),
        frictionRepository.allUnlockLogsFlow,
        frictionRepository.pendingDisablesFlow,
        strictAndTickFlow,
    ) { settings, todayLogs, allLogs, pending, (strictLocked, nowMs) ->
        FrictionUiState(
            settings = settings,
            passesUsedToday = todayLogs.size,
            unlockLogs = allLogs,
            pendingDisables = pending,
            isStrictSettingsLocked = strictLocked,
            nowEpochMs = nowMs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FrictionUiState(),
    )

    fun setPauseSeconds(seconds: Int) {
        if (uiState.value.isStrictSettingsLocked) return
        viewModelScope.launch { frictionRepository.setPauseSeconds(seconds) }
    }

    fun setDailyUnlockPassCap(cap: Int) {
        if (uiState.value.isStrictSettingsLocked) return
        viewModelScope.launch { frictionRepository.setDailyUnlockPassCap(cap) }
    }

    fun setDelayedDisableEnabled(enabled: Boolean) {
        if (uiState.value.isStrictSettingsLocked) return
        viewModelScope.launch { frictionRepository.setDelayedDisableEnabled(enabled) }
    }

    fun setDelayedDisableMinutes(minutes: Int) {
        if (uiState.value.isStrictSettingsLocked) return
        viewModelScope.launch { frictionRepository.setDelayedDisableMinutes(minutes) }
    }

    fun cancelPendingDisable(id: Long) {
        viewModelScope.launch { frictionRepository.cancelPendingDisable(id) }
    }

    fun clearUnlockLogs() {
        if (uiState.value.isStrictSettingsLocked) return
        viewModelScope.launch { frictionRepository.clearUnlockLogs() }
    }
}
