package com.harsha.reborn.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.engine.OemBatteryGuidance
import com.harsha.reborn.core.engine.OemBatteryHelper
import com.harsha.reborn.core.engine.PermissionHealthManager
import com.harsha.reborn.core.engine.PermissionHealthSnapshot
import com.harsha.reborn.core.engine.RebornPermissionType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionHealthManager: PermissionHealthManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val healthSnapshot: StateFlow<PermissionHealthSnapshot> = permissionHealthManager.healthSnapshot

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    val oemGuidance: OemBatteryGuidance = OemBatteryHelper.getGuidance(context)

    fun refreshPermissions() {
        permissionHealthManager.refresh()
    }

    fun openPermissionSettings(type: RebornPermissionType) {
        permissionHealthManager.openPermissionSettings(type)
    }

    fun openOemBatterySettings() {
        OemBatteryHelper.openBestBatterySettings(context)
    }

    fun nextStep(totalSteps: Int) {
        if (_currentStep.value < totalSteps - 1) {
            _currentStep.value += 1
        }
    }

    fun previousStep() {
        if (_currentStep.value > 0) {
            _currentStep.value -= 1
        }
    }

    fun setStep(step: Int) {
        _currentStep.value = step.coerceAtLeast(0)
    }

    fun completeOnboarding(onCompleted: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
            onCompleted()
        }
    }
}
