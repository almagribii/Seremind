package com.almagribii.seremind.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.UserPreference
import com.almagribii.seremind.service.AppDetectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PresetAppItem(
    val packageName: String,
    val appName: String
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val prefDao = db.userPreferenceDao()
    private val appTargetDao = db.appTargetDao()

    private val _presetApps = MutableStateFlow(
        RecommendedApps.items.map { (packageName, appName) ->
            PresetAppItem(packageName, appName)
        }
    )
    val presetApps: StateFlow<List<PresetAppItem>> = _presetApps.asStateFlow()

    private val _defaultDelay = MutableStateFlow(5)
    val defaultDelay: StateFlow<Int> = _defaultDelay.asStateFlow()

    fun setDefaultDelay(delay: Int) {
        _defaultDelay.value = delay
    }

    fun completeOnboarding(onFinished: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            appTargetDao.deleteByPackages(RecommendedApps.packageNames)
            prefDao.setPreference(UserPreference("onboarding_completed", "true"))
            prefDao.setPreference(UserPreference("default_delay", _defaultDelay.value.toString()))

            val context = getApplication<Application>()
            AppDetectionService.startService(context)

            viewModelScope.launch(Dispatchers.Main) {
                onFinished()
            }
        }
    }
}
