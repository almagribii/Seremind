package com.almagribii.seremind.util

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

object InstalledAppHelper {
    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        return resolveInfos.mapNotNull { resolveInfo ->
            try {
                val packageName = resolveInfo.activityInfo.packageName
                // Skip self package if desired, or keep it
                val appName = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                InstalledAppInfo(packageName, appName, icon)
            } catch (e: Exception) {
                null
            }
        }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
    }
}
