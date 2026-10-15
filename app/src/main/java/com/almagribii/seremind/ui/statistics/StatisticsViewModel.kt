package com.almagribii.seremind.ui.statistics

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.BuildConfig
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.dao.HourCount
import com.almagribii.seremind.data.local.dao.ReasonCount
import java.util.Calendar
import java.util.Locale
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

data class StatisticsAnalogyResult(
    val analogy: String? = null,
    val feedback: String? = null,
    val error: String? = null
)

data class StatisticsUiState(
    val filterPeriod: String = "Harian", // Harian, Mingguan, Bulanan
    val openedCount: Int = 0,
    val cancelledCount: Int = 0,
    val reasons: List<ReasonCount> = emptyList(),
    val hourly: List<HourCount> = emptyList(),
    val trendPercent: Int? = null,
    val peakHour: String? = null,
    val aiAnalogy: String? = null,
    val aiFeedback: String? = null,
    val isAiLoading: Boolean = false
)

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
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

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStats("Harian")
    }

    fun setFilterPeriod(period: String) {
        loadStats(period)
    }

    fun runAiAnalysis() {
        if (_uiState.value.isAiLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isAiLoading = true)

            val period = _uiState.value.filterPeriod
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
            val opened = _uiState.value.openedCount
            val cancelled = _uiState.value.cancelledCount
            val currentTotal = opened + cancelled
            val reasons = _uiState.value.reasons
            val hourly = _uiState.value.hourly
            val peak = _uiState.value.peakHour

            val events = gateDao.getEventsSince(startTime)
            val targetsMap = appTargetDao.getAllTargets().associateBy { it.packageName }

            val appSummary = events.groupingBy { event ->
                targetsMap[event.packageName]?.appName ?: deriveAppName(event.packageName)
            }.eachCount()
                .entries.sortedByDescending { it.value }
                .take(5)
                .joinToString { "${it.key} (${it.value}x)" }
                .ifBlank { "Belum ada aplikasi tercatat" }

            val reasonSummary = reasons.take(5)
                .joinToString { "${it.reason} (${it.count}x)" }
                .ifBlank { "Belum ada alasan tercatat" }

            val peakSummary = if (peak != null) {
                val countAtPeak = hourly.firstOrNull { it.hour == peak }?.count ?: 0
                "$peak.00–${(peak.toIntOrNull() ?: 0) + 1}.00 (${countAtPeak}x)"
            } else {
                "Belum ada jam puncak"
            }

            val sortedEvents = events.sortedBy { it.timestamp }
            val transitions = mutableMapOf<String, Int>()
            for (i in 0 until sortedEvents.size - 1) {
                val current = sortedEvents[i]
                val next = sortedEvents[i + 1]
                if (next.timestamp - current.timestamp in 1..10 * 60 * 1000 && current.packageName != next.packageName) {
                    val app1 = targetsMap[current.packageName]?.appName ?: deriveAppName(current.packageName)
                    val app2 = targetsMap[next.packageName]?.appName ?: deriveAppName(next.packageName)
                    val pair = "$app1 -> $app2"
                    transitions[pair] = (transitions[pair] ?: 0) + 1
                }
            }
            val sequenceSummary = transitions.entries
                .sortedByDescending { it.value }
                .take(3)
                .joinToString { "${it.key} (${it.value}x berturut-turut < 10 mnt)" }
                .ifBlank { "Tidak terdeteksi transisi beruntun antar aplikasi" }

            val analogyResult = if (currentTotal == 0) {
                StatisticsAnalogyResult(
                    analogy = "Belum ada aktivitas gate yang tercatat pada periode $period.",
                    feedback = "Gunakan aplikasi target secara teratur agar Seremind dapat menganalisis pola dan memberikan saran untukmu."
                )
            } else {
                generateStatisticsAnalogyInternal(
                    period = period,
                    totalOpened = opened,
                    totalCancelled = cancelled,
                    peakHoursSummary = peakSummary,
                    reasonsSummary = reasonSummary,
                    appsSummary = appSummary,
                    sequencesSummary = sequenceSummary
                )
            }

            _uiState.value = _uiState.value.copy(
                aiAnalogy = analogyResult.analogy,
                aiFeedback = analogyResult.feedback,
                isAiLoading = false
            )
        }
    }

    private suspend fun generateStatisticsAnalogyInternal(
        period: String,
        totalOpened: Int,
        totalCancelled: Int,
        peakHoursSummary: String,
        reasonsSummary: String,
        appsSummary: String,
        sequencesSummary: String
    ): StatisticsAnalogyResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext StatisticsAnalogyResult(error = "API key Gemini belum dikonfigurasi di local.properties.")
        }
        try {
            withTimeout(15_000) {
                val prompt = """
                    Analisis data agregat penggunaan aplikasi pengguna selama periode $period berikut:
                    - Total dibuka: $totalOpened, Total dicegah/dibatalkan: $totalCancelled
                    - Puncak jam pembukaan: $peakHoursSummary
                    - Alasan terbanyak: $reasonsSummary
                    - Aplikasi terbanyak: $appsSummary
                    - Transisi/urutan antar aplikasi: $sequencesSummary
                    - Seed variasi: ${System.currentTimeMillis()}

                    Tugasmu:
                    1) Temukan 1 korelasi/pola psikologis yang menarik atau tersembunyi antara aplikasi, jam sibuk, atau alasan yang menonjol. Sajikan pola ini dalam 1-2 kalimat yang empatik, ramah, dan tajam (tanpa menghakimi).
                    2) Berikan 1 saran/feedback konkret yang realistis dan dapat dilakukan pengguna ke depan untuk meningkatkan kesadaran digital.

                    Kembalikan HANYA JSON valid dengan format persis berikut (tanpa blok markdown atau teks lain):
                    {"analogy": "isi korelasi pola 1-2 kalimat", "feedback": "saran/feedback konkret ke depan"}
                """.trimIndent()

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
                        Log.e("StatisticsViewModel", "Gemini HTTP ${response.code}: $apiMessage")
                        return@use StatisticsAnalogyResult(error = "Gemini Error (${response.code}): ${apiMessage.ifBlank { "respons server tidak valid" }}")
                    }
                    val text = JSONObject(rawBody)
                        .optJSONArray("candidates")?.takeIf { it.length() > 0 }
                        ?.getJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                        ?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("text")
                        ?.trim()?.takeIf { it.isNotBlank() }

                    if (text != null) {
                        val cleanJson = text.replace("```json", "").replace("```", "").trim()
                        val jsonObject = JSONObject(cleanJson)
                        val analogy = jsonObject.optString("analogy").trim()
                        val feedback = jsonObject.optString("feedback").trim()
                        if (analogy.isNotBlank() && feedback.isNotBlank()) {
                            return@use StatisticsAnalogyResult(
                                analogy = analogy,
                                feedback = feedback
                            )
                        }
                    }
                    StatisticsAnalogyResult(error = "Format respons AI Statistik tidak dikenali.")
                }
            }
        } catch (e: Exception) {
            StatisticsAnalogyResult(error = "Gagal memuat analisis AI: ${e.message}")
        }
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
            val endTime = System.currentTimeMillis()
            val startTime = calendar.timeInMillis
            val periodLength = endTime - startTime
            val previousStart = startTime - periodLength

            val opened = gateDao.getOpenedCount(startTime)
            val cancelled = gateDao.getCancelledCount(startTime)
            val reasons = gateDao.getReasonDistribution(startTime)
            val hourly = gateDao.getHourlyDistribution(startTime)
            val previousTotal = gateDao.getOpenedCount(previousStart) + gateDao.getCancelledCount(previousStart)
            val currentTotal = opened + cancelled
            val trend = if (previousTotal == 0) null
            else ((currentTotal - previousTotal) * 100f / previousTotal).toInt()
            val peak = hourly.maxByOrNull { it.count }?.hour

            _uiState.value = StatisticsUiState(
                filterPeriod = period,
                openedCount = opened,
                cancelledCount = cancelled,
                reasons = reasons,
                hourly = hourly,
                trendPercent = trend,
                peakHour = peak,
                aiAnalogy = null,
                aiFeedback = null,
                isAiLoading = false
            )
        }
    }

    private fun deriveAppName(packageName: String): String {
        return when {
            packageName.contains("instagram", true) -> "Instagram"
            packageName.contains("tiktok", true) || packageName.contains("musically", true) -> "TikTok"
            packageName.contains("youtube", true) -> "YouTube"
            packageName.contains("whatsapp", true) -> "WhatsApp"
            packageName.contains("twitter", true) || packageName.contains("x.android", true) -> "X/Twitter"
            packageName.contains("facebook", true) -> "Facebook"
            packageName.contains("telegram", true) -> "Telegram"
            else -> packageName.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
