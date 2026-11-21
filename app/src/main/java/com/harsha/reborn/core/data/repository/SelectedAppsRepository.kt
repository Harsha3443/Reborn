package com.harsha.reborn.core.data.repository

import com.harsha.reborn.core.data.local.SelectedAppDao
import com.harsha.reborn.core.data.model.InstalledAppInfo
import com.harsha.reborn.core.data.model.SelectedAppEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelectedAppsRepository @Inject constructor(
    private val selectedAppDao: SelectedAppDao,
) {
    val selectedApps: Flow<List<SelectedAppEntity>> = selectedAppDao.observeSelectedApps()

    val selectedPackageNames: Flow<Set<String>> =
        selectedAppDao.observeSelectedPackageNames().map { it.toSet() }

    suspend fun toggleAppSelection(app: InstalledAppInfo, currentlySelected: Boolean) {
        if (app.isProtectedSystemApp) return
        if (currentlySelected) {
            selectedAppDao.deleteByPackageName(app.packageName)
        } else {
            selectedAppDao.upsertApp(
                SelectedAppEntity(
                    packageName = app.packageName,
                    appLabel = app.label,
                    categoryName = app.category.name,
                )
            )
        }
    }

    suspend fun selectApps(apps: List<InstalledAppInfo>) {
        val entities = apps
            .filterNot { it.isProtectedSystemApp }
            .map {
                SelectedAppEntity(
                    packageName = it.packageName,
                    appLabel = it.label,
                    categoryName = it.category.name,
                )
            }
        if (entities.isNotEmpty()) {
            selectedAppDao.upsertApps(entities)
        }
    }

    suspend fun clearSelection() {
        selectedAppDao.clearAll()
    }
}
