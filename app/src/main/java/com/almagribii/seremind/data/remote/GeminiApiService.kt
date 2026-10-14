package com.almagribii.seremind.data.remote

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
    private data class TextResponse(val text: String? = null, val error: String? = null)

    private val client = OkHttpClient.Builder()
        .callTimeout(3, TimeUnit.SECONDS)
        .build()
    private val journalClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun generateGateStatement(
        appName: String,
        reason: String?,
        recentEvents: List<GateEvent> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext getStaticGateStatement(appName)
        }
        try {
            withTimeout(3_000) {
                val history = recentEvents.take(10).joinToString("\n") { event ->
                    "- alasan: ${event.reason}, hasil: ${event.action}"
                }.ifBlank { "Belum ada riwayat gate untuk aplikasi ini." }
                val previousStatements = recentEvents.mapNotNull { it.aiQuestion }
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(" | ")
                    .ifBlank { "Tidak ada." }
                val prompt = """
                    Buat satu pernyataan kesadaran singkat dalam Bahasa Indonesia untuk seseorang
                    yang hendak membuka aplikasi "$appName". Gunakan konteks riwayat gate berikut:
                    $history
                    Pernyataan sebelumnya (jangan menirunya): $previousStatements
                    Alasan yang baru dipilih: ${reason ?: "belum dipilih"}.
                    Buat pernyataan yang berbeda, relevan dengan pola riwayat, empatik, netral,
                    dan tidak menghakimi. Jangan gunakan tanda tanya. Jangan mendiagnosis, jangan
                    memberi saran medis, dan jangan menyuruh pengguna melakukan tindakan tertentu.
                    Maksimal 120 karakter.
                    Variasi permintaan: ${System.currentTimeMillis()}.
                    Keluarkan hanya pernyataannya.
                """.trimIndent()
                requestText(prompt)
            }
        } catch (e: Exception) {
            null
        }
            ?: getStaticGateStatement(appName)
    }

    private fun requestText(prompt: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val candidates = response.body?.string()
                ?.let(::JSONObject)
                ?.optJSONArray("candidates")
            candidates?.takeIf { it.length() > 0 }
                ?.getJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.takeIf { it.length() > 0 }
                ?.getJSONObject(0)
                ?.optString("text")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }

        suspend fun generateJournalFeedback(
            journalContent: String,
            gateEvents: List<GateEvent>
        ): JournalFeedbackResult = withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext JournalFeedbackResult(error = "AI Jurnal belum tersedia karena API key belum dikonfigurasi.")
            try {
                withTimeout(15_000) {
                    val gateSummary = if (gateEvents.isEmpty()) {
                        "Belum ada aktivitas gate hari ini."
                    } else {
                        val appCounts = gateEvents.groupingBy { it.packageName }.eachCount()
                            .entries.joinToString { "${it.key} ${it.value}x" }
                        val reasonCounts = gateEvents.groupingBy { it.reason }.eachCount()
                            .entries.joinToString { "\"${it.key}\" ${it.value}x" }
                        val hours = gateEvents.groupingBy {
                            SimpleDateFormat("HH", Locale.getDefault()).format(Date(it.timestamp))
                        }.eachCount().entries.joinToString { "${it.key}.00 ${it.value}x" }
                        "Aplikasi: $appCounts. Alasan: $reasonCounts. Jam: $hours."
                    }
                    val prompt = """
                        Beri respons jurnal dalam Bahasa Indonesia dengan tepat dua bagian:
                        1) satu observasi yang mengutip inti tulisan pengguna dan menghubungkannya
                        dengan data gate hari ini secara hati-hati;
                        2) satu pertanyaan lanjutan yang terbuka.
                        Tulisan pengguna: "$journalContent"
                        Data gate hari ini: $gateSummary
                        Gunakan "mungkin" atau "bisa jadi", bukan kepastian. Jangan menghakimi,
                        mendiagnosis, memberi nasihat medis, memaksa, atau menyarankan tindakan.
                        Format: satu paragraf singkat berisi observasi lalu pertanyaan. Maksimal 320 karakter.
                    """.trimIndent()
                    val response = requestJournalText(prompt)
                    response.text?.let { JournalFeedbackResult(feedback = it) }
                        ?: JournalFeedbackResult(
                            error = response.error ?: "Gemini tidak memberikan respons. Jurnal tetap tersimpan."
                        )
                }
            } catch (error: Exception) {
                JournalFeedbackResult(error = "AI tidak dapat memproses jurnal: ${error.userMessage()}")
            }
        }

    private fun requestJournalText(prompt: String): TextResponse {
        if (apiKey.isBlank()) return TextResponse(error = "API key AI Jurnal belum tersedia di aplikasi.")
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
            journalClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiMessage = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty()
                    return@use TextResponse(
                        error = "Gemini menolak permintaan (${response.code}): ${
                            apiMessage.ifBlank { "respons server tidak valid" }
                        }"
                    )
                }
                val text = JSONObject(rawBody)
                    .optJSONArray("candidates")?.takeIf { it.length() > 0 }
                    ?.getJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    ?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("text")
                    ?.trim()?.takeIf { it.isNotBlank() }
                TextResponse(text = text, error = if (text == null) "Format respons Gemini tidak dikenali." else null)
            }
        } catch (error: Exception) {
            TextResponse(error = "Koneksi ke Gemini gagal: ${error.userMessage()}")
        }
    }

    private fun Exception.userMessage(): String = when (this) {
        is java.net.UnknownHostException -> "tidak ada koneksi internet."
        is java.net.SocketTimeoutException -> "waktu tunggu habis."
        else -> message?.takeIf { it.isNotBlank() } ?: "terjadi kesalahan yang tidak diketahui."
    }

    suspend fun generateRecoveryResponse(emotion: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext getStaticRecoveryResponse(emotion)
        }
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$apiKey"
            val prompt = "Seseorang merasa '$emotion' setelah sesi penggunaan media sosial yang panjang. Berikan tanggapan yang empatik, menenangkan, penuh welas asih, dan tidak menghakimi (dalam Bahasa Indonesia, maksimal 2 kalimat)."
            
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val responseString = response.body!!.string()
                    val jsonResponse = JSONObject(responseString)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text")
                            if (text.isNotBlank()) return@withContext text.trim()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        getStaticRecoveryResponse(emotion)
    }

    suspend fun generateJournalPrompt(): String = withContext(Dispatchers.IO) {
        val prompts = listOf(
            "Apa hal berharga yang kamu lewatkan hari ini karena sibuk menatap layar?",
            "Emosi apa yang paling sering muncul dalam dirimu hari ini, dan apa pesan di baliknya?",
            "Jika pikiranmu adalah sebuah ruangan yang tenang, apa yang sedang terjadi di dalamnya saat ini?",
            "Bagaimana rasanya memberi dirimu waktu 10 menit tanpa gawai hari ini?",
            "Hal kecil apa yang bisa kamu syukuri detik ini?"
        )
        prompts.random()
    }

    private fun getStaticGateStatement(appName: String): String {
        val statements = listOf(
            "Dorongan untuk membuka $appName sedang muncul; kamu bisa menyadari apa yang sedang kamu butuhkan.",
            "Sebelum membuka $appName, sadari apakah kamu sedang mencari sesuatu atau sekadar mengikuti kebiasaan.",
            "Perhatianmu kembali tertuju pada $appName; ini kesempatan untuk melihat niatmu dengan lebih jelas.",
            "Keinginan membuka $appName sedang hadir; kamu tetap punya ruang untuk memilih dengan sadar.",
            "Kamu sedang menuju $appName; sadari hal yang ingin kamu dapatkan dari pilihan ini."
        )
        return statements.random()
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
            return@withContext getStaticStatisticsAnalogy(
                period = period,
                peakHoursSummary = peakHoursSummary,
                reasonsSummary = reasonsSummary,
                appsSummary = appsSummary
            )
        }
        try {
            withTimeout(15_000) {
                val prompt = """
                    Analisis data agregat penggunaan aplikasi pengguna selama periode $period berikut:
                    - Total dibuka: $totalOpened, Total dicegah/dibatalkan: $totalCancelled
                    - Puncak jam pembukaan: $peakHoursSummary
                    - Alasan terbanyak: $reasonsSummary
                    - Aplikasi terbanyak: $appsSummary
                    - Urutan/pola transisi pembukaan antar aplikasi: $sequencesSummary

                    Tugasmu:
                    1) Temukan 1 korelasi/pola hubungan tersembunyi antar aplikasi atau antar waktu/alasan yang mungkin tidak disadari pengguna (misal: membuka aplikasi A di jam tertentu yang biasanya diikuti dengan membuka aplikasi B, atau korelasi antar aplikasi/alasan). Sajikan pola korelasi ini dalam 1-2 kalimat yang empatik, ramah, dan tajam (tanpa menghakimi).
                    2) Berikan 1 saran/feedback konkret yang realistis dan dapat dilakukan pengguna ke depan untuk meningkatkan kesadaran digital dan memperkecil gesekan ini.

                    Kembalikan HANYA JSON valid dengan format persis berikut (tanpa blok markdown atau teks lain):
                    {"analogy": "isi korelasi pola 1-2 kalimat", "feedback": "saran/feedback konkret ke depan"}
                """.trimIndent()

                val responseText = requestStatsText(prompt)
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
                getStaticStatisticsAnalogy(period, peakHoursSummary, reasonsSummary, appsSummary)
            }
        } catch (e: Exception) {
            getStaticStatisticsAnalogy(period, peakHoursSummary, reasonsSummary, appsSummary)
        }
    }

    private fun requestStatsText(prompt: String): String? {
        if (apiKey.isBlank()) return null
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
            }
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            journalClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@use null
                JSONObject(rawBody)
                    .optJSONArray("candidates")?.takeIf { it.length() > 0 }
                    ?.getJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    ?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("text")
                    ?.trim()?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getStaticStatisticsAnalogy(
        period: String,
        peakHoursSummary: String,
        reasonsSummary: String,
        appsSummary: String
    ): StatisticsAnalogyResult {
        val app = appsSummary.split(",").firstOrNull()?.takeBefore("(")?.trim()?.ifBlank { null } ?: "aplikasi target"
        val reason = reasonsSummary.split(",").firstOrNull()?.takeBefore("(")?.trim()?.ifBlank { null } ?: "alasan tertentu"
        val analogy = "Kamu cenderung paling sering mengakses $app di jam-jam rawan ($peakHoursSummary), dan biasanya terdorong oleh $reason. Kedua hal ini tampak saling memicu kebiasaan membuka layar secara otomatis."
        val feedback = "Coba atur durasi jeda yang lebih panjang pada $app dan alihkan perhatian dengan latihan napas singkat atau menulis jurnal saat merasa $reason."
        return StatisticsAnalogyResult(analogy = analogy, feedback = feedback)
    }

    private fun String.takeBefore(delimiter: String): String {
        val index = indexOf(delimiter)
        return if (index != -1) substring(0, index) else this
    }

    private fun getStaticRecoveryResponse(emotion: String): String {
        return when (emotion.lowercase()) {
            "lelah" -> "Wajar merasa lelah setelah menyerap begitu banyak informasi. Tubuh dan pikiranmu sedang meminta istirahat yang nyata."
            "cemas" -> "Kecemasan sering kali hadir ketika kita membandingkan diri dengan dunia luar. Tarik napas dalam, kamu sudah melakukan yang terbaik."
            "bosan" -> "Kebosanan adalah pintu gerbang kreativitas. Tidak apa-apa untuk duduk diam tanpa stimulasi sejenak."
            "kesepian" -> "Layar HP tidak bisa menggantikan kehangatan koneksi nyata. Peluk dirimu sendiri, kamu tidak sendirian."
            "stres" -> "Lepaskan beban pundakmu sejenak. Dunia tidak akan runtuh jika kamu beristirahat 5 menit saja."
            else -> "Terima kasih sudah jujur dengan perasaanmu. Setiap kesadaran adalah langkah awal menuju ketenangan."
        }
    }
}
