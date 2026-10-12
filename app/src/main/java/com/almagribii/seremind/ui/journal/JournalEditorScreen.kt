package com.almagribii.seremind.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Tulisanku",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

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
