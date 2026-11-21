package com.harsha.reborn.feature.blocking

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsha.reborn.core.data.model.AppCategory
import com.harsha.reborn.core.data.model.InstalledAppInfo
import com.harsha.reborn.core.data.repository.InstalledAppsRepository
import com.harsha.reborn.core.data.repository.SelectedAppsRepository
import com.harsha.reborn.core.data.repository.StrictModeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppPickerUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedCategory: AppCategory = AppCategory.ALL,
    val availableCategories: List<AppCategory> = listOf(AppCategory.ALL, AppCategory.SELECTED),
    val filteredApps: List<InstalledAppInfo> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val totalInstalledCount: Int = 0,
    val isStrictSettingsLocked: Boolean = false,
)

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val selectedAppsRepository: SelectedAppsRepository,
    private val strictModeRepository: StrictModeRepository,
) : ViewModel() {

    private val allApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isLoading = MutableStateFlow(true)
    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow(AppCategory.ALL)

    val uiState: StateFlow<AppPickerUiState> = combine(
        allApps,
        isLoading,
        searchQuery,
        selectedCategory,
        combine(selectedAppsRepository.selectedPackageNames, strictModeRepository.strictConfigFlow) { selected, strict ->
            selected to (strict.isEffectivelyLocked(System.currentTimeMillis()) && strict.blockAppSettingsAndSystemGuards)
        },
    ) { apps, loading, query, category, (selected, strictLocked) ->
        val normalizedQuery = query.trim().lowercase()
        val presentCategories = buildList {
            add(AppCategory.ALL)
            add(AppCategory.SELECTED)
            AppCategory.entries
                .filter { it != AppCategory.ALL && it != AppCategory.SELECTED }
                .forEach { cat ->
                    if (apps.any { it.category == cat }) add(cat)
                }
        }

        val filtered = apps.filter { app ->
            val matchesQuery = normalizedQuery.isEmpty() ||
                app.label.lowercase().contains(normalizedQuery) ||
                app.packageName.lowercase().contains(normalizedQuery)

            val matchesCategory = when (category) {
                AppCategory.ALL -> true
                AppCategory.SELECTED -> selected.contains(app.packageName)
                else -> app.category == category
            }

            matchesQuery && matchesCategory
        }

        AppPickerUiState(
            isLoading = loading,
            searchQuery = query,
            selectedCategory = category,
            availableCategories = presentCategories,
            filteredApps = filtered,
            selectedPackages = selected,
            totalInstalledCount = apps.size,
            isStrictSettingsLocked = strictLocked,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppPickerUiState(),
    )

    init {
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            isLoading.value = true
            allApps.value = installedAppsRepository.getLaunchableApps()
            isLoading.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onCategorySelected(category: AppCategory) {
        selectedCategory.value = category
    }

    fun toggleApp(app: InstalledAppInfo) {
        if (app.isProtectedSystemApp) return
        val currentlySelected = uiState.value.selectedPackages.contains(app.packageName)
        if (currentlySelected && uiState.value.isStrictSettingsLocked) return
        viewModelScope.launch {
            selectedAppsRepository.toggleAppSelection(app, currentlySelected)
        }
    }

    fun selectAllFiltered() {
        val selectable = uiState.value.filteredApps.filterNot { it.isProtectedSystemApp }
        viewModelScope.launch {
            selectedAppsRepository.selectApps(selectable)
        }
    }

    fun clearAllSelected() {
        if (uiState.value.isStrictSettingsLocked) return
        viewModelScope.launch {
            selectedAppsRepository.clearSelection()
        }
    }

    suspend fun loadIcon(packageName: String): ImageBitmap? =
        installedAppsRepository.loadAppIcon(packageName)
}
