package com.almagribii.seremind.ui.apps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.almagribii.seremind.data.local.entity.AppTarget
import com.almagribii.seremind.ui.theme.LightMint
import com.almagribii.seremind.util.AppIcon
import com.almagribii.seremind.data.local.entity.GateEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

private val categoryOrder = listOf("Media Sosial", "Video", "Game", "Lainnya")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    var pickerSearchQuery by remember { mutableStateOf("") }

    val filteredTargets = uiState.targets.filter {
        it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
    }
    val groupedTargets = filteredTargets.groupBy { appCategory(it) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            AppsHeader()
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).offset(y = (-20).dp),
                placeholder = { Text("Cari aplikasi...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                shape = RoundedCornerShape(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .18f)
                ),
                singleLine = true
            )

            if (filteredTargets.isEmpty()) {
                EmptyTargets()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    categoryOrder.forEach { category ->
                        val targets = groupedTargets[category].orEmpty()
                        if (targets.isNotEmpty()) {
                            item {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                            }
                            items(targets, key = { it.id }) { target ->
                                AppTargetRow(
                                    target = target,
                                    onToggle = { viewModel.toggleTargetActive(target, it) }
                                    ,onClick = { viewModel.selectTarget(target) }
                                    ,onLongClick = { viewModel.requestDelete(target) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(70.dp))
        }

        FloatingActionButton(
            onClick = { showAppPicker = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 90.dp, end = 20.dp),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah aplikasi", modifier = Modifier.size(28.dp))
        }
    }

    if (showAppPicker) {
        LaunchedEffect(Unit) {
            if (uiState.installedApps.isEmpty()) {
                viewModel.loadInstalledApps()
            }
        }
        AppPickerSheet(
            installedApps = uiState.installedApps,
            targets = uiState.targets,
            isLoading = uiState.isInstalledAppsLoading,
            searchQuery = pickerSearchQuery,
            onSearchChange = { pickerSearchQuery = it },
            onDismiss = { showAppPicker = false },
            onAdd = { packageName, appName ->
                viewModel.addTarget(packageName, appName)
                showAppPicker = false
            }
        )
    }

    uiState.selectedTarget?.let { target ->
        AppInteractionDialog(
            target = target,
            events = uiState.selectedEvents,
            onDismiss = viewModel::closeTargetDetails,
            onSaveSettings = { delaySec, maxMin, cooldownMin ->
                viewModel.updateTargetSettings(target, delaySec, maxMin, cooldownMin)
            }
        )
    }
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Hapus ${uiState.selectedTarget?.appName}?") },
            text = { Text("Aplikasi ini akan dihapus dari daftar target Seremind.") },
            confirmButton = {
                TextButton(onClick = {
                    uiState.selectedTarget?.let(viewModel::deleteTarget)
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Batal") } }
        )
    }
}

@Composable
private fun AppsHeader() {
    Surface(color = MaterialTheme.colorScheme.primary, tonalElevation = 0.dp) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 28.dp, top = 24.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("Aplikasi", color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Kelola aplikasi yang dijeda", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f),
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTargetRow(
    target: AppTarget,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        shape = RoundedCornerShape(14.dp),
        color = LightMint.copy(alpha = .22f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = target.packageName,
                appName = target.appName,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(target.appName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Jeda: ${target.delaySeconds}s • Sesi: ${target.maxSessionMinutes.takeIf { it > 0 } ?: "Nonaktif"} • Istirahat: ${target.cooldownMinutes.takeIf { it > 0 } ?: "Nonaktif"}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
                    fontSize = 11.sp
                )
            }

            Switch(
                checked = target.isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = .45f)
                )
            )
        }
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppInteractionDialog(
    target: AppTarget,
    events: List<GateEvent>,
    onDismiss: () -> Unit,
    onSaveSettings: (delaySeconds: Int, maxSessionMinutes: Int, cooldownMinutes: Int) -> Unit
) {
    val format = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    var selectedDelay by remember(target) { mutableIntStateOf(target.delaySeconds) }
    var selectedMaxSession by remember(target) { mutableIntStateOf(target.maxSessionMinutes) }
    var selectedCooldown by remember(target) { mutableIntStateOf(target.cooldownMinutes) }
    var customMaxSession by remember(target) { mutableStateOf(if (target.maxSessionMinutes > 0) target.maxSessionMinutes.toString() else "") }
    var customCooldown by remember(target) { mutableStateOf(if (target.cooldownMinutes > 0) target.cooldownMinutes.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(target.packageName, target.appName, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                Text(target.appName, Modifier.weight(1f).padding(start = 12.dp), fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Tutup") }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Pengaturan Akses & Sesi", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                // 1. Delay seconds
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Jeda sebelum membuka: $selectedDelay detik", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3, 5, 10, 15, 30).forEach { sec ->
                            FilterChip(
                                selected = selectedDelay == sec,
                                onClick = { selectedDelay = sec },
                                label = { Text("${sec}s", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // 2. Max Session Minutes
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Batas waktu sesi aktif: ${selectedMaxSession.takeIf { it > 0 } ?: "Nonaktif"}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 5, 10, 15, 30, 60).forEach { min ->
                            FilterChip(
                                selected = selectedMaxSession == min,
                                onClick = {
                                    selectedMaxSession = min
                                    if (min == 0) customMaxSession = ""
                                },
                                label = { Text(if (min == 0) "Nonaktif" else "${min}m", fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customMaxSession,
                        onValueChange = {
                            customMaxSession = it.filter(Char::isDigit)
                            selectedMaxSession = customMaxSession.toIntOrNull()?.coerceAtLeast(1) ?: 0
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Atau isi menit sendiri") },
                        suffix = { Text("menit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                // 3. Cooldown Minutes
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Masa jeda istirahat wajib: ${selectedCooldown.takeIf { it > 0 } ?: "Nonaktif"}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 15, 30, 45, 60, 120).forEach { min ->
                            FilterChip(
                                selected = selectedCooldown == min,
                                onClick = {
                                    selectedCooldown = min
                                    if (min == 0) customCooldown = ""
                                },
                                label = { Text(if (min == 0) "Nonaktif" else "${min}m", fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customCooldown,
                        onValueChange = {
                            customCooldown = it.filter(Char::isDigit)
                            selectedCooldown = customCooldown.toIntOrNull()?.coerceAtLeast(1) ?: 0
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Atau isi menit sendiri") },
                        suffix = { Text("menit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                HorizontalDivider()

                Text("Riwayat Interaksi (${events.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (events.isEmpty()) {
                    Text("Belum ada interaksi tercatat.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                } else {
                    events.take(10).forEach { event ->
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(format.format(Date(event.timestamp)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(
                                text = "${if (event.action == "cancelled") "Dicegah" else "Dibuka"} (${event.reason})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (event.action == "cancelled") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveSettings(
                        selectedDelay,
                        customMaxSession.toIntOrNull()?.coerceAtLeast(1) ?: selectedMaxSession,
                        customCooldown.toIntOrNull()?.coerceAtLeast(1) ?: selectedCooldown
                    )
                    onDismiss()
                }
            ) {
                Text("Simpan Pengaturan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
private fun EmptyTargets() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Belum ada aplikasi target", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Tekan tombol + untuk memilih aplikasi yang ingin diberi jeda reflektif.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    installedApps: List<com.almagribii.seremind.util.InstalledAppInfo>,
    targets: List<AppTarget>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.85f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Pilih aplikasi target",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari aplikasi terinstal...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(50.dp),
                singleLine = true
            )

            val apps = installedApps.filter {
                it.appName.contains(searchQuery, true) || it.packageName.contains(searchQuery, true)
            }

            if (isLoading || (installedApps.isEmpty() && apps.isEmpty())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Memuat daftar aplikasi terinstal...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aplikasi tidak ditemukan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        val added = targets.any { it.packageName == app.packageName }
                        ListItem(
                            leadingContent = {
                                AppIcon(
                                    packageName = app.packageName,
                                    appName = app.appName,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                                    drawable = app.icon
                                )
                            },
                            headlineContent = { Text(app.appName, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(app.packageName, fontSize = 11.sp) },
                            trailingContent = {
                                if (added) Text(
                                    "Ditambahkan",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                else TextButton(onClick = { onAdd(app.packageName, app.appName) }) { Text("Tambah") }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun appCategory(target: AppTarget): String {
    val value = "${target.packageName} ${target.appName}".lowercase()
    return when {
        listOf("instagram", "tiktok", "twitter", "facebook", "whatsapp", "telegram").any(value::contains) ->
            "Media Sosial"
        listOf("youtube", "netflix", "vidio", "spotify", "twitch").any(value::contains) ->
            "Video"
        listOf("mobile legends", "pubg", "game", "genshin", "free fire", "minecraft").any(value::contains) ->
            "Game"
        else -> "Lainnya"
    }
}
