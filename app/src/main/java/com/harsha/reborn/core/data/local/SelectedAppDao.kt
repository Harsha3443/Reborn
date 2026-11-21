package com.harsha.reborn.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.harsha.reborn.core.data.model.SelectedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SelectedAppDao {
    @Query("SELECT * FROM selected_apps ORDER BY appLabel ASC")
    fun observeSelectedApps(): Flow<List<SelectedAppEntity>>

    @Query("SELECT packageName FROM selected_apps")
    fun observeSelectedPackageNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApps(apps: List<SelectedAppEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: SelectedAppEntity)

    @Query("DELETE FROM selected_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("SELECT * FROM selected_apps ORDER BY appLabel ASC")
    suspend fun getSelectedAppsOnce(): List<SelectedAppEntity>

    @Query("SELECT packageName FROM selected_apps")
    suspend fun getSelectedPackageNamesOnce(): List<String>

    @Query("DELETE FROM selected_apps")
    suspend fun clearAll()
}
