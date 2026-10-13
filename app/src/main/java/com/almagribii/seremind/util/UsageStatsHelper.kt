package com.almagribii.seremind.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log

object UsageStatsHelper {
    private const val TAG = "UsageStatsHelper"

    data class AppLifecycleState(
        val packageName: String,
        val isForeground: Boolean,
        val timestamp: Long,
        val foregroundPackages: List<String>
    )

    fun getForegroundApp(context: Context): String? {
        return getLatestAppLifecycleState(context)
            ?.takeIf { it.isForeground }
            ?.packageName
    }

    fun getLatestAppLifecycleState(context: Context): AppLifecycleState? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: run {
            Log.d(TAG, "UsageStatsManager is null")
            return null
        }
        val currentTime = System.currentTimeMillis()
        // Use only recent lifecycle events. Aggregate stats can report an old app
        // as foreground and would cause gates to appear after the user has left it.
        val events = usageStatsManager.queryEvents(currentTime - 3000, currentTime)
        val event = UsageEvents.Event()
        var lastApp: String? = null
        var lastEventWasBackground = false
        var lastEventTimestamp = 0L
        val foregroundPackages = mutableListOf<String>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastApp = event.packageName
                    lastEventWasBackground = false
                    lastEventTimestamp = event.timeStamp
                    foregroundPackages += event.packageName
                    Log.d(TAG, "Activity resumed event: $lastApp")
                } else if (
                    event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                    event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
                ) {
                    lastApp = event.packageName
                    lastEventWasBackground = true
                    lastEventTimestamp = event.timeStamp
                    Log.d(TAG, "Activity background event: $lastApp")
                }
            } else {
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastApp = event.packageName
                    lastEventWasBackground = false
                    lastEventTimestamp = event.timeStamp
                    foregroundPackages += event.packageName
                    Log.d(TAG, "Move to foreground event: $lastApp")
                } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    lastApp = event.packageName
                    lastEventWasBackground = true
                    lastEventTimestamp = event.timeStamp
                    Log.d(TAG, "Move to background event: $lastApp")
                }
            }
        }

        if (lastApp != null) {
            Log.d(TAG, "Detected latest app lifecycle event: $lastApp, foreground=${!lastEventWasBackground}")
            return AppLifecycleState(
                packageName = lastApp,
                isForeground = !lastEventWasBackground,
                timestamp = lastEventTimestamp,
                foregroundPackages = foregroundPackages
            )
        }

        Log.d(TAG, "No recent foreground app event detected")
        return null
    }
}
