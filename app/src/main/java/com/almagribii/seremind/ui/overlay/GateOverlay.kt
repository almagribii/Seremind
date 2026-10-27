package com.almagribii.seremind.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.GateEvent
import com.almagribii.seremind.BuildConfig
import com.almagribii.seremind.util.AppIcon
import com.almagribii.seremind.R
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GateOverlay(
    packageName: String,
    appName: String,
    delaySeconds: Int,
    maxSessionMinutes: Int = 15,
    cooldownMinutes: Int = 30,
    isCooldownActive: Boolean = false,
    cooldownMinutesLeft: Int = 0,
    cooldownRemainingSeconds: Int = cooldownMinutesLeft * 60,
    isWarningKick: Boolean = false,
    warningMessage: String? = null,
    onCancel: () -> Unit,
    onOpenAllowed: () -> Unit,
    onEmergencyOpenAllowed: () -> Unit = {},
    onWriteReflection: (statement: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { SeremindDatabase.getDatabase(context) }

    var isLoading by remember { mutableStateOf(true) }
    var statement by remember { mutableStateOf("Luruskan niat dan ingatlah waktu yang Allah amanahkan padamu.") }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var customReason by remember { mutableStateOf("") }
    var countdownSeconds by remember { mutableStateOf(delaySeconds) }
    var isOpenedEnabled by remember { mutableStateOf(false) }
    var cooldownSeconds by remember { mutableIntStateOf(cooldownRemainingSeconds) }
    var isCooldownFinished by remember { mutableStateOf(false) }

    val effectiveCooldownActive = isCooldownActive && !isCooldownFinished && cooldownSeconds > 0

    val reasons = listOf("Hiburan", "Bosan", "Cek Notifikasi", "Refleks Aja", "Cari Informasi", "Lainnya")

    LaunchedEffect(packageName) {
        isLoading = true
        countdownSeconds = delaySeconds
        isOpenedEnabled = false
        selectedReason = null
        customReason = ""

        val fetchedStatement = withContext(Dispatchers.IO) {
            val recentEvents = db.gateEventDao().getRecentEventsForPackage(packageName)
            generateGateStatementInternal(appName, null, recentEvents)
        }

        statement = fetchedStatement
        isLoading = false

        var currentSec = delaySeconds
        while (currentSec > 0) {
            delay(1000)
            currentSec--
            countdownSeconds = currentSec
        }
        isOpenedEnabled = true
    }

    LaunchedEffect(packageName, isCooldownActive, cooldownRemainingSeconds) {
        if (isCooldownActive) {
            var remaining = cooldownRemainingSeconds
            cooldownSeconds = remaining
            isCooldownFinished = false
            while (remaining > 0) {
                delay(1000)
                remaining--
                cooldownSeconds = remaining
            }
            isCooldownFinished = true
        } else {
            isCooldownFinished = false
        }
    }

    val recordAndExecute: (action: String, onSuccess: () -> Unit) -> Unit = { action, onSuccess ->
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val event = GateEvent(
                    packageName = packageName,
                    timestamp = System.currentTimeMillis(),
                    reason = if (action == "cancelled") {
                        "Batal Membuka"
                    } else if (selectedReason == "Lainnya") {
                        customReason.trim().ifBlank { "Lainnya" }
                    } else {
                        selectedReason ?: "Lainnya"
                    },
                    action = action,
                    aiQuestion = statement
                )
                db.gateEventDao().insert(event)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(selectedReason) {
        if (selectedReason == "Lainnya") {
            delay(100)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f))
            .pointerInput(Unit) {
                detectTapGestures { }
            }
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box {
                if (!isWarningKick && (effectiveCooldownActive || countdownSeconds > 0)) {
                    val remaining = if (effectiveCooldownActive) cooldownSeconds else countdownSeconds
                    val total = if (effectiveCooldownActive) {
                        cooldownRemainingSeconds.coerceAtLeast(1)
                    } else {
                        delaySeconds.coerceAtLeast(1)
                    }
                    CountdownBadge(
                        remainingSeconds = remaining,
                        totalSeconds = total,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 14.dp, end = 14.dp)
                    )
                }
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(220.dp)
                        .align(Alignment.Center)
                        .alpha(0.07f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIcon(
                        packageName = packageName,
                        appName = appName,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )

                    if (isWarningKick) {
                        Text(
                            text = "Batas Waktu Habis",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = warningMessage ?: "Batas waktu sesi telah tercapai! Aplikasi $appName otomatis ditutup untuk menjaga ketenanganmu.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onCancel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Mengerti", fontWeight = FontWeight.Bold)
                        }
                    } else if (effectiveCooldownActive) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Masa Jeda Istirahat Aktif (Sisa ${formatCountdown(cooldownSeconds)})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Text(
                            text = "Kamu baru saja menutup $appName. Jika harus membukanya sekarang, sesi dibatasi maksimal 5 menit darurat.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                recordAndExecute("cancelled") {
                                    onCancel()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Kembali (Istirahat)", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "Membuka $appName",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text(
                                text = statement,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 21.sp
                            )
                        }

                        Text(
                            text = "Apa yang sebenarnya kamu cari?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            reasons.chunked(2).forEach { rowReasons ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowReasons.forEach { reason ->
                                        val isSelected = selectedReason == reason
                                        OutlinedButton(
                                            onClick = { selectedReason = reason },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp),
                                            shape = RoundedCornerShape(50),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Text(
                                                text = reason,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    if (rowReasons.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        if (selectedReason == "Lainnya") {
                            OutlinedTextField(
                                value = customReason,
                                onValueChange = { customReason = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Alasan kamu") },
                                placeholder = { Text("Tulis alasanmu di sini") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )
                        }

                        if (countdownSeconds > 0) {
                            Text(
                                text = "Jeda refleksi: ${formatCountdown(countdownSeconds)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    recordAndExecute("cancelled") {
                                        onCancel()
                                    }
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Batalkan", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    recordAndExecute("opened") {
                                        onOpenAllowed()
                                    }
                                },
                                enabled = isOpenedEnabled &&
                                        selectedReason != null &&
                                        (selectedReason != "Lainnya" || customReason.isNotBlank()),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = if (countdownSeconds > 0) "Buka (${countdownSeconds}s)" else "Buka (${maxSessionMinutes}m)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun generateGateStatementInternal(
    appName: String,
    reason: String?,
    recentEvents: List<GateEvent> = emptyList(),
    apiKey: String = BuildConfig.GROQ_API_KEY.ifEmpty { BuildConfig.GEMINI_API_KEY_OVERLAY }.ifEmpty { BuildConfig.GEMINI_API_KEY }
): String = withContext(Dispatchers.IO) {
    val cleanAppName = deriveAppName(appName)
    val trimmedKey = apiKey.trim()
    if (trimmedKey.isBlank()) {
        return@withContext "API key Groq belum dikonfigurasi di local.properties."
    }
    try {
        withTimeout(8_000) {
            val history = recentEvents.take(10).joinToString("\n") { event ->
                "- aplikasi: ${deriveAppName(event.packageName)}, alasan: ${event.reason}, hasil: ${event.action}"
            }.ifBlank { "Belum ada riwayat." }
            val previousStatements = recentEvents.mapNotNull { it.aiQuestion }
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(" | ")
                .ifBlank { "Tidak ada." }

            val systemPrompt = "Kamu adalah AI pendamping muhasabah dan kesadaran Islami Seremind. Tulis HANYA 1 (satu) kalimat pernyataan refleksi Islami yang hangat, menyejukkan hati, dan menyadarkan pengguna tentang niat serta keberkahan waktu dalam Bahasa Indonesia di dalam tanda kutip. DILARANG menuliskan analisis, Bahasa Inggris, atau proses berpikirmu."
            val userPrompt = """
                Aplikasi yang hendak dibuka: "$cleanAppName"
                Riwayat interupsi sebelumnya:
                $history
                Alasan dipilih saat ini: ${reason ?: "belum dipilih"}
                Pernyataan sebelumnya (JANGAN ditiru): $previousStatements
                Seed variasi: ${System.currentTimeMillis()}

                Panduan Islami & Reflektif (Sangat Penting):
                - Buat 1 kalimat refleksi Islami yang singkat, sejuk, empati, dan menggugah muhasabah diri tanpa menghakimi.
                - Sentuh nilai kebaikan Islami seperti pentingnya lurusnya niat, menjaga keberkahan waktu, amanah usia, ketenangan hati, atau dzikrullah.
                - Tanpa tanda tanya, tanpa salam (Assalamu'alaikum), tanpa istilah medis/mendiagnosis.
                - Tuliskan jawaban akhirmu dalam tanda kutip ganda, contoh: "Luruskan niatmu, jadikan setiap waktumu bernilai ibadah dan kebaikan."
                - Maksimal 100 karakter. HANYA keluarkan teks pernyataannya di dalam tanda kutip.
            """.trimIndent()

            val url = "https://api.groq.com/openai/v1/chat/completions"
            val model = "openai/gpt-oss-20b"

            val client = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .callTimeout(6, TimeUnit.SECONDS)
                .build()

            val jsonBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
                put("temperature", 0.6)
                put("max_tokens", 250)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $trimmedKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiMessage = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty()
                    Log.e("GateOverlay", "Groq $model HTTP ${response.code}: $rawBody")

                    return@use when (response.code) {
                        401 -> "Groq API Key tidak valid (401). Mohon periksa GROQ_API_KEY di local.properties."
                        429 -> "Batas permintaan Groq API terlampaui (429). Mohon tunggu sejenak."
                        else -> "Groq Error (${response.code}): ${apiMessage.ifBlank { "respons server tidak valid" }}"
                    }
                }

                Log.d("GateOverlay", "Groq Raw Body: $rawBody")
                val jsonResponse = JSONObject(rawBody)
                val choices = jsonResponse.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val choice0 = choices.getJSONObject(0)
                    val extractedText = extractTextFromGroqChoice(choice0)
                    if (!extractedText.isNullOrBlank()) {
                        val cleanText = extractedText.trim().removeSurrounding("\"").removeSurrounding("'")
                        if (cleanText.isNotBlank()) {
                            return@use cleanText
                        }
                    }
                }
                Log.e("GateOverlay", "Groq parsing failed for raw body: $rawBody")
                "Format respons Groq tidak dapat dibaca."
            }
        }
    } catch (e: Exception) {
        "Gagal memuat AI Groq: ${e.message}"
    }
}

private fun extractTextFromGroqChoice(choiceObj: JSONObject): String? {
    val rawText = extractRawTextFromChoice(choiceObj) ?: return null

    // 1. Try extracting text inside double quotes "..." or “...”
    val quoteRegex = Regex("\"([^\"]{8,150})\"|“([^”]{8,150})”")
    val match = quoteRegex.find(rawText)
    if (match != null) {
        val extracted = (match.groupValues[1].ifBlank { match.groupValues[2] }).trim()
        if (extracted.isNotBlank() && !extracted.contains("Need to produce", ignoreCase = true) && !extracted.contains("Count:", ignoreCase = true)) {
            return extracted
        }
    }

    // 2. Filter out English reasoning lines
    val lines = rawText.lines()
        .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
        .filter { line ->
            line.isNotBlank() &&
            !line.startsWith("So something like", ignoreCase = true) &&
            !line.startsWith("Count characters", ignoreCase = true) &&
            !line.startsWith("We need to", ignoreCase = true) &&
            !line.startsWith("Make sure no", ignoreCase = true) &&
            !line.startsWith("Count:", ignoreCase = true)
        }

    val indonesianSentence = lines.firstOrNull { line ->
        line.length >= 8 && !line.contains("characters", ignoreCase = true) && !line.contains("statement", ignoreCase = true)
    }

    return indonesianSentence ?: rawText.trim()
}

private fun extractRawTextFromChoice(choiceObj: JSONObject): String? {
    val msg = choiceObj.optJSONObject("message")
    if (msg != null) {
        if (msg.has("content") && !msg.isNull("content")) {
            val contentVal = msg.get("content")
            val text = if (contentVal is JSONArray) {
                val sb = StringBuilder()
                for (i in 0 until contentVal.length()) {
                    val part = contentVal.optJSONObject(i)
                    val pText = part?.optString("text") ?: part?.optString("content") ?: contentVal.optString(i)
                    if (!pText.isNullOrBlank()) sb.append(pText)
                }
                sb.toString()
            } else {
                contentVal.toString()
            }
            if (text.isNotBlank() && text != "null") return text
        }

        val reasoning = msg.optString("reasoning_content", "").ifBlank { msg.optString("reasoning", "") }.trim()
        if (reasoning.isNotBlank() && reasoning != "null") return reasoning
    }

    val directText = choiceObj.optString("text", "").trim()
    if (directText.isNotBlank() && directText != "null") return directText

    return null
}

private fun deriveAppName(packageName: String): String {
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
        "com.facebook.orca" to "Messenger"
    )
    knownApps[packageName.lowercase()]?.let { return it }
    return packageName.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
