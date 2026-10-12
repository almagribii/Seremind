package com.almagribii.seremind.ui.onboarding

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.AppTarget
import com.almagribii.seremind.data.local.entity.UserPreference
import com.almagribii.seremind.service.AppDetectionService
import com.almagribii.seremind.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PresetAppItem(
    val packageName: String,
    val appName: String,
    val isSelected: Boolean = true
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val prefDao = db.userPreferenceDao()
    private val appTargetDao = db.appTargetDao()

    private val _presetApps = MutableStateFlow(
        listOf(
            PresetAppItem("com.zhiliaoapp.musically", "TikTok", true),
            PresetAppItem("com.instagram.android", "Instagram", true),
            PresetAppItem("com.google.android.youtube", "YouTube", true),
            PresetAppItem("com.twitter.android", "Twitter/X", true),
            PresetAppItem("com.facebook.katana", "Facebook / Games", true)
        )
    )
    val presetApps: StateFlow<List<PresetAppItem>> = _presetApps.asStateFlow()

    private val _defaultDelay = MutableStateFlow(5)
    val defaultDelay: StateFlow<Int> = _defaultDelay.asStateFlow()

    fun toggleAppSelection(packageName: String) {
        _presetApps.value = _presetApps.value.map { app ->
            if (app.packageName == packageName) app.copy(isSelected = !app.isSelected) else app
        }
    }

    fun setDefaultDelay(delay: Int) {
        _defaultDelay.value = delay
    }

    fun completeOnboarding(onFinished: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            prefDao.setPreference(UserPreference("onboarding_completed", "true"))
            prefDao.setPreference(UserPreference("default_delay", _defaultDelay.value.toString()))

            for (item in _presetApps.value) {
                if (item.isSelected) {
                    val target = AppTarget(
                        packageName = item.packageName,
                        appName = item.appName,
                        isActive = true,
                        delaySeconds = _defaultDelay.value
                    )
                    appTargetDao.insert(target)
                }
            }

            val context = getApplication<Application>()
            if (PermissionHelper.hasUsageStatsPermission(context)) {
                val serviceIntent = Intent(context, AppDetectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }

            viewModelScope.launch(Dispatchers.Main) {
                onFinished()
            }
        }
    }
}
