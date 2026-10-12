package com.almagribii.seremind.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almagribii.seremind.data.local.entity.Article
import com.almagribii.seremind.data.local.entity.JournalEntry
import com.almagribii.seremind.util.TimeFormatter

@Composable
fun JournalScreen(
    viewModel: JournalViewModel,
    onNavigateToEditor: (initialPrompt: String?) -> Unit,
    onNavigateToDetail: (entryId: Int) -> Unit,
    onNavigateToArticle: (articleId: Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(1) } // Default to Articles/Bacaan as requested or 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Tulisanku", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Artikel & Wawasan", fontWeight = FontWeight.Bold) }
            )
        }

        when (selectedTab) {
            0 -> JournalEntriesTab(
                entries = uiState.entries,
                onNewEntry = { onNavigateToEditor(null) },
                onEntryClick = { entry -> onNavigateToDetail(entry.id) },
                onAskPrompt = {
                    viewModel.fetchNewPrompt()
                    uiState.currentPrompt?.let { onNavigateToEditor(it) }
                },
                currentPrompt = uiState.currentPrompt
            )
            1 -> ArticlesTab(
                articles = uiState.articles,
                onArticleClick = { article -> onNavigateToArticle(article.id) }
            )
        }
    }
}

@Composable
fun JournalEntriesTab(
    entries: List<JournalEntry>,
    onNewEntry: () -> Unit,
    onEntryClick: (JournalEntry) -> Unit,
    onAskPrompt: () -> Unit,
    currentPrompt: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNewEntry,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Tulis Jurnal", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onAskPrompt,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50)
            ) {
                Text("Prompt AI")
            }
        }

        if (currentPrompt != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Prompt Refleksi:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = currentPrompt, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    text = "Belum ada catatan jurnal. Mulai tulis perasaanmu hari ini.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries.size) { index ->
                    val entry = entries[index]
                    Card(
                        onClick = { onEntryClick(entry) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = TimeFormatter.formatFull(entry.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                if (entry.emotionTag != null) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(entry.emotionTag) }
                                }
                            }
                            Text(
                                text = entry.content,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 3
                            )
                        }
                    }
                }
            }
        }
    }
}
