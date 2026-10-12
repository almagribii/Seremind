package com.almagribii.seremind.util

import android.content.Context
import android.content.Intent
import com.almagribii.seremind.ui.overlay.GateOverlayActivity

object OverlayHelper {

    fun showGateOverlay(context: Context, packageName: String, appName: String, delaySeconds: Int) {
        val intent = Intent(context, GateOverlayActivity::class.java).apply {
            putExtra("package_name", packageName)
            putExtra("app_name", appName)
            putExtra("delay_seconds", delaySeconds)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
