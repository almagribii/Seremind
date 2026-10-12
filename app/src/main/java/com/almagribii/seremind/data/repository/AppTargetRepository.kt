package com.almagribii.seremind.data.repository

import com.almagribii.seremind.data.local.dao.AppTargetDao
import com.almagribii.seremind.data.local.entity.AppTarget

class AppTargetRepository(private val appTargetDao: AppTargetDao) {
    suspend fun getAllTargets(): List<AppTarget> = appTargetDao.getAllTargets()
    suspend fun getActiveTargets(): List<AppTarget> = appTargetDao.getActiveTargets()
    suspend fun getTargetByPackage(packageName: String): AppTarget? = appTargetDao.getTargetByPackage(packageName)
    suspend fun insert(target: AppTarget) = appTargetDao.insert(target)
    suspend fun update(target: AppTarget) = appTargetDao.update(target)
    suspend fun delete(target: AppTarget) = appTargetDao.delete(target)
}
