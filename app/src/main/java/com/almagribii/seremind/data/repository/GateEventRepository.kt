package com.almagribii.seremind.data.repository

import com.almagribii.seremind.data.local.dao.GateEventDao
import com.almagribii.seremind.data.local.dao.HourCount
import com.almagribii.seremind.data.local.dao.ReasonCount
import com.almagribii.seremind.data.local.entity.GateEvent

class GateEventRepository(private val gateEventDao: GateEventDao) {
    suspend fun insert(event: GateEvent) = gateEventDao.insert(event)
    suspend fun getEventsSince(startTime: Long): List<GateEvent> = gateEventDao.getEventsSince(startTime)
    suspend fun getCancelledCount(startTime: Long): Int = gateEventDao.getCancelledCount(startTime)
    suspend fun getOpenedCount(startTime: Long): Int = gateEventDao.getOpenedCount(startTime)
    suspend fun getReasonDistribution(startTime: Long): List<ReasonCount> = gateEventDao.getReasonDistribution(startTime)
    suspend fun getHourlyDistribution(startTime: Long): List<HourCount> = gateEventDao.getHourlyDistribution(startTime)
}
