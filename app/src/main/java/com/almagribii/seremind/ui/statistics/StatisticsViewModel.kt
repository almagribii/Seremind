package com.almagribii.seremind.ui.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.dao.HourCount
import com.almagribii.seremind.data.local.dao.ReasonCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class StatisticsUiState(
    val filterPeriod: String = "Harian", // Harian, Mingguan, Bulanan
    val openedCount: Int = 0,
    val cancelledCount: Int = 0,
    val reasons: List<ReasonCount> = emptyList(),
    val hourly: List<HourCount> = emptyList()
)

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val gateDao = db.gateEventDao()

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStats("Harian")
    }

    fun setFilterPeriod(period: String) {
        loadStats(period)
    }

    private fun loadStats(period: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            when (period) {
                "Harian" -> {
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                }
                "Mingguan" -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -7)
                }
                "Bulanan" -> {
                    calendar.add(Calendar.MONTH, -1)
                }
            }
            val startTime = calendar.timeInMillis

            val opened = gateDao.getOpenedCount(startTime)
            val cancelled = gateDao.getCancelledCount(startTime)
            val reasons = gateDao.getReasonDistribution(startTime)
            val hourly = gateDao.getHourlyDistribution(startTime)

            _uiState.value = StatisticsUiState(
                filterPeriod = period,
                openedCount = opened,
                cancelledCount = cancelled,
                reasons = reasons,
                hourly = hourly
            )
        }
    }
}
