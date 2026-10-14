package com.almagribii.seremind.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_targets")
data class AppTarget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "delay_seconds") val delaySeconds: Int = 5,
    @ColumnInfo(name = "max_session_minutes") val maxSessionMinutes: Int = 15,
    @ColumnInfo(name = "cooldown_minutes") val cooldownMinutes: Int = 30,
    @ColumnInfo(name = "last_session_end_time") val lastSessionEndTime: Long = 0L
)
