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
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val defaultDelay: Int = 5,
    val isDarkTheme: Boolean = false,
    val hasUsagePermission: Boolean = false,
    val hasOverlayPermission: Boolean = false
    ,val escalationFriction: Boolean = true
    ,val focusMode: Boolean = false
    ,val reflectionReminder: Boolean = true
    ,val weeklySummary: Boolean = true
    ,val aiJournalEnabled: Boolean = false
    ,val blockingSchedule: String = "22.00 - 06.00"
    ,val dailyBudget: String = "Belum diatur"
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
            _uiState.value = _uiState.value.copy(
                defaultDelay = delay,
                escalationFriction = prefDao.getPreference("escalation_friction") != "false",
                focusMode = prefDao.getPreference("focus_mode") == "true",
                reflectionReminder = prefDao.getPreference("reflection_reminder") != "false",
                weeklySummary = prefDao.getPreference("weekly_summary") != "false",
                aiJournalEnabled = prefDao.getPreference("ai_journal_enabled") == "true",
                blockingSchedule = prefDao.getPreference("blocking_schedule") ?: "22.00 - 06.00",
                dailyBudget = prefDao.getPreference("daily_budget") ?: "Belum diatur"
            )
        }

        checkPermissions()
    }

    fun setToggle(key: String, value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefDao.setPreference(UserPreference(key, value.toString()))
            _uiState.value = when (key) {
                "escalation_friction" -> _uiState.value.copy(escalationFriction = value)
                "focus_mode" -> _uiState.value.copy(focusMode = value)
                "reflection_reminder" -> _uiState.value.copy(reflectionReminder = value)
                "weekly_summary" -> _uiState.value.copy(weeklySummary = value)
                "ai_journal_enabled" -> _uiState.value.copy(aiJournalEnabled = value)
                else -> _uiState.value
            }
        }
    }

    fun setSchedule(value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefDao.setPreference(UserPreference("blocking_schedule", value))
            _uiState.value = _uiState.value.copy(blockingSchedule = value)
        }
    }

    fun setBudget(value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefDao.setPreference(UserPreference("daily_budget", value))
            _uiState.value = _uiState.value.copy(dailyBudget = value)
        }
    }

    fun deleteAllData(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val database = SeremindDatabase.getDatabase(getApplication())
            database.gateEventDao().deleteAll()
            database.journalEntryDao().deleteAll()
            database.recoveryEventDao().deleteAll()
            database.appTargetDao().deleteAll()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun exportData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val database = SeremindDatabase.getDatabase(getApplication())
            val events = database.gateEventDao().getEventsSince(0)
            val journals = database.journalEntryDao().getAllEntries()
            val text = buildString {
                appendLine("Seremind - Ekspor Data")
                appendLine("Gate events: ${events.size}")
                events.forEach { appendLine("${it.timestamp},${it.packageName},${it.action},${it.reason}") }
                appendLine("\nJournal entries: ${journals.size}")
                journals.forEach { appendLine("${it.timestamp},${it.emotionTag},${it.content}") }
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Ekspor data Seremind")
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }, "Ekspor data Seremind"))
            }
        }
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
