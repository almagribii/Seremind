package com.almagribii.seremind.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import com.almagribii.seremind.data.local.entity.GateEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiApiService(private val apiKey: String = "") {
    data class JournalFeedbackResult(val feedback: String? = null, val error: String? = null)
    data class StatisticsAnalogyResult(val analogy: String? = null, val feedback: String? = null, val error: String? = null)
    data class JournalPromptResult(val prompt: String? = null, val error: String? = null)
    private data class TextResponse(val text: String? = null, val error: String? = null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun executeGeminiRequest(prompt: String): TextResponse {
        if (apiKey.isBlank()) {
            return TextResponse(error = "API key Gemini belum dikonfigurasi di local.properties.")
        }
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
            }
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiMessage = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty()
                    Log.e("GeminiApiService", "Gemini HTTP ${response.code}: $apiMessage")
                    return@use TextResponse(
                        error = "Gemini menolak permintaan (${response.code}): ${apiMessage.ifBlank { "respons server tidak valid" }}"
                    )
                }
                val text = JSONObject(rawBody)
                    .optJSONArray("candidates")?.takeIf { it.length() > 0 }
                    ?.getJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    ?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("text")
                    ?.trim()?.takeIf { it.isNotBlank() }

                if (text != null) {
                    TextResponse(text = text)
                } else {
                    TextResponse(error = "Format respons Gemini tidak dikenali.")
                }
            }
        } catch (error: Exception) {
            Log.e("GeminiApiService", "Gemini connection error", error)
            TextResponse(error = "Koneksi ke Gemini gagal: ${error.userMessage()}")
        }
    }

    suspend fun generateGateStatement(
        appName: String,
        reason: String?,
        recentEvents: List<GateEvent> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val cleanAppName = getReadableAppName(appName)
        if (apiKey.isBlank()) {
            return@withContext "API key Gemini belum dikonfigurasi di local.properties."
        }
        try {
            withTimeout(12_000) {
                val history = recentEvents.take(10).joinToString("\n") { event ->
                    "- aplikasi: ${getReadableAppName(event.packageName)}, alasan: ${event.reason}, hasil: ${event.action}"
                }.ifBlank { "Belum ada riwayat." }
                val previousStatements = recentEvents.mapNotNull { it.aiQuestion }
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(" | ")
                    .ifBlank { "Tidak ada." }
                val prompt = """
                    Buat 1 (satu) pernyataan kesadaran diri singkat dalam Bahasa Indonesia untuk seseorang
                    yang hendak membuka aplikasi "$cleanAppName".

                    Konteks aktivitas:
                    - Riwayat gerbang: $history
                    - Alasan dipilih: ${reason ?: "belum dipilih"}
                    - Pernyataan yang pernah dipakai sebelumnya (JANGAN ditiru): $previousStatements
                    - Seed variasi: ${System.currentTimeMillis()}

                    Panduan Kreatif:
                    - JANGAN gunakan satu templat kaku!
                    - Buat pernyataan yang hangat, empati, dan menyentuh kesadaran tanpa menghakimi.
                    - Mendorong pengguna untuk menyadari niatnya sebelum membuka aplikasi.
                    - Tanpa tanda tanya, tanpa salam, tanpa kata-kata medis/mendiagnosis.
                    - Maksimal 120 karakter. Keluarkan HANYA teks pernyataannya.
                """.trimIndent()

                val response = executeGeminiRequest(prompt)
                response.text ?: (response.error ?: "AI Gemini tidak merespons.")
            }
        } catch (e: Exception) {
            "Gagal memuat AI Gemini: ${e.userMessage()}"
        }
    }

    suspend fun generateJournalFeedback(
        journalContent: String,
        gateEvents: List<GateEvent>
    ): JournalFeedbackResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext JournalFeedbackResult(error = "API key AI Jurnal belum dikonfigurasi di local.properties.")
        try {
            withTimeout(15_000) {
                val gateSummary = if (gateEvents.isEmpty()) {
                    "Belum ada aktivitas gerbang hari ini."
                } else {
                    val appCounts = gateEvents.groupingBy { getReadableAppName(it.packageName) }.eachCount()
                        .entries.joinToString { "${it.key} ${it.value}x" }
                    val reasonCounts = gateEvents.groupingBy { it.reason }.eachCount()
                        .entries.joinToString { "\"${it.key}\" ${it.value}x" }
                    val hours = gateEvents.groupingBy {
                        SimpleDateFormat("HH", Locale.getDefault()).format(Date(it.timestamp))
                    }.eachCount().entries.joinToString { "${it.key}.00 ${it.value}x" }
                    "Aplikasi: $appCounts. Alasan: $reasonCounts. Jam: $hours."
                }
                val prompt = """
                    Beri respons reflektif terhadap jurnal pengguna dalam Bahasa Indonesia.
                    Isi jurnal pengguna: "$journalContent"
                    Aktivitas gerbang hari ini: $gateSummary
                    Seed variasi: ${System.currentTimeMillis()}

                    Tugasmu (2 bagian singkat):
                    1) Berikan 1 observasi empatik yang menghubungkan apa yang ditulis pengguna dengan pola layarnya hari ini secara bijak dan ramah.
                    2) Berikan 1 pertanyaan lanjutan yang terbuka untuk mendalami perasaannya.

                    Pedoman:
                    - JANGAN gunakan templat kaku atau kalimat hafalan!
                    - Gunakan kata-kata yang lembut ("mungkin", "tampak seperti", "bisa jadi").
                    - Jangan menghakimi, mendiagnosis, atau menyuruh secara memaksa.
                    - Maksimal 300 karakter. HANYA keluarkan respons pendek tersebut.
                """.trimIndent()

                val response = executeGeminiRequest(prompt)
                if (response.text != null) {
                    JournalFeedbackResult(feedback = response.text)
                } else {
                    JournalFeedbackResult(error = response.error ?: "Gemini tidak memberikan respons. Jurnal tetap tersimpan.")
                }
            }
        } catch (error: Exception) {
            JournalFeedbackResult(error = "AI tidak dapat memproses jurnal: ${error.userMessage()}")
        }
    }

    suspend fun generateContextualJournalPrompt(
        gateEvents: List<GateEvent>,
        latestJournalEntries: List<com.almagribii.seremind.data.local.entity.JournalEntry>
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
                            val name = getReadableAppName(entry.key)
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

                val response = executeGeminiRequest(prompt)
                if (response.text != null) {
                    JournalPromptResult(prompt = response.text)
                } else {
                    JournalPromptResult(error = response.error ?: "Gemini tidak memberikan respons.")
                }
            }
        } catch (e: Exception) {
            JournalPromptResult(error = "Gagal memuat pertanyaan AI: ${e.userMessage()}")
        }
    }

    suspend fun generateStatisticsAnalogy(
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

                val response = executeGeminiRequest(prompt)
                val responseText = response.text
                if (responseText != null) {
                    val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
                    val jsonObject = JSONObject(cleanJson)
                    val analogy = jsonObject.optString("analogy").trim()
                    val feedback = jsonObject.optString("feedback").trim()
                    if (analogy.isNotBlank() && feedback.isNotBlank()) {
                        return@withTimeout StatisticsAnalogyResult(
                            analogy = analogy,
                            feedback = feedback
                        )
                    }
                }
                StatisticsAnalogyResult(error = response.error ?: "Format respons AI Statistik tidak dikenali.")
            }
        } catch (e: Exception) {
            StatisticsAnalogyResult(error = "Gagal memuat analisis AI: ${e.userMessage()}")
        }
    }

    suspend fun generateJournalPrompt(): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "API key Gemini belum dikonfigurasi di local.properties."
        }
        val prompt = "Buat 1 pertanyaan refleksi singkat dan hangat untuk membuka hari dalam Bahasa Indonesia (maksimal 120 karakter)."
        val response = executeGeminiRequest(prompt)
        response.text ?: (response.error ?: "Gagal memuat pertanyaan AI.")
    }

    suspend fun generateRecoveryResponse(emotion: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "API key Gemini belum dikonfigurasi di local.properties."
        }
        val prompt = "Seseorang merasa '$emotion' setelah sesi penggunaan media sosial yang panjang. Berikan tanggapan yang empatik, menenangkan, penuh welas asih, dan tidak menghakimi (dalam Bahasa Indonesia, maksimal 2 kalimat)."
        val response = executeGeminiRequest(prompt)
        response.text ?: (response.error ?: "Gagal memuat tanggapan AI.")
    }

    private fun getReadableAppName(packageName: String): String {
        val knownApps = mapOf(
            "com.ss.android.ugc.trill" to "TikTok",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.ss.android.ugc.aweme" to "TikTok",
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube",
            "com.whatsapp" to "WhatsApp",
            "com.twitter.android" to "X (Twitter)",
            "com.x.android" to "X (Twitter)",
            "com.facebook.katana" to "Facebook",
            "com.facebook.orca" to "Messenger",
            "com.mobile.legends" to "Mobile Legends",
            "com.shopee.id" to "Shopee",
            "com.tokopedia.tkpd" to "Tokopedia"
        )

        knownApps[packageName.lowercase()]?.let { return it }

        val segments = packageName.split(".")
        val cleanSegment = segments.lastOrNull { seg ->
            seg.lowercase() !in listOf("android", "trill", "katana", "orca", "ugc", "ss", "app")
        } ?: segments.lastOrNull() ?: packageName

        return cleanSegment.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun Exception.userMessage(): String = when (this) {
        is java.net.UnknownHostException -> "tidak ada koneksi internet."
        is java.net.SocketTimeoutException -> "waktu tunggu habis (koneksi lambat)."
        else -> message?.takeIf { it.isNotBlank() } ?: "terjadi kesalahan yang tidak diketahui."
    }
}
