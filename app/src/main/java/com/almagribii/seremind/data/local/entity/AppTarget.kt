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
    @ColumnInfo(name = "max_session_minutes") val maxSessionMinutes: Int = 0,
    @ColumnInfo(name = "cooldown_minutes") val cooldownMinutes: Int = 0,
    @ColumnInfo(name = "last_session_end_time") val lastSessionEndTime: Long = 0L
) {
    fun isCooldownActive(now: Long = System.currentTimeMillis()): Boolean {
        if (!isActive || cooldownMinutes <= 0 || lastSessionEndTime <= 0L) return false
        val timeSinceLastEnd = now - lastSessionEndTime
        return timeSinceLastEnd in 0 until (cooldownMinutes * 60_000L)
    }

    fun cooldownMinutesLeft(now: Long = System.currentTimeMillis()): Int {
        if (!isCooldownActive(now)) return 0
        val timeSinceLastEnd = now - lastSessionEndTime
        val remainingMs = (cooldownMinutes * 60_000L) - timeSinceLastEnd
        return ((remainingMs / 60_000L) + 1).toInt().coerceAtLeast(1)
    }
}
