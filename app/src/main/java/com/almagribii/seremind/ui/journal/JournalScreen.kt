package com.almagribii.seremind.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almagribii.seremind.data.local.entity.Article
import com.almagribii.seremind.data.local.entity.JournalEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun JournalScreen(
    viewModel: JournalViewModel,
    onNavigateToEditor: (initialPrompt: String?) -> Unit,
    onNavigateToDetail: (entryId: Int) -> Unit,
    onNavigateToArticle: (articleId: Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        JournalHeader(selectedTab) { selectedTab = it }
        when (selectedTab) {
            0 -> JournalEntriesTab(
                entries = uiState.entries,
                onNewEntry = { onNavigateToEditor(null) },
                onEntryClick = { onNavigateToDetail(it.id) },
                onDeleteEntry = { viewModel.deleteEntry(it) },
                onAskPrompt = { onNavigateToEditor(uiState.currentPrompt ?: "Apa satu hal yang kamu syukuri hari ini?") },
                onRequestNewPrompt = { viewModel.fetchNewPrompt() },
                currentPrompt = uiState.currentPrompt,
                isPromptLoading = uiState.isPromptLoading
            )
            else -> ArticlesTab(uiState.articles, onArticleClick = { onNavigateToArticle(it.id) })
        }

    }

}

@Composable
private fun JournalHeader(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(
            MaterialTheme.colorScheme.primary,
            RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
        ).statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text("Jurnal", color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Ruang refleksimu", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f))
        Spacer(Modifier.height(14.dp))
        Surface(Modifier.fillMaxWidth().height(38.dp), RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxSize().padding(3.dp)) {
                listOf("Tulisanku", "Bacaan").forEachIndexed { index, label ->
                    val selected = index == selectedTab
                    Surface(
                        Modifier.weight(1f).fillMaxHeight(),
                        RoundedCornerShape(20.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    ) {
                        TextButton(onClick = { onTabSelected(index) }, modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(0.dp)) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JournalEntriesTab(
    entries: List<JournalEntry>,
    onNewEntry: () -> Unit,
    onEntryClick: (JournalEntry) -> Unit,
    onDeleteEntry: (JournalEntry) -> Unit,
    onAskPrompt: () -> Unit,
    onRequestNewPrompt: () -> Unit,
    currentPrompt: String?,
    isPromptLoading: Boolean
) {
    val today = remember { Calendar.getInstance() }
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isPromptLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Pertanyaan Refleksi AI",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Menganalisis aktivitas & membuat pertanyaan...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (currentPrompt == null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Pertanyaan Refleksi AI",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = "Ingin refleksi kontekstual berdasarkan aktivitas penggunaan aplikasimu hari ini?",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            )

                            Button(
                                onClick = onRequestNewPrompt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Minta Pertanyaan AI",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Pertanyaan Refleksi AI",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                TextButton(
                                    onClick = onRequestNewPrompt,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Minta Lain",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Minta Lain",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Text(
                                text = currentPrompt,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )

                            Button(
                                onClick = onAskPrompt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Jawab & Tulis Jurnal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    Text("Belum ada catatan. Tulis refleksi pertamamu hari ini.",
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                }
            } else {
                item { Text("Hari Ini", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground) }
                items(entries, key = { it.id }) { entry ->
                    JournalEntryCard(entry, dateFormat, onEntryClick, onDeleteEntry)
                }
            }
        }

        FloatingActionButton(
            onClick = onNewEntry,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 90.dp, end = 20.dp),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah catatan", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun JournalEntryCard(entry: JournalEntry, dateFormat: SimpleDateFormat, onClick: (JournalEntry) -> Unit, onDelete: (JournalEntry) -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Card(modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(onClick = { onClick(entry) }, onLongClick = { showDeleteConfirm = true }),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dateFormat.format(Date(entry.timestamp)), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                    entry.emotionTag?.let {
                        Spacer(Modifier.width(8.dp))
                        Surface(modifier = Modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
                            Text(it, Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Hapus catatan?") },
                            text = { Text("Catatan ini akan dihapus permanen.") },
                            confirmButton = {
                                TextButton(onClick = { onDelete(entry); showDeleteConfirm = false }) {
                                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") } }
                        )
                    }
                }
                Text(entry.content, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
            }
            Text("›", fontSize = 25.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
        }
    }
}
