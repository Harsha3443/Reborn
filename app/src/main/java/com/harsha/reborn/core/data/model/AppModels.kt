package com.harsha.reborn.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ThemeMode {
    DARK,
    AMOLED,
    LIGHT,
    SYSTEM,
}

enum class AppCategory(val displayName: String) {
    ALL("All"),
    SELECTED("Selected"),
    SOCIAL("Social"),
    VIDEO_AUDIO("Video & Audio"),
    GAMES("Games"),
    NEWS_READING("News & Reading"),
    PRODUCTIVITY("Productivity"),
    SHOPPING("Shopping"),
    OTHER("Other"),
}

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    val isProtectedSystemApp: Boolean = false,
    val protectionReason: String? = null,
)

@Entity(tableName = "selected_apps")
data class SelectedAppEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val categoryName: String,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
)
