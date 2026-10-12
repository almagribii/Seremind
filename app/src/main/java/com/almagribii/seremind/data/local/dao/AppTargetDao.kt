package com.almagribii.seremind.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.almagribii.seremind.data.local.entity.AppTarget

@Dao
interface AppTargetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(target: AppTarget)

    @Update
    suspend fun update(target: AppTarget)

    @Delete
    suspend fun delete(target: AppTarget)

    @Query("SELECT * FROM app_targets WHERE is_active = 1")
    suspend fun getActiveTargets(): List<AppTarget>

    @Query("SELECT * FROM app_targets WHERE package_name = :packageName LIMIT 1")
    suspend fun getTargetByPackage(packageName: String): AppTarget?

    @Query("SELECT * FROM app_targets")
    suspend fun getAllTargets(): List<AppTarget>

    @Query("DELETE FROM app_targets WHERE package_name IN (:packageNames)")
    suspend fun deleteByPackages(packageNames: List<String>)
}
