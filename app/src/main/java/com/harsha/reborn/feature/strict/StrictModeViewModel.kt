package com.harsha.reborn.feature.strict

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsha.reborn.core.data.model.StrictModeConfig
import com.harsha.reborn.core.data.model.StrictModeLogEntry
import com.harsha.reborn.core.data.repository.StrictModeRepository
import com.harsha.reborn.core.data.repository.StrictUnlockResult
import com.harsha.reborn.core.engine.BatteryStatusHelper
import com.harsha.reborn.core.engine.RebornDeviceAdminManager
import com.harsha.reborn.core.engine.StrictModeCrypto
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

data class StrictModeUiState(
    val config: StrictModeConfig = StrictModeConfig(),
    val logs: List<StrictModeLogEntry> = emptyList(),
    val isChargerConnected: Boolean = false,
    val isDeviceAdminActive: Boolean = false,
    val nowEpochMs: Long = System.currentTimeMillis(),
    val generatedTrustedCode: String = "",
    val feedbackError: String? = null,
    val feedbackMessage: String? = null,
)

@HiltViewModel
class StrictModeViewModel @Inject constructor(
    private val strictModeRepository: StrictModeRepository,
    private val batteryStatusHelper: BatteryStatusHelper,
    private val deviceAdminManager: RebornDeviceAdminManager,
) : ViewModel() {

    private val isDeviceAdminFlow = MutableStateFlow(deviceAdminManager.isAdminActive())
    private val generatedCodeFlow = MutableStateFlow(StrictModeCrypto.generateTrustedContactCode())
    private val feedbackErrorFlow = MutableStateFlow<String?>(null)
    private val feedbackMessageFlow = MutableStateFlow<String?>(null)

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private data class FeedbackAndAdminSlice(
        val adminActive: Boolean,
        val trustedCode: String,
        val error: String?,
        val message: String?,
        val nowMs: Long,
    )

    private val metaSliceFlow = combine(
        isDeviceAdminFlow,
        generatedCodeFlow,
        feedbackErrorFlow,
        feedbackMessageFlow,
        tickerFlow,
    ) { adminActive, code, error, msg, nowMs ->
        FeedbackAndAdminSlice(
            adminActive = adminActive,
            trustedCode = code,
            error = error,
            message = msg,
            nowMs = nowMs,
        )
    }

    val uiState: StateFlow<StrictModeUiState> = combine(
        strictModeRepository.strictConfigFlow,
        strictModeRepository.strictLogsFlow,
        batteryStatusHelper.observeChargerConnected(),
        metaSliceFlow,
    ) { config, logs, chargerConnected, meta ->
        if (config.isActive && meta.nowMs >= config.endsAtEpochMs) {
            viewModelScope.launch { strictModeRepository.expireIfDue(meta.nowMs) }
        }
        StrictModeUiState(
            config = config,
            logs = logs,
            isChargerConnected = chargerConnected,
            isDeviceAdminActive = meta.adminActive,
            nowEpochMs = meta.nowMs,
            generatedTrustedCode = meta.trustedCode,
            feedbackError = meta.error,
            feedbackMessage = meta.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StrictModeUiState(),
    )

    fun refreshDeviceAdminStatus() {
        isDeviceAdminFlow.value = deviceAdminManager.isAdminActive()
    }

    fun getActivateDeviceAdminIntent(): Intent =
        deviceAdminManager.createActivateAdminIntent()

    fun deactivateDeviceAdmin() {
        if (uiState.value.config.isEffectivelyLocked(System.currentTimeMillis())) {
            feedbackErrorFlow.value = "Cannot remove Device Admin while Strict Mode is locked."
            return
        }
        if (deviceAdminManager.deactivateAdmin()) {
            isDeviceAdminFlow.value = deviceAdminManager.isAdminActive()
            feedbackMessageFlow.value = "Device Admin protection turned off."
        }
    }

    fun regenerateTrustedCode() {
        generatedCodeFlow.value = StrictModeCrypto.generateTrustedContactCode()
    }

    fun clearFeedback() {
        feedbackErrorFlow.value = null
        feedbackMessageFlow.value = null
    }

    fun activateStrictMode(
        durationMinutes: Int,
        requirePin: Boolean,
        pin: String,
        requireCharger: Boolean,
        requireTrustedCode: Boolean,
        blockSystemGuards: Boolean,
        disallowUnlockPasses: Boolean,
        emergencyWaitMinutes: Int,
    ) {
        feedbackErrorFlow.value = null
        feedbackMessageFlow.value = null

        if (requirePin && pin.trim().length < 4) {
            feedbackErrorFlow.value = "Please enter a PIN of at least 4 digits."
            return
        }

        val trustedCode = if (requireTrustedCode) generatedCodeFlow.value else ""
        viewModelScope.launch {
            strictModeRepository.activateStrictMode(
                durationMinutes = durationMinutes,
                requirePin = requirePin,
                rawPin = pin,
                requireChargerConnected = requireCharger,
                requireTrustedContactCode = requireTrustedCode,
                rawTrustedContactCode = trustedCode,
                blockAppSettingsAndSystemGuards = blockSystemGuards,
                disallowUnlockPassesWhileActive = disallowUnlockPasses,
                emergencyWaitMinutes = emergencyWaitMinutes,
            )
            // Immediately rotate away the displayed plaintext friend code once hashed & locked
            generatedCodeFlow.value = StrictModeCrypto.generateTrustedContactCode()
            feedbackMessageFlow.value = "Strict Mode locked. Stay focused!"
        }
    }

    fun attemptEarlyUnlock(pinCandidate: String, trustedCodeCandidate: String) {
        feedbackErrorFlow.value = null
        feedbackMessageFlow.value = null

        viewModelScope.launch {
            val charger = batteryStatusHelper.isChargerConnected()
            when (
                strictModeRepository.attemptEarlyUnlock(
                    pinCandidate = pinCandidate,
                    trustedCodeCandidate = trustedCodeCandidate,
                    isChargerConnected = charger,
                )
            ) {
                StrictUnlockResult.Unlocked -> {
                    feedbackMessageFlow.value = "Strict Mode unlocked early."
                }
                StrictUnlockResult.NoEarlyUnlockEnabled -> {
                    feedbackErrorFlow.value = "No early unlock method configured. Wait for the timer or use Emergency Escape."
                }
                StrictUnlockResult.ChargerNotConnected -> {
                    feedbackErrorFlow.value = "Connect your device to a charger before unlocking."
                }
                StrictUnlockResult.InvalidPin -> {
                    feedbackErrorFlow.value = "Incorrect PIN. Try again."
                }
                StrictUnlockResult.InvalidTrustedCode -> {
                    feedbackErrorFlow.value = "Incorrect trusted-contact code."
                }
                StrictUnlockResult.InvalidEscapePhrase -> Unit
            }
        }
    }

    fun executeEmergencyEscape(typedPhrase: String, onSuccess: () -> Unit) {
        feedbackErrorFlow.value = null
        viewModelScope.launch {
            when (strictModeRepository.executeEmergencyEscape(typedPhrase)) {
                StrictUnlockResult.Unlocked -> {
                    feedbackMessageFlow.value = "Emergency Escape completed and logged."
                    onSuccess()
                }
                else -> {
                    feedbackErrorFlow.value = "Type the exact confirmation phrase to complete Emergency Escape."
                }
            }
        }
    }

    fun clearLogs() {
        if (uiState.value.config.isEffectivelyLocked(System.currentTimeMillis())) return
        viewModelScope.launch {
            strictModeRepository.clearLogs()
        }
    }
}
