package com.almagribii.seremind.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.UserPreference
import android.content.Intent
import com.almagribii.seremind.ui.overlay.GateOverlayActivity
import com.almagribii.seremind.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val defaultDelay: Int = 5,
    val isDarkTheme: Boolean = false,
    val hasUsagePermission: Boolean = false,
    val hasOverlayPermission: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefDao = SeremindDatabase.getDatabase(application).userPreferenceDao()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        checkPermissions()
    }

    fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val delayStr = prefDao.getPreference("default_delay")
            val delay = delayStr?.toIntOrNull() ?: 5
            _uiState.value = _uiState.value.copy(defaultDelay = delay)
        }
        checkPermissions()
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val hasUsage = PermissionHelper.hasUsageStatsPermission(context)
        val hasOverlay = PermissionHelper.hasOverlayPermission(context)
        _uiState.value = _uiState.value.copy(
            hasUsagePermission = hasUsage,
            hasOverlayPermission = hasOverlay
        )
    }

    fun setDefaultDelay(delay: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            prefDao.setPreference(UserPreference("default_delay", delay.toString()))
            _uiState.value = _uiState.value.copy(defaultDelay = delay)
        }
    }

    fun requestUsagePermission(context: Context) {
        PermissionHelper.requestUsageStatsPermission(context)
    }

    fun requestOverlayPermission(context: Context) {
        PermissionHelper.requestOverlayPermission(context)
    }

    fun testOverlay(context: Context) {
        if (!PermissionHelper.hasOverlayPermission(context)) {
            PermissionHelper.requestOverlayPermission(context)
        } else {
            val intent = Intent(context, GateOverlayActivity::class.java).apply {
                putExtra("package_name", context.packageName)
                putExtra("app_name", "Seremind (Test)")
                putExtra("delay_seconds", 5)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
