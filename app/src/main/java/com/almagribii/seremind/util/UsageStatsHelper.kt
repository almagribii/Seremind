package com.almagribii.seremind.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log

object UsageStatsHelper {
    private const val TAG = "UsageStatsHelper"

    fun getForegroundApp(context: Context): String? {
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

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastApp = event.packageName
                    Log.d(TAG, "Activity resumed event: $lastApp")
                }
            } else {
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastApp = event.packageName
                    Log.d(TAG, "Move to foreground event: $lastApp")
                }
            }
        }

        if (lastApp != null) {
            Log.d(TAG, "Detected foreground app from events: $lastApp")
            return lastApp
        }

        Log.d(TAG, "No recent foreground app event detected")
        return null
    }
}
