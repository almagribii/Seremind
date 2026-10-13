package com.almagribii.seremind.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almagribii.seremind.util.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalDetailScreen(
    viewModel: JournalViewModel,
    entryId: Int,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val entry = uiState.entries.find { it.id == entryId }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text("Detail jurnal", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    if (entry != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Hapus jurnal",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (entry == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Catatan jurnal tidak ditemukan.")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = TimeFormatter.formatFull(entry.timestamp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                entry.emotionTag?.let {
                    Badge { Text(it, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Text(
                        text = entry.content,
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                }

                entry.aiPrompt?.let { prompt ->
                    DetailSection(
                        title = "Prompt refleksi",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(prompt, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                entry.aiFeedback?.let { feedback ->
                    DetailSection(
                        title = "Refleksi AI",
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(feedback, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Berdasarkan tulisanmu dan data gate hari ini.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .7f)
                        )
                    }
                }

                entry.aiFeedbackError?.let { error ->
                    DetailSection(
                        title = "Refleksi AI belum tersedia",
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { viewModel.requestFeedback(entry.id) }) {
                            Text("Coba lagi")
                        }
                    }
                }

                if (entry.aiFeedback == null &&
                    entry.aiFeedbackError == null &&
                    uiState.processingFeedbackFor != entry.id
                ) {
                    OutlinedButton(
                        onClick = { viewModel.requestFeedback(entry.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Minta feedback AI")
                    }
                }

                if (uiState.processingFeedbackFor == entry.id) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("AI sedang membaca jurnalmu…")
                    }
                }

                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Kembali")
                }
                Spacer(Modifier.height(8.dp))
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
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    containerColor: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                content()
            }
        )
    }
}
