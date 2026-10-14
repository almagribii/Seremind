package com.almagribii.seremind.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.GateEvent
import com.almagribii.seremind.data.remote.GeminiApiService
import com.almagribii.seremind.BuildConfig
import com.almagribii.seremind.util.AppIcon
import com.almagribii.seremind.R
import kotlinx.coroutines.Dispatchers
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
    val geminiService = remember { GeminiApiService(BuildConfig.GEMINI_API_KEY_OVERLAY) }

    var isLoading by remember { mutableStateOf(true) }
    var statement by remember { mutableStateOf("Kamu sedang menyadari niatmu sebelum membuka aplikasi ini.") }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var customReason by remember { mutableStateOf("") }
    var countdownSeconds by remember { mutableStateOf(delaySeconds) }
    var isOpenedEnabled by remember { mutableStateOf(false) }
    var cooldownSeconds by remember { mutableStateOf(cooldownRemainingSeconds) }

    val reasons = listOf("Hiburan", "Bosan", "Cek Notifikasi", "Refleks Aja", "Cari Informasi", "Lainnya")

    LaunchedEffect(packageName) {
        isLoading = true
        countdownSeconds = delaySeconds
        isOpenedEnabled = false
        selectedReason = null
        customReason = ""

        val fetchedStatement = withContext(Dispatchers.IO) {
            val recentEvents = db.gateEventDao().getRecentEventsForPackage(packageName)
            geminiService.generateGateStatement(appName, null, recentEvents)
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
            while (remaining > 0) {
                delay(1000)
                remaining--
                cooldownSeconds = remaining
            }
        }

    }

    val recordAndExecute: (action: String, onSuccess: () -> Unit) -> Unit = { action, onSuccess ->
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val event = GateEvent(
                    packageName = packageName,
                    timestamp = System.currentTimeMillis(),
                    reason = if (selectedReason == "Lainnya") {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box {
                if (!isWarningKick && (isCooldownActive || countdownSeconds > 0)) {
                    val remaining = if (isCooldownActive) cooldownSeconds else countdownSeconds
                    val total = if (isCooldownActive) {
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
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {

                }

                AppIcon(
                    packageName = packageName,
                    appName = appName,
                    modifier = Modifier
                        .size(60.dp)
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
                } else if (isCooldownActive) {
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
                    Button(
                        onClick = {
                            recordAndExecute("opened") {
                                onEmergencyOpenAllowed()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Buka Darurat 5 Menit", fontWeight = FontWeight.Bold)
                    }
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
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

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
                        reasons.forEach { reason ->
                            val isSelected = selectedReason == reason
                            OutlinedButton(
                                onClick = { selectedReason = reason },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(50),
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
                                    fontSize = 14.sp
                                )
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

                    Spacer(modifier = Modifier.height(8.dp))

                    if (countdownSeconds > 0) {
                        Text(
                            text = "Jeda refleksi: ${formatCountdown(countdownSeconds)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                recordAndExecute("cancelled") {
                                    onCancel()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
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
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = if (countdownSeconds > 0) "Buka (${countdownSeconds}s)" else "Buka (Maks ${maxSessionMinutes}m)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            recordAndExecute("cancelled") {
                                onWriteReflection(statement)
                            }
                        }
                    ) {
                        Text("Tulis Jurnal Refleksi", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            }
        }
    }
}
