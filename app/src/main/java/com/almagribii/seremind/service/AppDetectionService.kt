package com.almagribii.seremind.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.ui.overlay.OverlayWindowManager
import com.almagribii.seremind.util.PermissionHelper
import com.almagribii.seremind.util.UsageStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppDetectionService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var lastTriggeredPackage: String? = null

    companion object {
        const val CHANNEL_ID = "SeremindServiceChannel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "AppDetectionService"

        fun startService(context: Context) {
            if (!PermissionHelper.hasUsageStatsPermission(context)) return
            try {
                val serviceIntent = Intent(context, AppDetectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start AppDetectionService: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AppDetectionService onCreate")
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startAppMonitoring()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Seremind Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Memantau penggunaan aplikasi untuk membantu Anda tetap tenang."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Seremind Aktif")
            .setContentText("Menjaga kesadaran digital dan ketenangan pikiran Anda.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private fun startAppMonitoring() {
        serviceScope.launch {
            val database = SeremindDatabase.getDatabase(applicationContext)
            val appTargetDao = database.appTargetDao()
            Log.d(TAG, "Started app monitoring polling loop")

            while (isActive) {
                try {
                    val foregroundApp = UsageStatsHelper.getForegroundApp(applicationContext)
                    Log.d(TAG, "Polled foreground app: $foregroundApp (self package: $packageName)")
                    if (foregroundApp != null && foregroundApp != packageName) {
                        val target = appTargetDao.getTargetByPackage(foregroundApp)
                        Log.d(TAG, "Database target check for $foregroundApp: found target = $target, isActive = ${target?.isActive}")

                        if (target != null && target.isActive) {
                            // Trigger once per foreground entry. The state is reset when
                            // the user leaves the target, so a quick re-entry is detected too.
                            if (foregroundApp != lastTriggeredPackage) {
                                Log.d(TAG, "Triggering overlay for target app: ${target.appName} (${target.packageName}) with delay ${target.delaySeconds}s")
                                lastTriggeredPackage = foregroundApp
                                OverlayWindowManager.showOverlay(
                                    applicationContext,
                                    target.packageName,
                                    target.appName,
                                    target.delaySeconds
                                )
                            } else {
                                Log.d(TAG, "Skipping overlay for $foregroundApp: triggered recently (cooldown active)")
                            }
                        } else {
                            Log.d(TAG, "No active target found in database for package: $foregroundApp")
                        }
                    } else if (foregroundApp != packageName && foregroundApp != lastTriggeredPackage) {
                        // Leaving the target arms the next entry, regardless of timing.
                        lastTriggeredPackage = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in app monitoring loop", e)
                }
                delay(500) // Detect a newly opened app before it becomes interactive.
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AppDetectionService onDestroy")
        serviceJob.cancel()
    }
}
