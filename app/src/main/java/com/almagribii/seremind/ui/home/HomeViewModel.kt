package com.almagribii.seremind.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.BuildConfig
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.dao.HourCount
import com.almagribii.seremind.data.local.entity.AppTarget
import com.almagribii.seremind.service.AppDetectionService
import com.almagribii.seremind.util.PermissionHelper
import java.util.Calendar
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
    private val apiKey = BuildConfig.GEMINI_API_KEY
        .ifEmpty { BuildConfig.GEMINI_API_KEY_JURNAL }
        .ifEmpty { BuildConfig.GEMINI_API_KEY_OVERLAY }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

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

            val prompt = generateHomePromptInternal()

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

    private suspend fun generateHomePromptInternal(): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "API key Gemini belum dikonfigurasi di local.properties."
        }
        try {
            withTimeout(15_000) {
                val prompt = "Buat 1 pertanyaan refleksi singkat dan hangat untuk membuka hari dalam Bahasa Indonesia (maksimal 120 karakter)."
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
                        Log.e("HomeViewModel", "Gemini HTTP ${response.code}: $apiMessage")
                        return@use "Gemini Error (${response.code}): ${apiMessage.ifBlank { "respons server tidak valid" }}"
                    }
                    val text = JSONObject(rawBody)
                        .optJSONArray("candidates")?.takeIf { it.length() > 0 }
                        ?.getJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                        ?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("text")
                        ?.trim()?.takeIf { it.isNotBlank() }

                    text ?: "Format respons AI Beranda tidak dikenali."
                }
            }
        } catch (e: Exception) {
            "Gagal memuat pertanyaan AI: ${e.message}"
        }
    }
}
