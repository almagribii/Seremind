package com.almagribii.seremind.ui.settings

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almagribii.seremind.R
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
    var showPrivacyInfo by remember { mutableStateOf(false) }
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
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileCard()
            SettingsGroup("Pengaturan Umum") {
                SettingRow(Icons.Default.Info, "Durasi jeda default", "${state.defaultDelay} detik") { dialog = "delay" }

            }


            SettingsGroup("Privasi") {
                SettingSwitchRow(Icons.Default.Lock, "Refleksi jurnal dengan AI", state.aiJournalEnabled) {
                    viewModel.setToggle("ai_journal_enabled", it)
                }
                SettingRow(Icons.Default.Lock, "Penyimpanan jurnal", "Jurnal tersimpan lokal.") {
                    showPrivacyInfo = true
                }
                SettingRow(Icons.Default.Share, "Ekspor data", "Bagikan salinan data lokal") { viewModel.exportData(context) }
                SettingRow(Icons.Default.Delete, "Hapus semua data", "Tidak dapat dibatalkan", destructive = true) {
                    showDeleteConfirm = true
                }
            }
            SettingsGroup("Tentang") {
                SettingRow(Icons.Default.Info, "Versi", "1.0.0") {}
                SettingRow(Icons.Default.Lock, "Kebijakan privasi", "Data lokal dan privasi pengguna") {
                    showPrivacyInfo = true
                }
            }
            if (deleted) Text("Semua data lokal telah dihapus.", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(70.dp))
        }
    }

    if (dialog != null) {
        val title = when (dialog) { "delay" -> "Durasi jeda default"; "schedule" -> "Jadwal blocking"; else -> "Budget waktu harian" }
        val initial = when (dialog) {
            "delay" -> state.defaultDelay.toString()
            "schedule" -> state.blockingSchedule
            else -> state.dailyBudget.filter(Char::isDigit)
        }
        EditSettingDialog(title, initial, dialog == "delay", onDismiss = { dialog = null }) { value ->
            when (dialog) {
                "delay" -> value.toIntOrNull()?.coerceIn(3, 30)?.let(viewModel::setDefaultDelay)
                "schedule" -> viewModel.setSchedule(value)
                "budget" -> value.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 1440)?.let {
                    viewModel.setBudget("$it menit")
                }
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
    if (showPrivacyInfo) {
        AlertDialog(
            onDismissRequest = { showPrivacyInfo = false },
            title = { Text("Privasi dan data") },
            text = {
                Text(
                    "Gate dan jurnal disimpan di perangkat. Isi jurnal hanya dikirim ke Gemini ketika Refleksi jurnal dengan AI diaktifkan. Kamu dapat mematikannya kapan saja."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyInfo = false }) { Text("Mengerti") }
            }
        )
    }
}

@Composable
private fun SettingsHeader() {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 40.dp, bottom = 30.dp)) {
            Text("Pengaturan", color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Atur Seremind sesuai kebutuhanmu", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f))
        }
    }
}

@Composable
private fun ProfileCard() {
    Card(
        modifier = Modifier.fillMaxWidth().offset(y = (-14).dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(5.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "Logo Seremind",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
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
        Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp),
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
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (destructive || value.length > 28) {
                    Text(
                        value,
                        fontSize = 11.sp,
                        color = if (destructive) MaterialTheme.colorScheme.error.copy(alpha = .7f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)
                    )
                }
            }
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
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
