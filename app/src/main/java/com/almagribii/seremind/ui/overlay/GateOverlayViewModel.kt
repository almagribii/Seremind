package com.almagribii.seremind.ui.overlay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.GateEvent
import com.almagribii.seremind.data.remote.GeminiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GateOverlayUiState(
    val appName: String = "Aplikasi",
    val packageName: String = "",
    val question: String = "Apa yang sedang kamu cari?",
    val selectedReason: String? = null,
    val countdownSeconds: Int = 5,
    val isOpenedEnabled: Boolean = false,
    val isLoading: Boolean = true
)

class GateOverlayViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val gateDao = db.gateEventDao()
    private val geminiService = GeminiApiService()

    private val _uiState = MutableStateFlow(GateOverlayUiState())
    val uiState: StateFlow<GateOverlayUiState> = _uiState.asStateFlow()

    fun initGate(packageName: String, appName: String, delaySeconds: Int) {
        _uiState.value = _uiState.value.copy(
            appName = appName,
            packageName = packageName,
            countdownSeconds = delaySeconds,
            isOpenedEnabled = false,
            isLoading = true
        )

        viewModelScope.launch(Dispatchers.IO) {
            val q = geminiService.generateGateQuestion(appName, null)
            _uiState.value = _uiState.value.copy(question = q, isLoading = false)

            // Start countdown
            var currentSec = delaySeconds
            while (currentSec > 0) {
                delay(1000)
                currentSec--
                _uiState.value = _uiState.value.copy(countdownSeconds = currentSec)
            }
            _uiState.value = _uiState.value.copy(isOpenedEnabled = true)
        }
    }

    fun selectReason(reason: String) {
        _uiState.value = _uiState.value.copy(selectedReason = reason)
    }

    fun recordEvent(action: String, onCompleted: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val event = GateEvent(
                packageName = state.packageName,
                timestamp = System.currentTimeMillis(),
                reason = state.selectedReason ?: "Lainnya",
                action = action,
                aiQuestion = state.question
            )
            gateDao.insert(event)
            viewModelScope.launch(Dispatchers.Main) {
                onCompleted()
            }
        }
    }
}
