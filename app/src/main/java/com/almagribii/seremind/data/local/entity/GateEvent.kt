package com.almagribii.seremind.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gate_events")
data class GateEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "action") val action: String, // "opened" or "cancelled"
    @ColumnInfo(name = "ai_question") val aiQuestion: String? = null
)
