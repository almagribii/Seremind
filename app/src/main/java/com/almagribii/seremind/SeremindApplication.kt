package com.almagribii.seremind

import android.app.Application
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.service.AppDetectionService
import com.almagribii.seremind.ui.onboarding.RecommendedApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SeremindApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize Database instance
        SeremindDatabase.getDatabase(this)
        migrateLegacyPresetTargets()

        // Safely attempt to start AppDetectionService if permission is granted
        AppDetectionService.startService(this)
    }

    private fun migrateLegacyPresetTargets() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = SeremindDatabase.getDatabase(applicationContext)
            val preferences = database.userPreferenceDao()
            if (preferences.getPreference("legacy_preset_targets_removed") != "true") {
                database.appTargetDao().deleteByPackages(RecommendedApps.packageNames)
                preferences.setPreference(
                    com.almagribii.seremind.data.local.entity.UserPreference(
                        "legacy_preset_targets_removed",
                        "true"
                        )
                )
            }
        }
    }
}
