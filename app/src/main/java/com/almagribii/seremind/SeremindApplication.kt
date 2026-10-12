package com.almagribii.seremind

import android.app.Application
import android.content.Intent
import android.os.Build
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.service.AppDetectionService
import com.almagribii.seremind.util.PermissionHelper

class SeremindApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize Database instance
        SeremindDatabase.getDatabase(this)

        // Start AppDetectionService if usage stats permission is granted
        if (PermissionHelper.hasUsageStatsPermission(this)) {
            val serviceIntent = Intent(this, AppDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }
}
