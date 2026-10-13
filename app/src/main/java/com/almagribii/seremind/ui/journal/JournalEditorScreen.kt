package com.almagribii.seremind.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEditorScreen(
    viewModel: JournalViewModel,
    initialPrompt: String?,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var content by remember { mutableStateOf("") }
    var selectedEmotion by remember { mutableStateOf<String?>(null) }
    var showAiConsent by remember { mutableStateOf(false) }
    val emotions = listOf("Tenang", "Senang", "Lelah", "Cemas", "Bosan", "Stres")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Catatan baru", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Ruang aman untuk menulis apa adanya", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("✦", modifier = Modifier.padding(10.dp), color = MaterialTheme.colorScheme.primary)
            }
        }

        if (initialPrompt != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Prompt refleksi", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(text = initialPrompt, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            placeholder = { Text("Tuliskan pikiran dan perasaanmu di sini...") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            minLines = 8,
            maxLines = 14,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Refleksi dengan AI", fontWeight = FontWeight.SemiBold)
                    Text(
                        "AI membaca tulisan ini dan gate hari ini untuk memberi observasi serta pertanyaan.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .75f)
                    )
                }
                Switch(
                    checked = uiState.aiJournalEnabled,
                    onCheckedChange = { if (it) showAiConsent = true else viewModel.setAiJournalEnabled(false) }
                )
            }
        }

        Text(text = "Bagaimana perasaanmu saat ini?", style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                emotions.forEach { emotion ->
                    FilterChip(
                        selected = selectedEmotion == emotion,
                        onClick = { selectedEmotion = if (selectedEmotion == emotion) null else emotion },
                        label = { Text(emotion) }
                    )
                }
            }
        }

        Button(
            onClick = {
                if (content.isNotBlank()) {
                    viewModel.addJournalEntry(
                        content = content,
                        emotionTag = selectedEmotion,
                        entryType = if (initialPrompt != null) "daily_prompt" else "free",
                        aiPrompt = initialPrompt,
                        onComplete = onSaved
                    )
                }
            },
            enabled = content.isNotBlank() && !uiState.isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (uiState.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Simpan Jurnal", fontWeight = FontWeight.Bold)
        }
    }

    if (showAiConsent) {
        AlertDialog(
            onDismissRequest = { showAiConsent = false },
            title = { Text("Izinkan refleksi AI?") },
            text = { Text("Isi jurnal dan ringkasan gate hari ini akan dikirim ke Gemini untuk membuat satu observasi dan satu pertanyaan. Kamu bisa mematikannya kapan saja di Pengaturan.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAiJournalEnabled(true)
                    showAiConsent = false
                }) { Text("Izinkan") }
            },
            dismissButton = { TextButton(onClick = { showAiConsent = false }) { Text("Batal") } }
        )
    }
}
