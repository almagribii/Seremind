package com.almagribii.seremind.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recovery_events")
data class RecoveryEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "emotion") val emotion: String,
    @ColumnInfo(name = "ai_response") val aiResponse: String? = null
)
