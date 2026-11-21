package com.harsha.reborn.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "strict_mode_logs")
data class StrictModeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val eventType: String,
    val detail: String,
    val timestampEpochMs: Long,
)
