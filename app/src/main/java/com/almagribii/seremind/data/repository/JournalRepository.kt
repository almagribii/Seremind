package com.almagribii.seremind.data.repository

import com.almagribii.seremind.data.local.dao.EmotionCount
import com.almagribii.seremind.data.local.dao.JournalEntryDao
import com.almagribii.seremind.data.local.entity.JournalEntry

class JournalRepository(private val journalEntryDao: JournalEntryDao) {
    suspend fun insert(entry: JournalEntry) = journalEntryDao.insert(entry)
    suspend fun update(entry: JournalEntry) = journalEntryDao.update(entry)
    suspend fun delete(entry: JournalEntry) = journalEntryDao.delete(entry)
    suspend fun getAllEntries(): List<JournalEntry> = journalEntryDao.getAllEntries()
    suspend fun getEntriesSince(startTime: Long): List<JournalEntry> = journalEntryDao.getEntriesSince(startTime)
    suspend fun getEntriesByEmotion(emotion: String): List<JournalEntry> = journalEntryDao.getEntriesByEmotion(emotion)
    suspend fun searchEntries(query: String): List<JournalEntry> = journalEntryDao.searchEntries(query)
    suspend fun getEmotionDistribution(startTime: Long): List<EmotionCount> = journalEntryDao.getEmotionDistribution(startTime)
}
