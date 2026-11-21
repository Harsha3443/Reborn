package com.harsha.reborn.feature.insights

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsha.reborn.core.data.model.InsightsDashboardSnapshot
import com.harsha.reborn.core.data.repository.InsightsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val insightsRepository: InsightsRepository,
) : ViewModel() {

    val dashboardState: StateFlow<InsightsDashboardSnapshot> = insightsRepository
        .observeDashboardSnapshot()
        .onEach { snapshot ->
            insightsRepository.syncTodayStreakIfNeeded(snapshot)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = InsightsDashboardSnapshot(),
        )

    private val _selectedDayIndex = MutableStateFlow(6)
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    fun selectDay(index: Int) {
        _selectedDayIndex.value = index.coerceIn(0, 6)
    }

    fun refresh() {
        viewModelScope.launch {
            insightsRepository.refreshUsageStats()
        }
    }

    suspend fun loadIcon(packageName: String): ImageBitmap? =
        insightsRepository.loadIcon(packageName)
}
