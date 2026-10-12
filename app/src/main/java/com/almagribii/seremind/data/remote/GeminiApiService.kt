package com.almagribii.seremind.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiApiService(private val apiKey: String = "AIzaSyBjVE3F70wMTNWfS1skyUJCLhnbO7c14zU") {

    private val client = OkHttpClient()

    suspend fun generateGateQuestion(appName: String, reason: String?): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext getStaticGateQuestion(appName)
        }
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$apiKey"
            val prompt = "Buat satu pertanyaan reflektif yang singkat, empatik, dan menenangkan (dalam Bahasa Indonesia) untuk seseorang yang hendak membuka aplikasi $appName. Tujuannya adalah membantu mereka menyadari niat mereka tanpa menghakimi."
            
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
        getStaticGateQuestion(appName)
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

    private fun getStaticGateQuestion(appName: String): String {
        val questions = listOf(
            "Apa yang sebenarnya sedang kamu cari di $appName saat ini?",
            "Apakah membuka $appName sekarang akan membuat hatimu lebih tenang?",
            "Seberapa besar kebutuhanmu untuk membuka $appName detik ini?",
            "Bagaimana jika kamu mengambil 3 napas dalam sebelum melanjutkan ke $appName?",
            "Apakah ada hal lain di dunia nyata yang lebih ingin kamu selesaikan sekarang?"
        )
        return questions.random()
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
