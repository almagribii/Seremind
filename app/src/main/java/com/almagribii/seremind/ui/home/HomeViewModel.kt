package com.almagribii.seremind.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.dao.HourCount
import com.almagribii.seremind.data.local.entity.AppTarget
import com.almagribii.seremind.data.remote.GeminiApiService
import com.almagribii.seremind.service.AppDetectionService
import com.almagribii.seremind.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val openedCount: Int = 0,
    val cancelledCount: Int = 0,
    val peakHour: String = "-",
    val peakReason: String = "-",
    val aiPrompt: String = "Apa hal berharga yang kamu lewatkan hari ini karena sibuk menatap layar?",
    val activeTargets: List<AppTarget> = emptyList(),
    val hasUsagePermission: Boolean = true,
    val hasOverlayPermission: Boolean = true,
    val isLoading: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val gateDao = db.gateEventDao()
    private val appTargetDao = db.appTargetDao()
    private val geminiService = GeminiApiService()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val hasUsage = PermissionHelper.hasUsageStatsPermission(context)
            val hasOverlay = PermissionHelper.hasOverlayPermission(context)

            if (hasUsage && hasOverlay) {
                AppDetectionService.startService(context)
            }

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis

            val opened = gateDao.getOpenedCount(startOfDay)
            val cancelled = gateDao.getCancelledCount(startOfDay)
            val hourly = gateDao.getHourlyDistribution(startOfDay)
            val reasons = gateDao.getReasonDistribution(startOfDay)
            val peak = findPeakHour(hourly)
            val targets = appTargetDao.getActiveTargets()

            val prompt = geminiService.generateJournalPrompt()

            _uiState.value = HomeUiState(
                openedCount = opened,
                cancelledCount = cancelled,
                peakHour = peak,
                peakReason = reasons.firstOrNull()?.reason ?: "-",
                aiPrompt = prompt,
                activeTargets = targets,
                hasUsagePermission = hasUsage,
                hasOverlayPermission = hasOverlay
            )
        }
    }

    fun requestUsagePermission() {
        PermissionHelper.requestUsageStatsPermission(getApplication())
    }

    fun requestOverlayPermission() {
        PermissionHelper.requestOverlayPermission(getApplication())
    }

    private fun findPeakHour(hourly: List<HourCount>): String {
        if (hourly.isEmpty()) return "-"
        val maxHour = hourly.maxByOrNull { it.count }
        return if (maxHour != null) "${maxHour.hour}:00" else "-"
    }
}
