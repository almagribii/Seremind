package com.almagribii.seremind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.almagribii.seremind.data.local.entity.RecoveryEvent

@Dao
interface RecoveryEventDao {
    @Insert
    suspend fun insert(event: RecoveryEvent)

    @Query("SELECT * FROM recovery_events WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getEventsSince(startTime: Long): List<RecoveryEvent>

    @Query("DELETE FROM recovery_events")
    suspend fun deleteAll()
}
