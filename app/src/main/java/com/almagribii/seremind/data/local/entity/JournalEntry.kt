package com.almagribii.seremind.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "emotion_tag") val emotionTag: String? = null,
    @ColumnInfo(name = "entry_type") val entryType: String, // "free", "gate_reflection", "recovery_reflection", "daily_prompt", "weekly_summary"
    @ColumnInfo(name = "related_gate_id") val relatedGateId: Int? = null,
    @ColumnInfo(name = "ai_prompt") val aiPrompt: String? = null,
    @ColumnInfo(name = "ai_feedback") val aiFeedback: String? = null,
    @ColumnInfo(name = "ai_feedback_error") val aiFeedbackError: String? = null
)
