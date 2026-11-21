package com.harsha.reborn.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unlock_pass_logs")
data class UnlockPassLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val appLabel: String,
    val reason: String,
    val durationMinutes: Int,
    val grantedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

@Entity(tableName = "pending_disable_requests")
data class PendingDisableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val targetType: String,
    val targetId: Long,
    val targetLabel: String,
    val requestedAtEpochMs: Long,
    val executeAfterEpochMs: Long,
)
