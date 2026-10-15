package com.almagribii.seremind.ui.journal

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.BuildConfig
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.Article
import com.almagribii.seremind.data.local.entity.GateEvent
import com.almagribii.seremind.data.local.entity.JournalEntry
import com.almagribii.seremind.data.local.entity.UserPreference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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

data class JournalFeedbackResult(val feedback: String? = null, val error: String? = null)
data class JournalPromptResult(val prompt: String? = null, val error: String? = null)

data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
    val articles: List<Article> = emptyList(),
    val selectedTab: Int = 0,
    val currentPrompt: String? = null,
    val currentPromptError: String? = null,
    val isPromptLoading: Boolean = false,
    val isLoading: Boolean = false,
    val aiJournalEnabled: Boolean = false,
    val latestFeedback: String? = null,
    val processingFeedbackFor: Int? = null
)

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val journalDao = db.journalEntryDao()
    private val articleDao = db.articleDao()
    private val prefDao = db.userPreferenceDao()
    private val apiKey = BuildConfig.GEMINI_API_KEY_JURNAL.ifEmpty { BuildConfig.GEMINI_API_KEY }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        loadJournalData()
    }

    fun setSelectedTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
    }

    fun loadJournalData() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshJournalData()
        }
    }

    private suspend fun refreshJournalData() {
        var articles = articleDao.getAllArticles()
        if (articles.size != 7 || !articles.any { it.title.contains("Action Prediction Error", ignoreCase = true) }) {
            SeremindDatabase.prepopulateArticles(articleDao)
            articles = articleDao.getAllArticles()
        }
        _uiState.value = _uiState.value.copy(
            entries = journalDao.getAllEntries(),
            articles = articles,
            aiJournalEnabled = prefDao.getPreference("ai_journal_enabled") == "true"
        )
    }

    fun setAiJournalEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefDao.setPreference(UserPreference("ai_journal_enabled", enabled.toString()))
            _uiState.value = _uiState.value.copy(aiJournalEnabled = enabled)
        }
    }

    fun addJournalEntry(
        content: String,
        emotionTag: String?,
        entryType: String = "free",
        aiPrompt: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val aiEnabled = prefDao.getPreference("ai_journal_enabled") == "true"
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                latestFeedback = null
            )

            val feedbackResult = if (aiEnabled) {
                try {
                    val startOfDay = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    generateJournalFeedbackInternal(
                        content,
                        db.gateEventDao().getEventsSince(startOfDay)
                    )
                } catch (_: Exception) {
                    JournalFeedbackResult(
                        error = "AI tidak dapat membaca data gate hari ini. Jurnal tetap tersimpan."
                    )
                }
            } else {
                null
            }

            journalDao.insert(
                JournalEntry(
                    timestamp = System.currentTimeMillis(),
                    content = content,
                    emotionTag = emotionTag,
                    entryType = entryType,
                    aiPrompt = aiPrompt,
                    aiFeedback = feedbackResult?.feedback,
                    aiFeedbackError = feedbackResult?.error
                )
            )
            refreshJournalData()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                latestFeedback = feedbackResult?.feedback
            )
            kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun requestFeedback(entryId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = journalDao.getById(entryId) ?: return@launch
            _uiState.value = _uiState.value.copy(processingFeedbackFor = entryId)
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val result = generateJournalFeedbackInternal(
                entry.content,
                db.gateEventDao().getEventsSince(startOfDay)
            )
            journalDao.update(entry.copy(aiFeedback = result.feedback, aiFeedbackError = result.error))
            _uiState.value = _uiState.value.copy(
                processingFeedbackFor = null,
                latestFeedback = result.feedback
            )
            loadJournalData()
        }
    }

    fun deleteEntry(entry: JournalEntry, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            journalDao.delete(entry)
            loadJournalData()
            kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun fetchNewPrompt() {
        viewModelScope.launch(Dispatchers.IO) {
            fetchNewPromptInternal()
        }
    }

    private suspend fun fetchNewPromptInternal() {
        _uiState.value = _uiState.value.copy(
            isPromptLoading = true,
            currentPromptError = null
        )
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val gateEventsToday = db.gateEventDao().getEventsSince(startOfDay)
        val recentEntries = journalDao.getAllEntries().take(3)

        val result = generateContextualJournalPromptInternal(gateEventsToday, recentEntries)
        _uiState.value = _uiState.value.copy(
            currentPrompt = result.prompt,
            currentPromptError = result.error,
            isPromptLoading = false
        )
    }

    fun markArticleAsRead(article: Article) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = article.copy(isRead = true)
            articleDao.update(updated)
            loadJournalData()
        }
    }

    private suspend fun executeGeminiRequest(prompt: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalStateException("API key Gemini belum dikonfigurasi di local.properties.")
        }
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
                Log.e("JournalViewModel", "Gemini HTTP ${response.code}: $apiMessage")
                throw IllegalStateException("Gemini HTTP ${response.code}: ${apiMessage.ifBlank { "respons server tidak valid" }}")
            }
            val text = JSONObject(rawBody)
                .optJSONArray("candidates")?.takeIf { it.length() > 0 }
                ?.getJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("text")
                ?.trim()?.takeIf { it.isNotBlank() }

            text ?: throw IllegalStateException("Format respons Gemini tidak dikenali.")
        }
    }

    private suspend fun generateJournalFeedbackInternal(
        journalContent: String,
        gateEvents: List<GateEvent>
    ): JournalFeedbackResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext JournalFeedbackResult(error = "API key AI Jurnal belum dikonfigurasi di local.properties.")
        try {
            withTimeout(20_000) {
                val gateSummary = if (gateEvents.isEmpty()) {
                    "Belum ada aktivitas terinterupsi hari ini."
                } else {
                    val appCounts = gateEvents.groupingBy { deriveAppName(it.packageName) }.eachCount()
                        .entries.sortedByDescending { it.value }
                        .joinToString { "${it.key} ${it.value}x" }
                    val reasonCounts = gateEvents.groupingBy { it.reason }.eachCount()
                        .entries.sortedByDescending { it.value }
                        .joinToString { "\"${it.key}\" (${it.value}x)" }
                    "Interupsi layar hari ini: $appCounts. Alasan utama: $reasonCounts. Total gerbang: ${gateEvents.size}x."
                }

                val prompt = """
                    Kamu adalah Seremind AI — seorang pendamping refleksi diri dan psiko-edukator kesadaran digital yang sangat empatik, profesional, menenangkan, dan penuh welas asih.

                    Tugasmu:
                    Baca dan resapi tulisan jurnal pengguna berikut beserta data aktivitas layarnya hari ini:
                    - Isi Jurnal Pengguna: "$journalContent"
                    - Ringkasan Aktivitas Layar Hari Ini: $gateSummary
                    - Seed Variasi: ${System.currentTimeMillis()}

                    Panduan Penyusunan Respons Reflektif (Sangat Penting):
                    1. **Empati & Pengakuan Perasaan**: Mulailah dengan merangkul dan memvalidasi emosi yang tersirat dari isi tulisan pengguna secara hangat, ramah, dan profesional. Tunjukkan bahwa kamu benar-benar membaca dan memahami apa yang sedang ia alami.
                    2. **Menghubungkan dengan Pola Layar**: Secara bijak dan lembut (gunakan kata-kata seperti "mungkin", "tampak seperti", "bisa jadi"), hubungkan isi curahan hatinya dengan data pembukaan aplikasi/alasan yang ia pilih hari ini. Tunjukkan hubungan antara suasana hatinya dan dorongan mencari distraksi layar.
                    3. **Pesan Menenangkan & Pertanyaan Dalam**: Berikan kalimat penguatan yang menenangkan jiwa pengguna bahwa apa yang dirasakannya adalah hal manusiawi. Akhiri dengan SATU pertanyaan refleksi terbuka yang membantu pengguna menyadari kebutuhan sejati hatinya saat ini.

                    Gaya Bahasa:
                    - Bahasa Indonesia yang santun, hangat, profesional, puitis-reflektif, dan menenangkan.
                    - Tanpa nada menghakimi, tanpa menggurui, dan tanpa istilah medis/mendiagnosis.
                    - Panjang respons: 2-3 paragraf singkat yang nyaman dibaca (maksimal 550 karakter). HANYA keluarkan teks respons tersebut.
                """.trimIndent()

                val text = executeGeminiRequest(prompt)
                JournalFeedbackResult(feedback = text)
            }
        } catch (error: Exception) {
            JournalFeedbackResult(error = error.message ?: "Gagal memproses jurnal dengan AI.")
        }
    }

    private suspend fun generateContextualJournalPromptInternal(
        gateEvents: List<GateEvent>,
        latestJournalEntries: List<JournalEntry>
    ): JournalPromptResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext JournalPromptResult(error = "API key Gemini Jurnal belum dikonfigurasi di local.properties.")
        }
        try {
            withTimeout(15_000) {
                val gateSummary = if (gateEvents.isEmpty()) {
                    "Hari ini belum ada catatan pembukaan aplikasi terinterupsi."
                } else {
                    val appCounts = gateEvents.groupingBy { it.packageName }.eachCount()
                    val topApps = appCounts.entries.sortedByDescending { it.value }.take(3)
                        .joinToString { entry ->
                            val name = deriveAppName(entry.key)
                            val reasons = gateEvents.filter { it.packageName == entry.key }
                                .groupingBy { it.reason }.eachCount()
                                .entries.joinToString { "\"${it.key}\" (${it.value}x)" }
                            "$name (${entry.value}x; alasan: $reasons)"
                        }
                    "Ringkasan pembukaan aplikasi hari ini: $topApps. Total interupsi gerbang: ${gateEvents.size}x."
                }

                val journalSummary = if (latestJournalEntries.isEmpty()) {
                    "Belum ada entri jurnal sebelumnya."
                } else {
                    val latest = latestJournalEntries.first()
                    "Jurnal terakhir (${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(latest.timestamp))}, emosi '${latest.emotionTag ?: "netral"}'): \"${latest.content.take(120)}\""
                }

                val prompt = """
                    Kamu adalah AI pendamping refleksi diri Seremind yang hangat, perseptif, dan tidak menghakimi.
                    Analisis data aktivitas layar dan suasana hati pengguna hari ini:
                    - Aktivitas penggunaan aplikasi hari ini: $gateSummary
                    - Jurnal & emosi terakhir: $journalSummary
                    - Seed variasi: ${System.currentTimeMillis()}

                    Tugasmu:
                    Buat 1 (satu) pertanyaan refleksi pembuka jurnal yang unik, dinamis, dan menggugah kesadaran.

                    Panduan Kreatif (PENTING):
                    - JANGAN gunakan satu rumus kalimat kaku! Variasikan gaya bahasa dan sudut pandangmu.
                    - Gunakan nama aplikasi yang ramah dan manusiawi (misal "TikTok", "Instagram", "YouTube"), BUKAN nama paket teknis seperti "trill" atau "android".
                    - Kamu bisa menyoroti dorongan psikologis di balik kebiasaan hari ini (seperti dorongan mencari distraksi, rasa lelah, atau keinginan terhubung).
                    - Jika ada pola aplikasi terbanyak atau alasan menonjol, hubungkan secara alami dengan suasana hatinya.
                    - Buat pertanyaan berbentuk 1-2 kalimat eksploratif yang mengundang pengguna untuk jujur pada dirinya sendiri.
                    - Tanpa mukadimah, tanpa salam, tanpa tanda kutip di luar. HANYA keluarkan teks pertanyaannya.
                """.trimIndent()

                val text = executeGeminiRequest(prompt)
                JournalPromptResult(prompt = text)
            }
        } catch (e: Exception) {
            JournalPromptResult(error = e.message ?: "Gagal memuat pertanyaan AI.")
        }
    }

    private fun deriveAppName(packageName: String): String {
        return when {
            packageName.contains("trill", true) || packageName.contains("musically", true) -> "TikTok"
            packageName.contains("instagram", true) -> "Instagram"
            packageName.contains("youtube", true) -> "YouTube"
            packageName.contains("whatsapp", true) -> "WhatsApp"
            packageName.contains("twitter", true) || packageName.contains("x.android", true) -> "X/Twitter"
            packageName.contains("facebook", true) -> "Facebook"
            else -> packageName.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
