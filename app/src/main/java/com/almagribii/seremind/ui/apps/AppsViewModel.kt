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
    val showDeleteConfirmation: Boolean = false,
    val userMessage: String? = null
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

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
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
        if (!isActive && target.isCooldownActive()) {
            _uiState.value = _uiState.value.copy(
                userMessage = "🔒 ${target.appName} sedang dalam masa istirahat (sisa ${target.cooldownMinutesLeft()}m) dan tidak dapat dinonaktifkan!"
            )
            return
        }
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
        var finalCooldown = cooldownMinutes
        if (target.isCooldownActive()) {
            if (cooldownMinutes < target.cooldownMinutes) {
                finalCooldown = target.cooldownMinutes
                _uiState.value = _uiState.value.copy(
                    userMessage = "🔒 Masa istirahat sedang aktif! Waktu istirahat tidak dapat dikurangi atau dinonaktifkan."
                )
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val updated = target.copy(
                delaySeconds = delaySeconds,
                maxSessionMinutes = maxSessionMinutes,
                cooldownMinutes = finalCooldown
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
        if (target.isCooldownActive()) {
            _uiState.value = _uiState.value.copy(
                userMessage = "🔒 Aplikasi ${target.appName} sedang dalam masa istirahat (sisa ${target.cooldownMinutesLeft()}m) dan tidak dapat dihapus!"
            )
            return
        }
        _uiState.value = _uiState.value.copy(selectedTarget = target, showDeleteConfirmation = true)
    }

    fun dismissDelete() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = false)
    }

    fun deleteTarget(target: AppTarget) {
        if (target.isCooldownActive()) {
            _uiState.value = _uiState.value.copy(
                showDeleteConfirmation = false,
                userMessage = "🔒 Aplikasi ${target.appName} sedang dalam masa istirahat dan tidak dapat dihapus!"
            )
            return
        }
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
