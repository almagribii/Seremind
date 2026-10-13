package com.almagribii.seremind.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import com.almagribii.seremind.data.local.entity.GateEvent
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiApiService(private val apiKey: String = "") {

    private val client = OkHttpClient.Builder()
        .callTimeout(3, TimeUnit.SECONDS)
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
