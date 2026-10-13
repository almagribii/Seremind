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
    private var activeAppSession: String? = null
    private var gateShownForSession = false
    private var launcherPackage: String? = null

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
        launcherPackage = resolveLauncherPackage()
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
                            // A target app session owns one gate. Internal navigation
                            // (for example, opening a profile or another page) keeps
                            // the same package and therefore must not show it again.
                            if (foregroundApp != activeAppSession) {
                                activeAppSession = foregroundApp
                                gateShownForSession = false
                            }

                            if (!gateShownForSession) {
                                Log.d(TAG, "Triggering overlay for target app: ${target.appName} (${target.packageName}) with delay ${target.delaySeconds}s")
                                gateShownForSession = true
                                OverlayWindowManager.showOverlay(
                                    applicationContext,
                                    target.packageName,
                                    target.appName,
                                    target.delaySeconds
                                )
                            } else {
                                Log.d(TAG, "Skipping overlay for $foregroundApp: gate already shown in this app session")
                            }
                        }
                    } else if (
                        foregroundApp != null &&
                        activeAppSession != null &&
                        !isTransientForegroundPackage(foregroundApp)
                    ) {
                        // Only a different real app ends the session. System UI and
                        // launcher events can occur while the target app is loading.
                        activeAppSession = null
                        gateShownForSession = false
                        Log.d(TAG, "Target app session ended: foreground app changed to $foregroundApp")
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

    private fun isTransientForegroundPackage(foregroundApp: String): Boolean {
        return foregroundApp == packageName ||
                foregroundApp == "android" ||
                foregroundApp == "com.android.systemui" ||
                foregroundApp == launcherPackage ||
                foregroundApp.startsWith("com.android.systemui")
    }

    private fun resolveLauncherPackage(): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        return packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName
    }

}
