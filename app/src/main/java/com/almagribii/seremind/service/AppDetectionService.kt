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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

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
            val preferenceDao = database.userPreferenceDao()
            val gateEventDao = database.gateEventDao()
            updateServiceNotification(
                reflectionEnabled = preferenceDao.getPreference("reflection_reminder") != "false"
            )
            if (preferenceDao.getPreference("weekly_summary") == "true") {
                postWeeklySummary(gateEventDao)
            }
            Log.d(TAG, "Started app monitoring polling loop")

            while (isActive) {
                try {
                    val foregroundApp = UsageStatsHelper.getForegroundApp(applicationContext)
                    Log.d(TAG, "Polled foreground app: $foregroundApp (self package: $packageName)")
                    if (foregroundApp != null && foregroundApp != packageName) {
                        val target = appTargetDao.getTargetByPackage(foregroundApp)
                        Log.d(TAG, "Database target check for $foregroundApp: found target = $target, isActive = ${target?.isActive}")

                        if (target != null && target.isActive &&
                            isWithinBlockingSchedule(preferenceDao.getPreference("blocking_schedule")) &&
                            !hasExceededDailyBudget(preferenceDao.getPreference("daily_budget"), foregroundApp)
                        ) {
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
                                val delaySeconds = calculateDelaySeconds(
                                    target.delaySeconds,
                                    preferenceDao.getPreference("focus_mode") == "true",
                                    preferenceDao.getPreference("escalation_friction") != "false",
                                    gateEventDao.getCancelledCountForPackage(
                                        target.packageName,
                                        java.util.Calendar.getInstance().apply {
                                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                                            set(java.util.Calendar.MINUTE, 0)
                                            set(java.util.Calendar.SECOND, 0)
                                            set(java.util.Calendar.MILLISECOND, 0)
                                        }.timeInMillis
                                    )
                                )
                                OverlayWindowManager.showOverlay(
                                    applicationContext,
                                    target.packageName,
                                    target.appName,
                                    delaySeconds
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

    private fun isWithinBlockingSchedule(schedule: String?): Boolean {
        if (schedule.isNullOrBlank()) return true
        val parts = schedule.replace('.', ':').split('-').map { it.trim() }
        if (parts.size != 2) return true
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = format.format(Date())
        val start = runCatching { format.parse(parts[0]) }.getOrNull() ?: return true
        val end = runCatching { format.parse(parts[1]) }.getOrNull() ?: return true
        val current = runCatching { format.parse(now) }.getOrNull() ?: return true
        return if (start <= end) current >= start && current <= end else current >= start || current <= end
    }

    private fun hasExceededDailyBudget(budget: String?, packageName: String): Boolean {
        val budgetMinutes = budget?.filter(Char::isDigit)?.toIntOrNull() ?: return false
        val statsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            ?: return false
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val usage = statsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        ).firstOrNull { it.packageName == packageName } ?: return false
        return usage.totalTimeInForeground >= budgetMinutes * 60_000L
    }

    private fun calculateDelaySeconds(
        baseDelay: Int,
        focusMode: Boolean,
        escalationFriction: Boolean,
        cancelledToday: Int
    ): Int {
        val focusDelay = if (focusMode) maxOf(baseDelay, 10) else baseDelay
        val escalationDelay = if (escalationFriction) cancelledToday.coerceAtMost(5) else 0
        return (focusDelay + escalationDelay).coerceIn(3, 30)
    }

    private fun updateServiceNotification(reflectionEnabled: Boolean) {
        val text = if (reflectionEnabled) {
            "Jeda refleksi aktif untuk membantu kamu tetap sadar."
        } else {
            "Pemantauan aplikasi target sedang aktif."
        }
        getSystemService(NotificationManager::class.java)?.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Seremind Aktif")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build()
        )
    }

    private suspend fun postWeeklySummary(gateEventDao: com.almagribii.seremind.data.local.dao.GateEventDao) {
        if (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) return
        val start = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val events = gateEventDao.getEventsSince(start)
        getSystemService(NotificationManager::class.java)?.notify(
            NOTIFICATION_ID + 1,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Ringkasan mingguan Seremind")
                .setContentText("${events.size} jeda tercatat minggu ini. Lihat jurnalmu untuk refleksi.")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setAutoCancel(true)
                .build()
        )
    }

}
