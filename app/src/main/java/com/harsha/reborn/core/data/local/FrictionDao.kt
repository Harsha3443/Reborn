package com.harsha.reborn.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FrictionDao {
    @Query("SELECT * FROM unlock_pass_logs ORDER BY grantedAtEpochMs DESC")
    fun observeAllUnlockPasses(): Flow<List<UnlockPassLogEntity>>

    @Query("SELECT * FROM unlock_pass_logs WHERE grantedAtEpochMs >= :sinceEpochMs ORDER BY grantedAtEpochMs DESC")
    fun observeUnlockPassesSince(sinceEpochMs: Long): Flow<List<UnlockPassLogEntity>>

    @Query("SELECT COUNT(*) FROM unlock_pass_logs WHERE grantedAtEpochMs >= :sinceEpochMs")
    suspend fun countUnlockPassesSince(sinceEpochMs: Long): Int

    @Query("SELECT * FROM unlock_pass_logs WHERE expiresAtEpochMs > :nowEpochMs ORDER BY expiresAtEpochMs DESC")
    fun observeActivePasses(nowEpochMs: Long): Flow<List<UnlockPassLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnlockPass(entity: UnlockPassLogEntity): Long

    @Query("DELETE FROM unlock_pass_logs")
    suspend fun clearAllUnlockPasses()

    @Query("SELECT * FROM pending_disable_requests ORDER BY executeAfterEpochMs ASC")
    fun observePendingDisables(): Flow<List<PendingDisableEntity>>

    @Query("SELECT * FROM pending_disable_requests WHERE executeAfterEpochMs <= :nowEpochMs")
    suspend fun getDuePendingDisables(nowEpochMs: Long): List<PendingDisableEntity>

    @Query(
        "SELECT * FROM pending_disable_requests WHERE targetType = :targetType AND targetId = :targetId LIMIT 1"
    )
    suspend fun findPendingForTarget(targetType: String, targetId: Long): PendingDisableEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDisable(entity: PendingDisableEntity): Long

    @Query("DELETE FROM pending_disable_requests WHERE id = :id")
    suspend fun deletePendingDisableById(id: Long)

    @Query("DELETE FROM pending_disable_requests")
    suspend fun clearAllPendingDisables()
}
