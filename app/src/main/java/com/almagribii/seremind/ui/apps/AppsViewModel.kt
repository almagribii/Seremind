package com.almagribii.seremind.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.AppTarget
import com.almagribii.seremind.data.local.entity.GateEvent
import com.almagribii.seremind.util.InstalledAppHelper
import com.almagribii.seremind.util.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppsUiState(
    val targets: List<AppTarget> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isInstalledAppsLoading: Boolean = false,
    val selectedTarget: AppTarget? = null,
    val selectedEvents: List<GateEvent> = emptyList(),
    val showDeleteConfirmation: Boolean = false
)

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val appTargetDao = db.appTargetDao()
    private val gateEventDao = db.gateEventDao()

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    init {
        loadTargets()
        loadInstalledApps()
    }

    fun loadTargets() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = appTargetDao.getAllTargets()
            _uiState.value = _uiState.value.copy(targets = list)
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isInstalledAppsLoading = true)
            val apps = InstalledAppHelper.getInstalledApps(getApplication())
            _uiState.value = _uiState.value.copy(
                installedApps = apps,
                isInstalledAppsLoading = false
            )
        }
    }

    fun toggleTargetActive(target: AppTarget, isActive: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = target.copy(isActive = isActive)
            appTargetDao.update(updated)
            loadTargets()
        }
    }

    fun updateDelay(target: AppTarget, delaySeconds: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = target.copy(delaySeconds = delaySeconds)
            appTargetDao.update(updated)
            loadTargets()
        }
    }

    fun updateTargetSettings(target: AppTarget, delaySeconds: Int, maxSessionMinutes: Int, cooldownMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = target.copy(
                delaySeconds = delaySeconds,
                maxSessionMinutes = maxSessionMinutes,
                cooldownMinutes = cooldownMinutes
            )
            appTargetDao.update(updated)
            loadTargets()
            if (_uiState.value.selectedTarget?.id == target.id) {
                _uiState.value = _uiState.value.copy(selectedTarget = updated)
            }
        }
    }

    fun addTarget(packageName: String, appName: String, delaySeconds: Int = 5) {
        viewModelScope.launch(Dispatchers.IO) {
            // Check if already exists
            val existing = appTargetDao.getTargetByPackage(packageName)
            if (existing == null) {
                val configuredDelay = SeremindDatabase.getDatabase(getApplication<Application>())
                    .userPreferenceDao().getPreference("default_delay")?.toIntOrNull()
                    ?.coerceIn(3, 30) ?: delaySeconds
                val newTarget = AppTarget(
                    packageName = packageName,
                    appName = appName,
                    isActive = true,
                    delaySeconds = configuredDelay,
                    maxSessionMinutes = 5, // Default 5 menit sesi
                    cooldownMinutes = 0     // Default Nonaktif istirahat
                )
                appTargetDao.insert(newTarget)
                loadTargets()
            }
        }
    }

    fun selectTarget(target: AppTarget) {
        viewModelScope.launch(Dispatchers.IO) {
            val events = gateEventDao.getEventsForPackage(target.packageName)
            _uiState.value = _uiState.value.copy(selectedTarget = target, selectedEvents = events)
        }
    }

    fun closeTargetDetails() {
        _uiState.value = _uiState.value.copy(selectedTarget = null, selectedEvents = emptyList())
    }

    fun requestDelete(target: AppTarget) {
        _uiState.value = _uiState.value.copy(selectedTarget = target, showDeleteConfirmation = true)
    }

    fun dismissDelete() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = false)
    }

    fun deleteTarget(target: AppTarget) {
        viewModelScope.launch(Dispatchers.IO) {
            appTargetDao.delete(target)
            _uiState.value = _uiState.value.copy(
                targets = _uiState.value.targets.filterNot { it.id == target.id },
                selectedTarget = null,
                selectedEvents = emptyList(),
                showDeleteConfirmation = false
            )
        }
    }
}
