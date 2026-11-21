package com.harsha.reborn.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StrictModeDao {
    @Query("SELECT * FROM strict_mode_logs ORDER BY timestampEpochMs DESC")
    fun observeAllLogs(): Flow<List<StrictModeLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entity: StrictModeLogEntity): Long

    @Query("DELETE FROM strict_mode_logs")
    suspend fun clearAllLogs()
}
