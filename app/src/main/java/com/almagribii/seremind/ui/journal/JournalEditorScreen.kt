package com.almagribii.seremind.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEditorScreen(
    viewModel: JournalViewModel,
    initialPrompt: String?,
    onSaved: () -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedEmotion by remember { mutableStateOf<String?>(null) }
    val emotions = listOf("Tenang", "Senang", "Lelah", "Cemas", "Bosan", "Stres")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Tulis Catatan Jurnal",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (initialPrompt != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Prompt: $initialPrompt", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            placeholder = { Text("Tuliskan pikiran dan perasaanmu di sini...") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Text(text = "Bagaimana perasaanmu saat ini?", style = MaterialTheme.typography.titleLarge, fontSize = 16.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            emotions.forEach { emotion ->
                FilterChip(
                    selected = selectedEmotion == emotion,
                    onClick = { selectedEmotion = if (selectedEmotion == emotion) null else emotion },
                    label = { Text(emotion) }
                )
            }
        }

        Button(
            onClick = {
                if (content.isNotBlank()) {
                    viewModel.addJournalEntry(
                        content = content,
                        emotionTag = selectedEmotion,
                        entryType = if (initialPrompt != null) "daily_prompt" else "free",
                        aiPrompt = initialPrompt
                    )
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simpan Jurnal")
        }
    }
}
