package com.harsha.reborn.core.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.harsha.reborn.core.data.local.SelectedAppDao
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.repository.BlockingRulesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class QuickBlockWidgetState(
    val isActive: Boolean = false,
    val remainingMinutes: Int = 0,
    val selectedAppsCount: Int = 0,
    val isStrictLocked: Boolean = false,
)

sealed interface QuickBlockToggleOutcome {
    data class Started(val appCount: Int, val durationMinutes: Int) : QuickBlockToggleOutcome
    data object Stopped : QuickBlockToggleOutcome
    data object StrictLocked : QuickBlockToggleOutcome
    data object NoAppsSelected : QuickBlockToggleOutcome
}

@Singleton
class QuickBlockToggleController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockingRulesRepository: BlockingRulesRepository,
    private val selectedAppDao: SelectedAppDao,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun readCurrentState(): QuickBlockWidgetState {
        val now = System.currentTimeMillis()
        val rules = blockingRulesRepository.rulesSnapshotFlow.first()
        val prefs = settingsRepository.preferencesFlow.first()
        val selectedCount = selectedAppDao.getSelectedPackageNamesOnce().size
        val quick = rules.quickBlock
        val active = quick != null && quick.isActive && now < quick.endTimeEpochMs
        val remaining = if (active && quick != null) {
            ((quick.endTimeEpochMs - now + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        } else {
            0
        }
        return QuickBlockWidgetState(
            isActive = active,
            remainingMinutes = remaining,
            selectedAppsCount = selectedCount,
            isStrictLocked = prefs.strictModeConfig.isEffectivelyLocked(now),
        )
    }

    suspend fun toggleQuickBlock(
        defaultDurationMinutes: Int = DEFAULT_TOGGLE_DURATION_MINUTES,
    ): QuickBlockToggleOutcome {
        val current = readCurrentState()
        if (current.isActive) {
            if (current.isStrictLocked) {
                return QuickBlockToggleOutcome.StrictLocked
            }
            blockingRulesRepository.stopQuickBlock()
            return QuickBlockToggleOutcome.Stopped
        }

        val selected = selectedAppDao.getSelectedPackageNamesOnce().toSet()
        if (selected.isEmpty()) {
            return QuickBlockToggleOutcome.NoAppsSelected
        }

        blockingRulesRepository.startQuickBlock(selected, defaultDurationMinutes)
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RebornGuardService::class.java),
            )
        }
        return QuickBlockToggleOutcome.Started(
            appCount = selected.size,
            durationMinutes = defaultDurationMinutes,
        )
    }

    companion object {
        const val DEFAULT_TOGGLE_DURATION_MINUTES = 30
    }
}
