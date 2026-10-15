package com.almagribii.seremind.ui.recovery

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.BuildConfig
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.RecoveryEvent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class RecoveryUiState(
    val selectedEmotion: String? = null,
    val aiResponse: String = "Bagaimana perasaanmu setelah sesi ini?",
    val isLoading: Boolean = false
)

class RecoveryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val recoveryDao = db.recoveryEventDao()
    private val apiKey = BuildConfig.GEMINI_API_KEY.ifEmpty { BuildConfig.GEMINI_API_KEY_JURNAL }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    fun selectEmotion(emotion: String) {
        _uiState.value = _uiState.value.copy(selectedEmotion = emotion, isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val response = generateRecoveryResponseInternal(emotion)
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

    private suspend fun generateRecoveryResponseInternal(emotion: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "API key Gemini belum dikonfigurasi di local.properties."
        }
        try {
            withTimeout(15_000) {
                val prompt = "Seseorang merasa '$emotion' setelah sesi penggunaan media sosial yang panjang. Berikan tanggapan yang empatik, menenangkan, penuh welas asih, dan tidak menghakimi (dalam Bahasa Indonesia, maksimal 2 kalimat)."
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    }))
                }
                val request = Request.Builder()
                    .url(url)
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val apiMessage = runCatching {
                            JSONObject(rawBody).optJSONObject("error")?.optString("message")
                        }.getOrNull().orEmpty()
                        Log.e("RecoveryViewModel", "Gemini HTTP ${response.code}: $apiMessage")
                        return@use "Gemini Error (${response.code}): ${apiMessage.ifBlank { "respons server tidak valid" }}"
                    }
                    val text = JSONObject(rawBody)
                        .optJSONArray("candidates")?.takeIf { it.length() > 0 }
                        ?.getJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                        ?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("text")
                        ?.trim()?.takeIf { it.isNotBlank() }

                    text ?: "Format respons AI Pemulihan tidak dikenali."
                }
            }
        } catch (e: Exception) {
            "Gagal memuat tanggapan AI: ${e.message}"
        }
    }
}
