package com.almagribii.seremind.ui.recovery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.RecoveryEvent
import com.almagribii.seremind.data.remote.GeminiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecoveryUiState(
    val selectedEmotion: String? = null,
    val aiResponse: String = "Bagaimana perasaanmu setelah sesi ini?",
    val isLoading: Boolean = false
)

class RecoveryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val recoveryDao = db.recoveryEventDao()
    private val geminiService = GeminiApiService()

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    fun selectEmotion(emotion: String) {
        _uiState.value = _uiState.value.copy(selectedEmotion = emotion, isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val response = geminiService.generateRecoveryResponse(emotion)
            recoveryDao.insert(
                RecoveryEvent(
                    timestamp = System.currentTimeMillis(),
                    emotion = emotion,
                    aiResponse = response
                )
            )
            _uiState.value = _uiState.value.copy(aiResponse = response, isLoading = false)
        }
    }
}
