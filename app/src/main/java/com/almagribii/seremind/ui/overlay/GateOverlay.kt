package com.almagribii.seremind.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.GateEvent
import com.almagribii.seremind.data.remote.GeminiApiService
import com.almagribii.seremind.util.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GateOverlay(
    packageName: String,
    appName: String,
    delaySeconds: Int,
    onCancel: () -> Unit,
    onOpenAllowed: () -> Unit,
    onWriteReflection: (question: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { SeremindDatabase.getDatabase(context) }
    val geminiService = remember { GeminiApiService() }

    var isLoading by remember { mutableStateOf(true) }
    var question by remember { mutableStateOf("Apa yang sedang kamu cari?") }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var countdownSeconds by remember { mutableStateOf(delaySeconds) }
    var isOpenedEnabled by remember { mutableStateOf(false) }

    val reasons = listOf("Hiburan", "Bosan", "Cek Notifikasi", "Refleks Aja", "Cari Informasi")

    LaunchedEffect(packageName) {
        isLoading = true
        countdownSeconds = delaySeconds
        isOpenedEnabled = false
        selectedReason = null

        val fetchedQuestion = withContext(Dispatchers.IO) {
            try {
                geminiService.generateGateQuestion(appName, null)
            } catch (e: Exception) {
                "Apa yang sedang kamu cari?"
            }
        }
        question = fetchedQuestion
        isLoading = false

        var currentSec = delaySeconds
        while (currentSec > 0) {
            delay(1000)
            currentSec--
            countdownSeconds = currentSec
        }
        isOpenedEnabled = true
    }

    val recordAndExecute: (action: String, onSuccess: () -> Unit) -> Unit = { action, onSuccess ->
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val event = GateEvent(
                    packageName = packageName,
                    timestamp = System.currentTimeMillis(),
                    reason = selectedReason ?: "Lainnya",
                    action = action,
                    aiQuestion = question
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
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppIcon(
                    packageName = packageName,
                    appName = appName,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                )

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
                        text = question,
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

                Spacer(modifier = Modifier.height(8.dp))

                if (countdownSeconds > 0) {
                    Text(
                        text = "$countdownSeconds detik",
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
                        enabled = isOpenedEnabled && selectedReason != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = if (countdownSeconds > 0) "Buka (${countdownSeconds}s)" else "Buka",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TextButton(
                    onClick = {
                        recordAndExecute("cancelled") {
                            onWriteReflection(question)
                        }
                    }
                ) {
                    Text("Tulis Jurnal Refleksi", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
