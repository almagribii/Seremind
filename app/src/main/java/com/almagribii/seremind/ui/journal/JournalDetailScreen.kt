package com.almagribii.seremind.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.almagribii.seremind.util.TimeFormatter

@Composable
fun JournalDetailScreen(
    viewModel: JournalViewModel,
    entryId: Int,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val entry = uiState.entries.find { it.id == entryId }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        if (entry != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Detail jurnal", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                text = TimeFormatter.formatFull(entry.timestamp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            if (entry.emotionTag != null) {
                Badge { Text(entry.emotionTag!!) }
            }
            if (entry.aiPrompt != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Prompt: ${entry.aiPrompt}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyLarge
            )
            entry.aiFeedback?.let { feedback ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Refleksi AI", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(feedback, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            entry.aiFeedbackError?.let { error ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Refleksi AI belum tersedia", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(error)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { viewModel.requestFeedback(entry.id) }) {
                            Text("Coba lagi")
                        }
                    }
                }
            }
            if (entry.aiFeedback == null && uiState.processingFeedbackFor != entry.id) {
                OutlinedButton(onClick = { viewModel.requestFeedback(entry.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Minta feedback AI")
                }
            } else if (uiState.processingFeedbackFor == entry.id) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("AI sedang membaca jurnalmu…")
                }
            }
        } else {
            Text("Catatan jurnal tidak ditemukan.")
        }
        Button(onClick = onBack) {
            Text("Kembali")
        }
        }
    }
    if (showDeleteConfirm && entry != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus catatan?") },
            text = { Text("Catatan ini akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(entry) { onBack() }
                    showDeleteConfirm = false
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") } }
        )
    }
}
