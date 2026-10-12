package com.almagribii.seremind.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.AppTarget
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
    val isLoading: Boolean = false
)

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val appTargetDao = db.appTargetDao()

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

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = InstalledAppHelper.getInstalledApps(getApplication())
            _uiState.value = _uiState.value.copy(installedApps = apps)
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

    fun addTarget(packageName: String, appName: String, delaySeconds: Int = 5) {
        viewModelScope.launch(Dispatchers.IO) {
            // Check if already exists
            val existing = appTargetDao.getTargetByPackage(packageName)
            if (existing == null) {
                val newTarget = AppTarget(packageName = packageName, appName = appName, isActive = true, delaySeconds = delaySeconds)
                appTargetDao.insert(newTarget)
                loadTargets()
            }
        }
    }
}
