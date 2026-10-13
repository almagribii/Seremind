package com.almagribii.seremind.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.almagribii.seremind.data.local.entity.JournalEntry

data class EmotionCount(val emotion_tag: String, val count: Int)

@Dao
interface JournalEntryDao {
    @Insert
    suspend fun insert(entry: JournalEntry): Long

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): JournalEntry?

    @Update
    suspend fun update(entry: JournalEntry)

    @Delete
    suspend fun delete(entry: JournalEntry)

    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    suspend fun getAllEntries(): List<JournalEntry>

    @Query("SELECT * FROM journal_entries WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getEntriesSince(startTime: Long): List<JournalEntry>

    @Query("SELECT * FROM journal_entries WHERE emotion_tag = :emotion ORDER BY timestamp DESC")
    suspend fun getEntriesByEmotion(emotion: String): List<JournalEntry>

    @Query("SELECT * FROM journal_entries WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchEntries(query: String): List<JournalEntry>

    @Query("SELECT emotion_tag, COUNT(*) as count FROM journal_entries WHERE timestamp >= :startTime AND emotion_tag IS NOT NULL GROUP BY emotion_tag ORDER BY count DESC")
    suspend fun getEmotionDistribution(startTime: Long): List<EmotionCount>

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()
}
