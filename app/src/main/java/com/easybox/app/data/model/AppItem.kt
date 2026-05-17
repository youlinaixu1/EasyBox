package com.easybox.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_items")
data class AppItem(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,        // GameType.id or "plugin"
    val category: String,    // "game" or "tool"
    val iconName: String,    // icon identifier
    val sortOrder: Int,
    val isBuiltIn: Boolean = true,
    val pluginPath: String? = null
)
