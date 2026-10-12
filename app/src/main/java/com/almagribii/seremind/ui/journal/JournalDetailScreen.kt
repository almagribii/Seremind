package com.almagribii.seremind.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (entry != null) {
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
        } else {
            Text("Catatan jurnal tidak ditemukan.")
        }
        Button(onClick = onBack) {
            Text("Kembali")
        }
    }
}
