package com.almagribii.seremind.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.compose.ui.platform.LocalView

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    var dialog by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val owner = view.findViewTreeLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.checkPermissions()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsHeader()
        Column(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ProfileCard()
            SettingsGroup("Pengaturan Umum") {
                SettingRow(Icons.Default.Info, "Durasi jeda default", "${state.defaultDelay} detik") { dialog = "delay" }
                SettingRow(Icons.Default.Info, "Eskalasi friksi", if (state.escalationFriction) "Aktif" else "Nonaktif") {
                    viewModel.setToggle("escalation_friction", !state.escalationFriction)
                }
                SettingSwitchRow(Icons.Default.Lock, "Mode fokus", state.focusMode) {
                    viewModel.setToggle("focus_mode", it)
                }
            }
            SettingsGroup("Jadwal") {
                SettingRow(Icons.Default.Info, "Jadwal blocking", state.blockingSchedule) { dialog = "schedule" }
                SettingRow(Icons.Default.Info, "Budget waktu harian", state.dailyBudget) { dialog = "budget" }
            }
            SettingsGroup("Notifikasi") {
                SettingSwitchRow(Icons.Default.Notifications, "Pengingat refleksi", state.reflectionReminder) {
                    viewModel.setToggle("reflection_reminder", it)
                }
                SettingSwitchRow(Icons.Default.Notifications, "Ringkasan mingguan", state.weeklySummary) {
                    viewModel.setToggle("weekly_summary", it)
                }
            }
            SettingsGroup("Privasi") {
                SettingSwitchRow(Icons.Default.Lock, "Refleksi jurnal dengan AI", state.aiJournalEnabled) {
                    viewModel.setToggle("ai_journal_enabled", it)
                }
                SettingRow(Icons.Default.Lock, "Penyimpanan jurnal", "Jurnal tersimpan lokal; AI hanya aktif dengan izinmu") {}
                SettingRow(Icons.Default.Share, "Ekspor data", "Bagikan salinan data lokal") { viewModel.exportData(context) }
                SettingRow(Icons.Default.Delete, "Hapus semua data", "Tidak dapat dibatalkan", destructive = true) {
                    showDeleteConfirm = true
                }
            }
            SettingsGroup("Tentang") {
                SettingRow(Icons.Default.Info, "Versi", "1.0.0") {}
                SettingRow(Icons.Default.Lock, "Kebijakan privasi", "Data lokal dan privasi pengguna") {}
            }
            if (deleted) Text("Semua data lokal telah dihapus.", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
        }
    }

    if (dialog != null) {
        val title = when (dialog) { "delay" -> "Durasi jeda default"; "schedule" -> "Jadwal blocking"; else -> "Budget waktu harian" }
        val initial = when (dialog) {
            "delay" -> state.defaultDelay.toString()
            "schedule" -> state.blockingSchedule
            else -> state.dailyBudget
        }
        EditSettingDialog(title, initial, dialog == "delay", onDismiss = { dialog = null }) { value ->
            when (dialog) {
                "delay" -> value.toIntOrNull()?.coerceIn(3, 30)?.let(viewModel::setDefaultDelay)
                "schedule" -> viewModel.setSchedule(value)
                "budget" -> viewModel.setBudget(value)
            }
            dialog = null
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus semua data?") },
            text = { Text("Gate, jurnal, aplikasi target, dan preferensi akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllData { deleted = true }
                    showDeleteConfirm = false
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun SettingsHeader() {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Column(Modifier.fillMaxWidth().padding(start = 28.dp, top = 24.dp, bottom = 38.dp)) {
            Text("Pengaturan", color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Atur Seremind sesuai kebutuhanmu", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f))
        }
    }
}

@Composable
private fun ProfileCard() {
    Card(Modifier.fillMaxWidth().offset(y = (-20).dp), RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(5.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center) {
                Text("S", color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Teman Seremind", fontWeight = FontWeight.Bold)
                Text("Pengguna Aktif", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            }
            Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (destructive) Text(value, fontSize = 11.sp, color = MaterialTheme.colorScheme.error.copy(alpha = .7f))
            }
            Text(value.takeIf { !destructive } ?: "›", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
        }
    }
}

@Composable
private fun SettingSwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.weight(1f).padding(start = 10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EditSettingDialog(title: String, initial: String, numeric: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(if (numeric) "Detik (3-30)" else "Nilai") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}
