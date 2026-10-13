package com.almagribii.seremind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.almagribii.seremind.data.local.entity.GateEvent

data class ReasonCount(val reason: String, val count: Int)
data class HourCount(val hour: String, val count: Int)

@Dao
interface GateEventDao {
    @Insert
    suspend fun insert(event: GateEvent)

    @Query("SELECT * FROM gate_events WHERE package_name = :packageName ORDER BY timestamp DESC")
    suspend fun getEventsForPackage(packageName: String): List<GateEvent>

    @Query("SELECT * FROM gate_events WHERE package_name = :packageName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEventsForPackage(packageName: String, limit: Int = 10): List<GateEvent>

    @Query("SELECT * FROM gate_events WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getEventsSince(startTime: Long): List<GateEvent>

    @Query("SELECT COUNT(*) FROM gate_events WHERE package_name = :packageName AND timestamp >= :startTime AND action = 'cancelled'")
    suspend fun getCancelledCountForPackage(packageName: String, startTime: Long): Int

    @Query("SELECT COUNT(*) FROM gate_events WHERE `action` = 'cancelled' AND timestamp >= :startTime")
    suspend fun getCancelledCount(startTime: Long): Int

    @Query("SELECT COUNT(*) FROM gate_events WHERE `action` = 'opened' AND timestamp >= :startTime")
    suspend fun getOpenedCount(startTime: Long): Int

    @Query("SELECT reason, COUNT(*) as count FROM gate_events WHERE timestamp >= :startTime GROUP BY reason ORDER BY count DESC")
    suspend fun getReasonDistribution(startTime: Long): List<ReasonCount>

    @Query("SELECT strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') as hour, COUNT(*) as count FROM gate_events WHERE timestamp >= :startTime GROUP BY hour ORDER BY hour")
    suspend fun getHourlyDistribution(startTime: Long): List<HourCount>

    @Query("DELETE FROM gate_events")
    suspend fun deleteAll()
}
