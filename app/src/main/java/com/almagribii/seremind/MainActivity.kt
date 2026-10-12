package com.almagribii.seremind

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.service.AppDetectionService
import com.almagribii.seremind.ui.navigation.Screen
import com.almagribii.seremind.ui.navigation.SeremindNavGraph
import com.almagribii.seremind.ui.theme.SeremindTheme
import com.almagribii.seremind.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        val prefDao = SeremindDatabase.getDatabase(applicationContext).userPreferenceDao()
        val isOnboardingCompleted = runBlocking {
            withContext(Dispatchers.IO) {
                prefDao.getPreference("onboarding_completed") == "true"
            }
        }

        val action = intent?.action
        val packageName = intent?.getStringExtra("package_name")
        val appName = intent?.getStringExtra("app_name")
        val delaySeconds = intent?.getIntExtra("delay_seconds", 5) ?: 5

        val startDestination = when {
            action == "ACTION_SHOW_GATE" && packageName != null && appName != null -> {
                Screen.GateOverlay.createRoute(packageName, appName, delaySeconds)
            }
            !isOnboardingCompleted -> Screen.Onboarding.route
            else -> Screen.Home.route
        }

        setContent {
            SeremindTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SeremindNavGraph(startDestination = startDestination)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasUsageStatsPermission(this) && PermissionHelper.hasOverlayPermission(this)) {
            val serviceIntent = Intent(this, AppDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }
}
